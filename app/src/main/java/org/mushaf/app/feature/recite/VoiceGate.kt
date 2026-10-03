package org.mushaf.app.feature.recite

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.R
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.ui.component.rememberHaptics

/** What keeps the phone from listening, each said to the reader with what to do. */
enum class VoiceProblem { DOWNLOAD_FAILED, NO_SPACE, MIC_DENIED, MIC_BLOCKED, NOTHING_HEARD }

/**
 * Everything the phone needs before it listens: the speech model (offered,
 * then fetched with its progress shown), room for it, the microphone's
 * permission. [request] goes through them in order and runs the action once
 * all are met; whatever is missing is shown by [VoiceNotice], never silently.
 */
class VoiceGate internal constructor(
    private val context: Context,
    private val recogniser: Recogniser,
    private val scope: CoroutineScope,
    private val onReject: () -> Unit
) {
    /** Where the model download stands, while it runs. */
    var progress by mutableStateOf<String?>(null)
        internal set
    var problem by mutableStateOf<VoiceProblem?>(null)
    internal var offering by mutableStateOf(false)
    internal var pending: (() -> Unit)? = null
    internal lateinit var askMic: () -> Unit

    /** Runs [action] once the model and the microphone are there; asks for what is missing. */
    fun request(action: () -> Unit) {
        problem = null
        pending = action
        when {
            !recogniser.installed() -> offering = true
            micAllowed() -> run()
            else -> askMic()
        }
    }

    /** The phone heard nothing: said, so the reader checks the microphone. */
    fun nothingHeard() {
        problem = VoiceProblem.NOTHING_HEARD
    }

    internal fun micAllowed() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    internal fun run() {
        val action = pending ?: return
        pending = null
        action()
    }

    internal fun fetch() {
        offering = false
        // The model and its unpacking need about 100 MB.
        if (context.filesDir.usableSpace < (120L shl 20)) {
            problem = VoiceProblem.NO_SPACE
            onReject()
            return
        }
        scope.launch {
            progress = context.getString(R.string.downloading_model)
            runCatching { recogniser.install { bytes -> progress = context.getString(R.string.downloading_model_progress, (bytes shr 20).toInt()) } }
                .onFailure {
                    progress = null
                    problem = VoiceProblem.DOWNLOAD_FAILED
                    onReject()
                }
                .onSuccess {
                    progress = null
                    if (micAllowed()) run() else askMic()
                }
        }
    }

    internal fun retry() {
        val p = problem
        problem = null
        when (p) {
            VoiceProblem.DOWNLOAD_FAILED -> fetch()
            VoiceProblem.MIC_DENIED -> askMic()
            VoiceProblem.MIC_BLOCKED -> context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            else -> Unit
        }
    }
}

@Composable
fun rememberVoiceGate(): VoiceGate {
    val context = LocalContext.current
    val recogniser: Recogniser = koinInject()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val gate = remember { VoiceGate(context, recogniser, scope) { haptics.reject() } }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) gate.run()
        else {
            // Refused twice, Android no longer asks: only the app's settings can allow it.
            val activity = context.findActivity()
            val canAsk = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
            gate.problem = if (canAsk) VoiceProblem.MIC_DENIED else VoiceProblem.MIC_BLOCKED
            haptics.reject()
        }
    }
    gate.askMic = { launcher.launch(Manifest.permission.RECORD_AUDIO) }
    if (gate.offering) ModelOffer(onYes = { gate.fetch() }, onNo = { gate.offering = false; gate.pending = null })
    return gate
}

/** The download's progress, or what is missing with the way out (try again, allow, settings). */
@Composable
fun VoiceNotice(gate: VoiceGate, modifier: Modifier = Modifier) {
    val progress = gate.progress
    val problem = gate.problem
    if (progress == null && problem == null) return
    Column(modifier.fillMaxWidth().padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            progress ?: when (problem!!) {
                VoiceProblem.DOWNLOAD_FAILED -> stringResource(R.string.voice_download_failed)
                VoiceProblem.NO_SPACE -> stringResource(R.string.voice_no_space)
                VoiceProblem.MIC_DENIED -> stringResource(R.string.voice_mic_denied)
                VoiceProblem.MIC_BLOCKED -> stringResource(R.string.voice_mic_blocked)
                VoiceProblem.NOTHING_HEARD -> stringResource(R.string.voice_nothing_heard)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = if (problem != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        val action = when (problem) {
            VoiceProblem.DOWNLOAD_FAILED -> stringResource(R.string.try_again)
            VoiceProblem.MIC_DENIED -> stringResource(R.string.allow)
            VoiceProblem.MIC_BLOCKED -> stringResource(R.string.open_settings)
            else -> null
        }
        if (progress == null && action != null) EvenRows(minSlot = 140.dp) { TextControl(action, { gate.retry() }, accent = true) }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
