package org.mushaf.app.feature.hifz

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlin.random.Random
import org.koin.compose.koinInject
import org.mushaf.app.R
import org.mushaf.app.feature.recite.VoiceNotice
import org.mushaf.app.feature.recite.rememberVoiceGate
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.stt.Heard
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.feature.common.IconControl
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.feature.recite.Recite
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons
import org.mushaf.app.ui.theme.quranFont

/** A question: an ayah known by heart, and the one after it to recite. */
private class Question(val given: AyahKey, val givenWords: List<String>, val next: AyahKey, val nextWords: List<String>)

/**
 * Tested as in a competition: an ayah from what is known by heart, picked
 * at random; the reader recites the one after it, and the phone, hearing
 * it, shows each word said; what was passed over stays hidden.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TestScreen(onBack: () -> Unit) {
    val hifz: Hifz = koinInject()
    val quran: Quran = koinInject()
    val recite: Recite = koinInject()
    val recogniser: Recogniser = koinInject()
    val listen: Listen = koinInject()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val font = quranFont()
    val heard by recite.state.collectAsState()
    val modelReady by recogniser.ready.collectAsState()
    var round by remember { mutableIntStateOf(0) }
    var right by remember { mutableIntStateOf(0) }
    var asked by remember { mutableIntStateOf(0) }
    var shown by remember { mutableStateOf(false) }
    val gate = rememberVoiceGate()
    DisposableEffect(Unit) { onDispose { recite.reset(); listen.stop() } }

    // The known ayat whose next ayah, in the same surah, is known too.
    val question by produceState<Question?>(null, round) {
        val known = hifz.knownSet()
        val ayat = quran.ayat()
        val pairs = ayat.zipWithNext().filter { (a, b) -> a.key.surah == b.key.surah && a.key in known && b.key in known }
        if (pairs.isEmpty()) { value = null; return@produceState }
        val (a, b) = pairs[Random(System.nanoTime()).nextInt(pairs.size)]
        suspend fun words(k: AyahKey) = quran.page(quran.pageOf(k)).words.filter { it.key == k && !it.end }.map { it.text }
        value = Question(a.key, words(a.key), b.key, words(b.key))
    }
    val q = question
    LaunchedEffect(q) { shown = false; recite.reset() }

    val start = { if (q != null) recite.start(q.nextWords) }

    // A question is right when every word was said, none passed over.
    val complete = q != null && heard.marks.size >= q.nextWords.size && heard.marks.values.none { it == Heard.SKIPPED }
    LaunchedEffect(complete) {
        if (complete) { haptics.done(); right++; asked++; shown = true; recite.stop() }
    }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(stringResource(R.string.continue_ayah), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LocalReadableInset.current + 16.dp)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            if (q == null) {
                Text(
                    stringResource(R.string.test_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(24.dp)
                )
                return@Column
            }
            Text(
                if (asked == 0) stringResource(R.string.what_comes_after) else stringResource(R.string.test_score, right, asked),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
            )
            ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    q.givenWords.joinToString(" "),
                    style = TextStyle(fontFamily = font, fontSize = 28.sp, lineHeight = 52.sp, textAlign = TextAlign.Center),
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            // The next ayah, its words hidden until said.
            ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        q.nextWords.forEachIndexed { i, w ->
                            val mark = heard.marks[i]
                            val visible = shown || (mark != null && mark != Heard.SKIPPED)
                            Text(
                                if (visible) w else "⋯",
                                style = TextStyle(fontFamily = font, fontSize = 28.sp),
                                color = when {
                                    mark == Heard.SKIPPED -> MaterialTheme.colorScheme.error
                                    mark != null -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
            val status = when {
                heard.failed -> stringResource(R.string.mic_failed)
                heard.listening -> stringResource(R.string.listening_next)
                else -> null
            }
            if (status != null) Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 10.dp))
            VoiceNotice(gate)
            Spacer(Modifier.height(16.dp))
            EvenRows(minSlot = 64.dp) {
                IconControl(AppIcons.Mic, if (heard.listening) stringResource(R.string.stop_listening) else stringResource(R.string.recite), {
                    if (heard.listening) recite.stop() else gate.request { start() }
                }, accent = true, tint = if (heard.listening) MaterialTheme.colorScheme.error else androidx.compose.ui.graphics.Color.Unspecified)
                IconControl(AppIcons.VolumeUp, stringResource(R.string.hear_given), { listen.playOnce(q.given) })
                TextControl(stringResource(R.string.show), { recite.stop(); if (!shown && !complete) asked++; shown = true })
                TextControl(stringResource(R.string.next), { round++ })
            }
        }
    }
}
