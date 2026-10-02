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

    FloatingPane(shape = RoundedCornerShape(28.dp), modifier = modifier.widthIn(max = 560.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                Recitations.reciter(settings.reciter).label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                when {
                    state.failed -> "Could not be played. Check the connection."
                    state.key != null -> "${surah ?: ""} ${state.key}" + if (settings.repeat != 1) " · ${repeatLabel(settings.repeat).lowercase()}" else ""
                    else -> "Starting…"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 10.dp)
            ) {
                FloatingAction(AppIcons.SkipPrevious, "Ayah before", { listen.skip(-1) })
                Box(contentAlignment = Alignment.Center) {
                    FloatingAction(if (state.playing) AppIcons.Pause else AppIcons.Play, if (state.playing) "Pause" else "Play", {
                        haptics.toggle(!state.playing); listen.toggle()
                    })
                    if (state.loading) LoadingMark(size = 20.dp)
                }
                FloatingAction(AppIcons.SkipNext, "Next ayah", { listen.skip(1) })
                Chip(repeatLabel(settings.repeat)) {
                    haptics.tick()
                    listen.setRepeat(REPEATS[(REPEATS.indexOf(settings.repeat) + 1) % REPEATS.size])
                }
                Chip("${settings.speed}×".replace(".0×", "×")) {
                    haptics.tick()
                    listen.setSpeed(SPEEDS[(SPEEDS.indexOf(settings.speed).coerceAtLeast(0) + 1) % SPEEDS.size])
                }
                FloatingAction(AppIcons.Headphones, "Reciter", { choosing = true })
                FloatingAction(AppIcons.Close, "Stop", { haptics.tick(); listen.stop() })
            }
        }
    }
    if (choosing) {
        ChoiceDialog(
            title = "Reciter",
            options = Recitations.RECITERS.map { it.id to it.label },
            selected = settings.reciter,
            onSelect = { listen.setReciter(it) },
            onDismiss = { choosing = false }
        )
    }
}

@Composable
private fun Chip(text: String, onClick: () -> Unit) {
    FloatingPane(shape = CircleShape, onClick = onClick) {
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp))
    }
}
