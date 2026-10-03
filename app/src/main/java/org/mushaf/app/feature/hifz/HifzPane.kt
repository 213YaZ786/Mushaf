package org.mushaf.app.feature.hifz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.foundation.layout.Row
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.IconControl
import org.mushaf.app.feature.common.EvenRows
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import org.mushaf.app.core.stt.Heard
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.feature.recite.ModelOffer
import org.mushaf.app.feature.recite.Recite
import org.mushaf.app.ui.component.ZoneAlertDialog
import org.mushaf.app.core.hifz.Grade
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.feature.mushaf.WordShow
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/**
 * The session's controls on one pane of glass: how the words are hidden,
 * the next word shown, the lesson heard, and the end: the lesson known,
 * or the page graded. The similar ayat of the page are named, so the
 * reader is warned where memory tends to cross.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HifzPane(session: Session, words: List<Word>, modifier: Modifier = Modifier) {
    val hifz: HifzSession = koinInject()
    val listen: Listen = koinInject()
    val quran: Quran = koinInject()
    val recite: Recite = koinInject()
    val recogniser: Recogniser = koinInject()
    val reciting by recite.state.collectAsState()
    val modelReady by recogniser.ready.collectAsState()
    val context = LocalContext.current
    var offerModel by remember { mutableStateOf(false) }
    var fetching by remember { mutableStateOf<String?>(null) }
    // The words recited, in reading order: the ones the recogniser follows.
    val expected = remember(session.keys, words) { words.filter { !it.end && it.key in session.keys } }
    val startReciting = {
        if (session.show == WordShow.ALL) hifz.setShow(WordShow.HIDDEN)
        recite.start(expected.map { it.text })
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startReciting() }
    // What was heard shows the words; a word passed over counts as slipped.
    LaunchedEffect(reciting.marks) {
        for ((i, heard) in reciting.marks) {
            val w = expected.getOrNull(i) ?: continue
            if (heard == Heard.SKIPPED) hifz.slip(w) else hifz.reveal(w)
        }
    }
    DisposableEffect(Unit) { onDispose { recite.reset() } }
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val keys = session.keys.filter { k -> words.any { it.key == k } }.sorted()
    val similar by produceState(emptyList<String>(), session.page, keys) {
        value = keys.flatMap { k -> quran.similar(k).map { "$k ≈ $it" } }.take(4)
    }

    if (offerModel) {
        ModelOffer(
            onYes = {
                offerModel = false
                scope.launch {
                    fetching = "Downloading the speech model…"
                    runCatching { recogniser.install { bytes -> fetching = "Downloading the speech model · ${bytes shr 20} of 80 MB" } }
                        .onFailure { fetching = null; haptics.reject() }
                        .onSuccess {
                            fetching = null
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startReciting()
                            else askMic.launch(Manifest.permission.RECORD_AUDIO)
                        }
                }
            },
            onNo = { offerModel = false }
        )
    }
    FloatingPane(shape = RoundedCornerShape(28.dp), modifier = modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when (session.kind) {
                        SessionKind.LESSON -> "Lesson · ${keys.firstOrNull() ?: ""}–${keys.lastOrNull()?.ayah ?: ""}"
                        SessionKind.REVISION -> "Revision · page ${session.page}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                FloatingAction(AppIcons.Close, "End", { hifz.stop() })
            }
            Text(
                when (session.show) {
                    WordShow.ALL -> "Read it and listen, then hide the words."
                    else -> "Tap the page to recite, a word to see it, hold a word that slipped."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (reciting.listening || fetching != null || reciting.failed) {
                Text(
                    when {
                        fetching != null -> fetching!!
                        reciting.failed -> "The microphone could not be opened."
                        else -> "Listening: recite, the words show as you say them."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (similar.isNotEmpty()) {
                Text(
                    "Similar: " + similar.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // The controls share the whole width.
            EvenRows(Modifier.padding(top = 10.dp), minSlot = 52.dp) {
                for ((show, label) in listOf(WordShow.ALL to "Shown", WordShow.FIRST_LETTER to "Hints", WordShow.HIDDEN to "Hidden")) {
                    TextControl(label, { haptics.tick(); hifz.setShow(show) }, accent = session.show == show)
                }
                if (session.show != WordShow.ALL) {
                    IconControl(AppIcons.Visibility, "Show the next word", { hifz.revealNext(words) })
                }
                IconControl(AppIcons.Mic, if (reciting.listening) "Stop listening" else "Recite", {
                    when {
                        reciting.listening -> recite.stop()
                        !modelReady -> offerModel = true
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startReciting()
                        else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }, tint = if (reciting.listening) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
                if (session.kind == SessionKind.LESSON && keys.isNotEmpty()) {
                    IconControl(AppIcons.Repeat, "Listen three times", {
                        listen.setRepeat(3)
                        listen.play(keys.first(), until = keys.last())
                    })
                }
            }
            EvenRows(Modifier.padding(top = 8.dp), minSlot = 64.dp) {
                when (session.kind) {
                    SessionKind.LESSON -> {
                        TextControl("I know it by heart", { haptics.done(); scope.launch { hifz.learnt() } }, accent = true)
                    }
                    SessionKind.REVISION -> {
                        TextControl("Forgot", { haptics.reject(); hifz.grade(Grade.AGAIN) })
                        TextControl("Hard", { haptics.tick(); hifz.grade(Grade.HARD) })
                        TextControl("Good", { haptics.done(); hifz.grade(Grade.GOOD) }, accent = true)
                        TextControl("Easy", { haptics.done(); hifz.grade(Grade.EASY) })
                    }
                }
            }
        }
    }
}
