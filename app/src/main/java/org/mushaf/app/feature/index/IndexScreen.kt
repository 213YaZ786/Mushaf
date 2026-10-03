package org.mushaf.app.feature.index

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.feature.recite.Recite
import org.mushaf.app.feature.recite.ModelOffer
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.core.stt.Find
import org.mushaf.app.core.quran.Arabic
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.core.content.ContextCompat
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.DisposableEffect
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.content.pm.PackageManager
import android.Manifest
import org.mushaf.app.ui.theme.quranFont
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.data.quran.Found
import org.mushaf.app.data.quran.Search
import org.mushaf.app.data.quran.Translations
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.EmptyZone
import org.mushaf.app.ui.component.SearchPill
import androidx.compose.material3.Icon
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.text.style.TextAlign
import org.mushaf.app.R
import org.mushaf.app.core.quran.QuranMeta
import org.mushaf.app.core.quran.Surah
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.feature.mushaf.Reader
import org.mushaf.app.feature.mushaf.hizbLabel
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

private enum class Part(val label: String) { SURAHS("Surahs"), JUZ("Juz"), HIZB("Hizb"), SAVED("Saved") }

/**
 * Where to go in the mushaf: the surahs, the 30 juz, the 60 hizb and their
 * quarters. The place being read is marked; a tap opens its page.
 */
