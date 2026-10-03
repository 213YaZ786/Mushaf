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
import org.mushaf.app.feature.listen.label
import org.mushaf.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
        top = { FloatingTop(stringResource(R.string.offline), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Section(stringResource(R.string.everything_in_use)) {
                Text(
                    stringResource(R.string.everything_in_use_detail, Recitations.reciter(store.reciter(settings.riwayah)).name),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
                EvenRows(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                    TextControl(stringResource(R.string.keep_all_offline), {
                        haptics.done()
                        // Warsh's pages are drawn from the font the app carries: nothing to fetch.
                        if (settings.script != Script.HAFS && settings.riwayah == Riwayah.HAFS) offline.start(Pack.Pages(settings.script))
                        offline.start(Pack.Recitation(store.reciter(settings.riwayah)))
                        offline.start(Pack.TafsirBook(settings.tafsir))
                    }, accent = true)
                }
                SwitchRow(
                    title = stringResource(R.string.wait_wifi),
                    summary = stringResource(R.string.wait_wifi_detail),
                    checked = settings.wifiOnly,
                    onChange = { on -> store.update { it.copy(wifiOnly = on) } }
                )
            }
            Section(stringResource(R.string.mushaf_pages)) {
                PackRow(Pack.Pages(Script.PRINT), stringResource(R.string.as_printed), stringResource(R.string.pages_190))
                if (Script.TAJWEED.usable) PackRow(Pack.Pages(Script.TAJWEED), stringResource(R.string.tajweed_in_colour), stringResource(R.string.pages_170))
            }
            Section(stringResource(R.string.recitations)) {
                for (r in Recitations.of(settings.riwayah)) PackRow(Pack.Recitation(r.id), r.label(), stringResource(R.string.surahs_size))
            }
            Section(stringResource(R.string.tafsir)) {
                for (b in Tafsir.BOOKS) PackRow(Pack.TafsirBook(b.id), b.name, stringResource(R.string.tafsir_book_size, java.util.Locale.forLanguageTag(b.language).getDisplayLanguage(java.util.Locale.getDefault()).replaceFirstChar { it.titlecase() }))
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
                        kept >= pack.total -> stringResource(R.string.kept_offline)
                        running -> stringResource(R.string.pack_downloading, kept, pack.total)
                        progress?.failed == true -> stringResource(R.string.pack_stopped, kept, pack.total)
                        kept > 0 -> stringResource(R.string.pack_kept, kept, pack.total, size)
                        else -> size
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                running -> QuietButton(onClick = { haptics.tick(); offline.cancel(pack) }) { Text(stringResource(R.string.pause)) }
                kept >= pack.total -> QuietButton(onClick = {
                    haptics.tick()
                    when (pack) {
                        is Pack.Pages -> fonts.remove(pack.script)
                        is Pack.TafsirBook -> tafsir.remove(Tafsir.BOOKS.first { it.id == pack.book })
                        is Pack.Recitation -> recitations.remove(pack.reciter)
                    }
                    kept = 0
                }) { Text(stringResource(R.string.remove)) }
                else -> BoldButton(onClick = { haptics.done(); offline.start(pack) }) { Text(if (kept > 0) stringResource(R.string.finish) else stringResource(R.string.keep)) }
            }
        }
        if (running) LinearProgressIndicator(progress = { kept / pack.total.toFloat() }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
    }
}
