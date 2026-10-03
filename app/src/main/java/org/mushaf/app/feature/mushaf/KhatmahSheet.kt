package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.data.khatmah.Khatmah
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.QuietButton
import org.mushaf.app.ui.component.ZoneAlertDialog
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** The lengths offered for a khatmah, in days. */
private val LENGTHS = listOf(7, 10, 15, 30, 60)

/**
 * The khatmah, on glass over the page: started in a tap (the whole Quran in
 * so many days, from page 1 or from the page shown), then the day's pages,
 * how far the reading is, and a way to today's first page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KhatmahSheet(page: Int, onGo: (Int) -> Unit, onClose: () -> Unit) {
    val khatmah: Khatmah = koinInject()
    val quran: Quran = koinInject()
    val haptics = rememberHaptics()
    val plan by khatmah.plan.collectAsState()
    var fromHere by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
        FloatingPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Khatmah", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FloatingAction(AppIcons.Close, "Close", onClose)
                }
                Spacer(Modifier.size(8.dp))
                val p = plan
                when {
                    p == null -> {
                        Text("Read the whole Quran in", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(12.dp))
                        val from = if (fromHere) page else 1
                        // The lengths share the whole width.
                        EvenRows(minSlot = 60.dp) {
                            for (d in LENGTHS) {
                                val perDay = (PAGES - from + 1 + d - 1) / d
                                FloatingPane(shape = RoundedCornerShape(20.dp), onClick = { haptics.done(); khatmah.start(d, from) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("$d days", style = MaterialTheme.typography.labelLarge)
                                        Text("$perDay p./day", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (page > 1) {
                            Spacer(Modifier.size(12.dp))
                            EvenRows(minSlot = 120.dp) {
                                TextControl("From page 1", { haptics.tick(); fromHere = false }, accent = !fromHere)
                                TextControl("From page $page", { haptics.tick(); fromHere = true }, accent = fromHere)
                            }
                        }
                    }
                    p.finished -> {
                        val took = ((p.ended ?: khatmah.today()) - p.start + 1).toInt()
                        Text(
                            if (took == 1) "The whole Quran read in one day." else "The whole Quran read in $took days.",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.size(12.dp))
                        TextControl("Start another", { haptics.tick(); khatmah.end() }, accent = true)
                    }
                    else -> {
                        val today = remember(p) { p.portion(khatmah.today()) }
                        val surah by produceState("", today.fromPage) {
                            value = runCatching { quran.surah(quran.firstAyah(today.fromPage).surah).name }.getOrDefault("")
                        }
                        LinearProgressIndicator(
                            progress = { p.read / p.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )
                        Text(
                            "Day ${minOf(today.day, p.days)} of ${p.days} · ${p.read} of ${p.total} pages read",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            when {
                                today.done -> "Today's pages are read. Next: page ${today.fromPage}, $surah."
                                today.fromPage == today.toPage -> "Today: page ${today.fromPage}, $surah."
                                else -> "Today: pages ${today.fromPage}–${today.toPage}, from $surah."
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            "Pages count as read when turned one after the other.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        EvenRows(minSlot = 120.dp) {
                            if (today.fromPage != page) TextControl("Go to page ${today.fromPage}", { haptics.tick(); onGo(today.fromPage) }, accent = true)
                            TextControl("End the khatmah", { ending = true })
                        }
                    }
                }
            }
        }
    }
    if (ending) ZoneAlertDialog(
        onDismissRequest = { ending = false },
        title = { Text("End the khatmah?") },
        text = { Text("Its progress is forgotten.") },
        confirmButton = { TextButton(onClick = { khatmah.end(); ending = false }) { Text("End") } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text("Keep it") } }
    )
}