@Composable
fun IndexScreen(onBack: () -> Unit) {
    val quran: Quran = koinInject()
    val reader: Reader = koinInject()
    val search: Search = koinInject()
    val marks: Marks = koinInject()
    val store: SettingsStore = koinInject()
    val saved by marks.marks.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    val found by produceState<List<Found>?>(null, query) {
        value = if (query.isBlank()) null else {
            delay(250)
            search.find(query, store.current.translations.firstOrNull { it != Translations.BUNDLED.id })
        }
    }
    val haptics = rememberHaptics()
    val meta by produceState(quran.metaNow, quran) { value = quran.meta() }

    // Finding an ayah by reciting it: heard on the phone, then the closest ayat.
    val recite: Recite = koinInject()
    val recogniser: Recogniser = koinInject()
    val hearing by recite.state.collectAsState()
    val modelReady by recogniser.ready.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var heard by remember { mutableStateOf<Pair<String, List<Found>>?>(null) }
    var finding by remember { mutableStateOf<String?>(null) }
    var offerModel by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { recite.reset() } }
    val startHearing = {
        query = ""
        heard = null
        finding = null
        recite.hear { text ->
            finding = "Finding the ayah…"
            scope.launch {
                val ayat = quran.ayat()
                val hits = withContext(Dispatchers.Default) {
                    Find.rank(text, ayat.map { it.key to Arabic.words(it.plain) })
                }
                val byKey = ayat.associateBy { it.key }
                heard = text to hits.mapNotNull { h -> byKey[h.key]?.let { Found.Ayah(it.key, it.page, it.plain, true) } }
                finding = null
                if (hits.isEmpty()) haptics.reject() else haptics.done()
            }
        }
    }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) startHearing() }
    val listenToFind = {
        haptics.tick()
        when {
            hearing.listening -> recite.finish()
            !modelReady -> offerModel = true
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startHearing()
            else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    if (offerModel) ModelOffer(
        onYes = {
            offerModel = false
            scope.launch {
                finding = "Downloading the speech model…"
                runCatching { recogniser.install { bytes -> finding = "Downloading the speech model · ${bytes shr 20} of 80 MB" } }
                    .onFailure { finding = null; haptics.reject() }
                    .onSuccess {
                        finding = null
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startHearing()
                        else askMic.launch(Manifest.permission.RECORD_AUDIO)
                    }
            }
        },
        onNo = { offerModel = false }
    )
    val page by reader.page.collectAsState()
    var part by rememberSaveable { mutableStateOf(Part.SURAHS) }
    val hafs = remember { FontFamily(Font(R.font.uthmanic_hafs)) }

    val open = { target: Int ->
        haptics.tick()
        reader.go(target)
        onBack()
    }
    val openAyah = { key: AyahKey, page: Int ->
        haptics.tick()
        reader.go(page, key)
        onBack()
    }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop("Index", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) })
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                // Warsh has no hizb list: its eighths are partly printed in the margin only.
                for (p in Part.entries.filter { it != Part.HIZB || meta?.quarters?.isNotEmpty() != false }) {
                    FloatingPane(shape = CircleShape, accent = p == part && query.isBlank(), onClick = { haptics.tick(); part = p; query = "" }) {
                        Text(p.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp))
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchPill(query, { query = it; if (it.isNotEmpty()) heard = null }, "Search: words, meaning, 2:255, page 50", modifier = Modifier.weight(1f), floating = true)
                FloatingAction(
                    AppIcons.Mic,
                    if (hearing.listening) "Done, find it" else "Recite to find the ayah",
                    listenToFind,
                    tint = if (hearing.listening) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            val status = when {
                finding != null -> finding
                hearing.listening -> "Listening: recite a few words, then tap the microphone."
                hearing.failed -> "The microphone could not be opened."
                else -> null
            }
            if (status != null) Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)
            )
        }
    ) { padding ->
        val m = meta ?: return@FloatingFrame
        if (part == Part.HIZB && m.quarters.isEmpty()) part = Part.JUZ
        val inset = LocalReadableInset.current
        val list = rememberLazyListState()
        // Opens on the place being read.
        LaunchedEffect(part) {
            val index = when (part) {
                Part.SURAHS -> m.surahs.indexOfLast { it.firstPage <= page }
                Part.JUZ -> m.juz.indexOfLast { it.page <= page }
                Part.HIZB -> (m.quarters.indexOfLast { it.page <= page } / 4)
                Part.SAVED -> 0
            }.coerceAtLeast(0)
            list.scrollToItem((index - 2).coerceAtLeast(0))
        }
        heard?.let { (text, hits) ->
            if (query.isBlank()) {
                SearchResults(hits, text.ifBlank { "nothing heard" }, m, padding, inset, open, openAyah)
                return@FloatingFrame
            }
        }
        val results = found
        if (results != null) {
            SearchResults(results, query, m, padding, inset, open, openAyah)
            return@FloatingFrame
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, start = inset + 12.dp, end = inset + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (part) {
                Part.SURAHS -> items(m.surahs, key = { it.n }) { s ->
                    val here = page in s.pages.first()..s.pages.last()
                    SurahRow(s, hafs, here) { open(s.firstPage) }
                }
                Part.JUZ -> items(m.juz, key = { it.n }) { j ->
                    val next = m.juz.getOrNull(j.n)?.page ?: 605
                    val surah = m.surahs[j.ayah.surah - 1]
                    PlaceRow(
                        number = j.n,
                        title = "Juz ${j.n}",
                        detail = "${surah.name} ${j.ayah.surah}:${j.ayah.ayah} · page ${j.page}",
                        here = page in j.page until next
                    ) { open(j.page) }
                }
                Part.HIZB -> items((1..60).toList(), key = { it }) { h ->
                    HizbRow(h, m, page, open)
                }
                Part.SAVED -> {
                    val keys = (saved.bookmarks.map { it.key } + saved.notes.map { it.key }).distinct().sorted()
                    if (keys.isEmpty()) item {
                        EmptyZone(
                            "Nothing saved yet",
                            "Long press an ayah in the mushaf to bookmark it or write a note.",
                            icon = AppIcons.BookmarkOutline
                        )
                    }
                    items(keys, key = { it.toString() }) { key ->
                        val note = saved.notes.firstOrNull { it.key == key }?.text
                        val marked = saved.bookmarks.any { it.key == key }
                        SavedRow(key, m.surahs[key.surah - 1].name, marked, note) { scope ->
                            scope.launch { openAyah(key, quran.pageOf(key)) }
                        }
                    }
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun SearchResults(
    results: List<Found>,
    query: String,
    m: QuranMeta,
    padding: PaddingValues,
    inset: androidx.compose.ui.unit.Dp,
    open: (Int) -> Unit,
    openAyah: (AyahKey, Int) -> Unit
) {
    val hafs = quranFont()
    LazyColumn(
        contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, start = inset + 12.dp, end = inset + 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Text(
                when (results.size) { 0 -> "Nothing found for “$query”"; 1 -> "1 result"; else -> "${results.size} results" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
        items(results, key = { (it as? Found.Ayah)?.key?.toString() ?: "p" }) { r ->
            when (r) {
                is Found.Page -> PlaceRow(r.page, "Page ${r.page}", "Open the page", here = false) { open(r.page) }
                is Found.Ayah -> ZoneSurface(shape = RoundedCornerShape(22.dp), onClick = { openAyah(r.key, r.page) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "${m.surahs[r.key.surah - 1].name} ${r.key} · page ${r.page}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            r.text,
                            style = if (r.arabic) TextStyle(fontFamily = hafs, fontSize = 22.sp, textAlign = TextAlign.Right) else MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
    }
}

@Composable
private fun SavedRow(key: AyahKey, surah: String, bookmarked: Boolean, note: String?, onClick: (kotlinx.coroutines.CoroutineScope) -> Unit) {
    val scope = rememberCoroutineScope()
    ZoneSurface(shape = RoundedCornerShape(22.dp), onClick = { onClick(scope) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$surah $key", style = MaterialTheme.typography.titleMedium)
                if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (bookmarked) Icon(AppIcons.Bookmark, contentDescription = "Bookmarked", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun SurahRow(s: Surah, hafs: FontFamily, here: Boolean, onClick: () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(s.n)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.meaning} · ${s.ayat} ayat · ${if (s.meccan) "Meccan" else "Medinan"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(s.arabic, style = TextStyle(fontFamily = hafs, fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlaceRow(number: Int, title: String, detail: String, here: Boolean, onClick: () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(number)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A hizb and its four quarters, which wrap under it on a narrow screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HizbRow(hizb: Int, m: QuranMeta, page: Int, open: (Int) -> Unit) {
    val quarters = m.quarters.subList((hizb - 1) * 4, hizb * 4)
    val end = m.quarters.getOrNull(hizb * 4)?.page ?: 605
    val here = page in quarters.first().page until end
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Number(hizb)
                Text(
                    "Hizb $hizb · Juz ${(hizb + 1) / 2}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                for (q in quarters) {
                    val surah = m.surahs[q.ayah.surah - 1]
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { open(q.page) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(hizbLabel(q.n), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${surah.name} ${q.key} · p. ${q.page}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Number(n: Int) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        ZoneSurface(shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(n.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
