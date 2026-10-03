package org.mushaf.app.feature.tafsir

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import org.mushaf.app.ui.component.ChoiceDialog
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.ui.theme.quranFont
import org.mushaf.app.data.offline.Pack
import org.mushaf.app.feature.offline.OfferDownload
import org.mushaf.app.R
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.quran.Tafsir
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.EmptyZone
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

private sealed interface Loaded {
    data object Waiting : Loaded
    data class Text(val paragraphs: List<String>) : Loaded
    data object Failed : Loaded
}

/** The tafsir of an ayah, in the book chosen; the books wrap at the top. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TafsirScreen(key: AyahKey, onBack: () -> Unit) {
    val quran: Quran = koinInject()
    val tafsir: Tafsir = koinInject()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val haptics = rememberHaptics()
    val hafs = remember { FontFamily(Font(R.font.uthmanic_hafs)) }
    // The ayah in its riwayah's font; the tafsir's own Arabic in the Hafs font.
    val ayahFont = quranFont()
    val book = Tafsir.BOOKS.firstOrNull { it.id == settings.tafsir } ?: Tafsir.BOOKS.first()
    val surah by produceState("", key.surah) { value = quran.surah(key.surah).title }
    val arabic by produceState("", key) {
        value = quran.page(quran.pageOf(key)).words.filter { it.key == key }.joinToString(" ") { it.text }
    }
    val loaded by produceState<Loaded>(Loaded.Waiting, key, book) {
        value = Loaded.Waiting
        value = runCatching { Loaded.Text(tafsir.of(book, key)) }.getOrElse { Loaded.Failed }
    }

    val kept = remember(book, loaded) { tafsir.kept(book) }
    OfferDownload(
        pack = Pack.TafsirBook(book.id),
        wanted = loaded is Loaded.Text && kept < 114,
        title = stringResource(R.string.keep_tafsir_offline),
        text = stringResource(if (settings.wifiOnly) R.string.offer_tafsir_wifi else R.string.offer_tafsir, book.name)
    )

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(stringResource(R.string.tafsir_of, surah, key.toString()), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current + 12.dp)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Text(
                    arabic,
                    style = TextStyle(fontFamily = ayahFont, fontSize = 28.sp, lineHeight = 52.sp, textAlign = TextAlign.Right),
                    modifier = Modifier.fillMaxWidth().padding(18.dp)
                )
            }
            // The book read, in one line; a tap chooses another.
            var choosing by remember { mutableStateOf(false) }
            EvenRows(Modifier.padding(vertical = 12.dp)) {
                TextControl(stringResource(R.string.another_book, book.name), { choosing = true })
            }
            if (choosing) ChoiceDialog(
                title = stringResource(R.string.tafsir),
                options = Tafsir.BOOKS.map { it.id to it.name },
                selected = book.id,
                onSelect = { id -> store.update { it.copy(tafsir = id) } },
                onDismiss = { choosing = false }
            )
            when (val l = loaded) {
                Loaded.Waiting -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { LoadingMark(size = 48.dp) }
                Loaded.Failed -> EmptyZone(stringResource(R.string.not_available), stringResource(R.string.tafsir_failed), icon = AppIcons.MenuBook)
                is Loaded.Text -> ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    CompositionLocalProvider(LocalLayoutDirection provides if (book.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
                        Column(Modifier.padding(18.dp)) {
                            for (p in l.paragraphs) {
                                val heading = p.startsWith("# ")
                                Text(
                                    p.removePrefix("# "),
                                    style = if (book.rtl) {
                                        TextStyle(fontFamily = hafs, fontSize = if (heading) 24.sp else 21.sp, lineHeight = 38.sp)
                                    } else if (heading) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                                    color = if (heading) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
                                )
                            }
                            Text(
                                stringResource(R.string.via_quran_com, book.name),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 10.dp)
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}
