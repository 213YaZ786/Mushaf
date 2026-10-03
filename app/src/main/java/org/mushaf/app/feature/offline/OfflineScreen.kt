package org.mushaf.app.feature.offline

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.koin.compose.koinInject
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.data.offline.Offline
import org.mushaf.app.data.offline.Pack
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Script
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.quran.Tafsir
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.QuietButton
import org.mushaf.app.ui.component.Section
import org.mushaf.app.ui.component.SwitchRow
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/**
 * What is kept on the phone for reading and listening offline: each pack
 * on its own, or everything in use at once. Translations are always kept
 * once chosen.
 */
@Composable
fun OfflineScreen(onBack: () -> Unit) {
    val store: SettingsStore = koinInject()
    val offline: Offline = koinInject()
    val settings by store.settings.collectAsState()
    val haptics = rememberHaptics()

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop("Offline", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Section("Everything in use") {
                Text(
                    "The mushaf's pages, ${Recitations.reciter(store.reciter(settings.riwayah)).name}'s recitation and the tafsir you read, kept on the phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                EvenRows(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    TextControl("Keep all offline", {
                        haptics.done()
                        // Warsh's pages are drawn from the font the app carries: nothing to fetch.
                        if (settings.script != Script.HAFS && settings.riwayah == Riwayah.HAFS) offline.start(Pack.Pages(settings.script))
                        offline.start(Pack.Recitation(store.reciter(settings.riwayah)))
                        offline.start(Pack.TafsirBook(settings.tafsir))
                    }, accent = true)
                }
                SwitchRow(
                    title = "Wait for Wi-Fi",
                    summary = "Large downloads start on Wi-Fi only.",
                    checked = settings.wifiOnly,
                    onChange = { on -> store.update { it.copy(wifiOnly = on) } }
                )
            }
            Section("Mushaf pages") {
                PackRow(Pack.Pages(Script.PRINT), "As printed", "604 pages, about 190 MB")
                if (Script.TAJWEED.usable) PackRow(Pack.Pages(Script.TAJWEED), "Tajweed in colour", "604 pages, about 170 MB")
            }
            Section("Recitations") {
                for (r in Recitations.of(settings.riwayah)) PackRow(Pack.Recitation(r.id), r.label, "114 surahs, about 0.5 to 1.5 GB")
            }
            Section("Tafsir") {
                for (b in Tafsir.BOOKS) PackRow(Pack.TafsirBook(b.id), b.name, "${b.language}, 114 surahs")
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** One pack: how much of it is here, and the way to keep it whole or let it go. */
@Composable
private fun PackRow(pack: Pack, title: String, size: String) {
    val offline: Offline = koinInject()
    val fonts: PageFonts = koinInject()
    val tafsir: Tafsir = koinInject()
    val recitations: Recitations = koinInject()
    val haptics = rememberHaptics()
    val progress by offline.progress(pack).collectAsState(null)
    var kept by remember { mutableIntStateOf(0) }
    // What is on the phone, read again while a download runs.
    LaunchedEffect(pack.id, progress) {
        while (true) {
            kept = when (pack) {
                is Pack.Pages -> fonts.count(pack.script)
                is Pack.TafsirBook -> tafsir.kept(Tafsir.BOOKS.first { it.id == pack.book })
                is Pack.Recitation -> recitations.kept(pack.reciter)
            }
            if (progress?.running != true) break
            delay(1500)
        }
    }
    val running = progress?.running == true
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        kept >= pack.total -> "Kept offline"
                        running -> "Downloading · $kept of ${pack.total}"
                        progress?.failed == true -> "Stopped · $kept of ${pack.total} kept · try again"
                        kept > 0 -> "$kept of ${pack.total} kept · $size"
                        else -> size
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                running -> QuietButton(onClick = { haptics.tick(); offline.cancel(pack) }) { Text("Pause") }
                kept >= pack.total -> QuietButton(onClick = {
                    haptics.tick()
                    when (pack) {
                        is Pack.Pages -> fonts.remove(pack.script)
                        is Pack.TafsirBook -> tafsir.remove(Tafsir.BOOKS.first { it.id == pack.book })
                        is Pack.Recitation -> recitations.remove(pack.reciter)
                    }
                    kept = 0
                }) { Text("Remove") }
                else -> BoldButton(onClick = { haptics.done(); offline.start(pack) }) { Text(if (kept > 0) "Finish" else "Keep") }
            }
        }
        if (running) LinearProgressIndicator(progress = { kept / pack.total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
