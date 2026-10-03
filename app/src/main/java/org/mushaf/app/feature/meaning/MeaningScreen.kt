package org.mushaf.app.feature.meaning

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.compose.foundation.clickable
import org.mushaf.app.data.audio.WordAudio
import org.mushaf.app.ui.theme.quranFont
import org.mushaf.app.R
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.quran.Translations
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** One ayah as shown here: its words and the meanings chosen. */
private class AyahRow(val key: AyahKey, val words: List<Word>, val meanings: List<Pair<String, String>>)

/**
 * A surah read with its meaning: each ayah in the Hafs script, word by
 * word with the meaning of each word if the reader wants it, then the
 * translations chosen, one under the other. Opens on [start].
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
fun MeaningScreen(
    start: AyahKey,
    onBack: () -> Unit,
    onOpenTranslations: () -> Unit,
    onOpenTafsir: (AyahKey) -> Unit,
    onOpenInMushaf: (AyahKey) -> Unit
) {
    val quran: Quran = koinInject()
    val translations: Translations = koinInject()
    val store: SettingsStore = koinInject()
    val marks: Marks = koinInject()
    val settings by store.settings.collectAsState()
    val saved by marks.marks.collectAsState()
    val haptics = rememberHaptics()
    val hafs = quranFont()
    val chosen = settings.translations
    val wordAudio: WordAudio = koinInject()
    val sound by wordAudio.sound.collectAsState()

    val surah by produceState<org.mushaf.app.core.quran.Surah?>(null, start.surah) { value = quran.surah(start.surah) }
    val intro by produceState(emptyList<String>(), start.surah) { value = quran.introduction(start.surah) }
    // Every ayah of the surah with its words, read page by page.
    val rows by produceState<List<AyahRow>?>(null, start.surah, chosen) {
        val s = quran.surah(start.surah)
        val words = (s.pages.first()..s.pages.last()).flatMap { quran.page(it).words }.filter { it.key.surah == start.surah && it.key.ayah > 0 }
        value = words.groupBy { it.key }.map { (key, w) ->
            AyahRow(key, w, chosen.mapNotNull { id ->
                val text = translations.text(id, key)
                if (text.isEmpty()) null else (translations.info(id)?.name ?: id) to text
            })
        }
    }
    var showIntro by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    LaunchedEffect(rows) {
        if (rows != null && start.ayah > 1) list.scrollToItem(start.ayah) // the header is item 0
    }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop(
                title = surah?.let { "${it.n}. ${it.title}" },
                leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) },
                trailing = { FloatingAction(AppIcons.Translate, stringResource(R.string.translations), onOpenTranslations) }
            )
        }
    ) { padding ->
        val inset = LocalReadableInset.current
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, start = inset + 12.dp, end = inset + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item(key = "intro") {
                val s = surah
                ZoneSurface(
                    shape = RoundedCornerShape(24.dp),
                    onClick = { haptics.tick(); showIntro = !showIntro },
                    modifier = Modifier.fillMaxWidth().animateContentSize()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(s?.meaning.orEmpty(), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    s?.let { "${it.ayat} ayat · ${if (it.meccan) "Meccan" else "Medinan"} · revealed ${ordinal(it.order)}" }.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(s?.arabic.orEmpty(), style = TextStyle(fontFamily = hafs, fontSize = 26.sp), color = MaterialTheme.colorScheme.primary)
                        }
                        if (showIntro) {
                            for (p in intro) {
                                when {
                                    p.startsWith("# ") -> Text(p.removePrefix("# "), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 14.dp))
                                    p.startsWith("@ ") -> Text(p.removePrefix("@ "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 12.dp))
                                    else -> Text(p, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 6.dp))
                                }
                            }
                        } else if (intro.isNotEmpty()) {
                            Text(stringResource(R.string.about_surah), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 10.dp))
                        }
                    }
                }
            }
            items(rows.orEmpty(), key = { it.key.toString() }) { row ->
                val bookmarked = saved.bookmarks.any { it.key == row.key }
                val note = saved.notes.firstOrNull { it.key == row.key }?.text
                ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .combinedClickable(onClick = {}, onLongClick = { haptics.firm(); onOpenTafsir(row.key) })
                            .padding(18.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FloatingPane(shape = CircleShape) {
                                Text(
                                    row.key.toString(),
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            SmallAction(if (bookmarked) AppIcons.Bookmark else AppIcons.BookmarkOutline, if (bookmarked) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark)) {
                                haptics.toggle(marks.toggleBookmark(row.key))
                            }
                            SmallAction(AppIcons.MenuBook, stringResource(R.string.tafsir)) { onOpenTafsir(row.key) }
                            SmallAction(AppIcons.List, stringResource(R.string.show_in_mushaf)) { onOpenInMushaf(row.key) }
                        }
                        Spacer(Modifier.size(12.dp))
                        if (settings.wordByWord) {
                            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    for (w in row.words) {
                                        // A word tapped is heard on its own.
                                        val canHear = wordAudio.available(w)
                                        val hearing = sound?.let { it.of(w) && it.playing } == true
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = if (canHear) Modifier.clickable { haptics.tick(); wordAudio.play(w) } else Modifier
                                        ) {
                                            Text(
                                                w.text,
                                                style = TextStyle(fontFamily = hafs, fontSize = 28.sp, textAlign = TextAlign.Center),
                                                color = if (hearing) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                            )
                                            if (!w.end) {
                                                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                                                    Text(
                                                        w.meaning,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(top = 2.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            Text(
                                row.words.joinToString(" ") { it.text },
                                style = TextStyle(fontFamily = hafs, fontSize = 28.sp, lineHeight = 52.sp, textAlign = TextAlign.Right),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        for ((name, text) in row.meanings) {
                            Spacer(Modifier.size(12.dp))
                            Text(text, style = MaterialTheme.typography.bodyLarge)
                            if (row.meanings.size > 1) {
                                Text(name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (note != null) {
                            Spacer(Modifier.size(12.dp))
                            Text(stringResource(R.string.note_n, note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun SmallAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Box(Modifier.padding(start = 6.dp)) {
        CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
            FloatingAction(icon, label, onClick)
        }
    }
}

private fun ordinal(n: Int): String {
    val suffix = if (n % 100 in 11..13) "th" else when (n % 10) { 1 -> "st"; 2 -> "nd"; 3 -> "rd"; else -> "th" }
    return "$n$suffix"
}
