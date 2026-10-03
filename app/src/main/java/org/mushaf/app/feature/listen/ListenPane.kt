package org.mushaf.app.feature.listen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.IconControl
import org.mushaf.app.feature.common.EvenRows
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.ChoiceDialog
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

private val REPEATS = listOf(1, 2, 3, 5, 10, 0)
private val SPEEDS = listOf(0.75f, 1f, 1.25f, 1.5f)

fun repeatLabel(n: Int) = when (n) { 0 -> "Again and again"; 1 -> "Once"; else -> "$n times" }

/**
 * The recitation's controls on one pane of glass at the foot of the page:
 * who recites and where, play, the ayah before and after, how many times
 * each ayah is heard, the speed. Its parts wrap on a narrow screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ListenPane(modifier: Modifier = Modifier) {
    val listen: Listen = koinInject()
    val quran: Quran = koinInject()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val state by listen.state.collectAsState()
    val haptics = rememberHaptics()
    var choosing by remember { mutableStateOf(false) }
    val surah by produceState<String?>(null, state.key?.surah) { value = state.key?.let { quran.surah(it.surah).name } }

    // Sleep: in 15, 30 or 60 minutes, at the end of the surah, or not.
    val sleep by listen.sleep.collectAsState()
    val left by produceState(0, sleep) {
        while (true) {
            value = (sleep as? Sleep.At)?.let { ((it.at - System.currentTimeMillis() + 59_999) / 60_000).toInt() } ?: 0
            delay(15_000)
        }
    }

    FloatingPane(shape = RoundedCornerShape(28.dp), modifier = modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The reciter: a tap chooses another.
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { haptics.tick(); choosing = true }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(AppIcons.Headphones, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(
                            Recitations.reciter(store.reciter(settings.riwayah)).label,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        when {
                            state.failed -> "Could not be played. Check the connection."
                            state.key != null -> listOfNotNull(
                                "${surah ?: ""} ${state.key}",
                                repeatLabel(settings.repeat).lowercase().takeIf { settings.repeat != 1 },
                                when (sleep) {
                                    null -> null
                                    Sleep.SurahEnd -> "stops at the surah's end"
                                    is Sleep.At -> "stops in $left min"
                                }
                            ).joinToString(" · ")
                            else -> "Starting…"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                FloatingAction(AppIcons.Close, "Stop", { haptics.tick(); listen.stop() })
            }
            Spacer(Modifier.size(10.dp))
            // The controls share the whole width.
            EvenRows(minSlot = 46.dp) {
                IconControl(AppIcons.SkipPrevious, "Ayah before", { listen.skip(-1) })
                IconControl(
                    if (state.playing) AppIcons.Pause else AppIcons.Play,
                    if (state.playing) "Pause" else "Play",
                    { haptics.toggle(!state.playing); listen.toggle() },
                    accent = true
                ) { if (state.loading) LoadingMark(size = 20.dp) }
                IconControl(AppIcons.SkipNext, "Next ayah", { listen.skip(1) })
                TextControl(if (settings.repeat == 1) "Once" else if (settings.repeat == 0) "∞" else "${settings.repeat}×", {
                    haptics.tick()
                    listen.setRepeat(REPEATS[(REPEATS.indexOf(settings.repeat) + 1) % REPEATS.size])
                })
                TextControl("${settings.speed}×".replace(".0×", "×"), {
                    haptics.tick()
                    listen.setSpeed(SPEEDS[(SPEEDS.indexOf(settings.speed).coerceAtLeast(0) + 1) % SPEEDS.size])
                })
                TextControl(
                    when (sleep) {
                        null -> "Sleep"
                        Sleep.SurahEnd -> "Surah"
                        is Sleep.At -> "$left min"
                    },
                    {
                        haptics.tick()
                        listen.setSleep(
                            when (val s = sleep) {
                                null -> Sleep.At(System.currentTimeMillis() + 15 * 60_000L, 15)
                                is Sleep.At -> when (s.minutes) {
                                    15 -> Sleep.At(System.currentTimeMillis() + 30 * 60_000L, 30)
                                    30 -> Sleep.At(System.currentTimeMillis() + 60 * 60_000L, 60)
                                    else -> Sleep.SurahEnd
                                }
                                Sleep.SurahEnd -> null
                            }
                        )
                    },
                    accent = sleep != null
                )
            }
        }
    }
    if (choosing) {
        ChoiceDialog(
            title = "Reciter",
            options = Recitations.of(settings.riwayah).map { it.id to it.label },
            selected = store.reciter(settings.riwayah),
            onSelect = { listen.setReciter(it) },
            onDismiss = { choosing = false }
        )
    }
}
