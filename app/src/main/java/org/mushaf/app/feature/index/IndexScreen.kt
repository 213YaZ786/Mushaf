package org.mushaf.app.feature.index

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.feature.common.IconControl
import org.mushaf.app.feature.common.NameDialog
import org.mushaf.app.feature.common.ControlHeight
import org.mushaf.app.ui.component.ChoiceDialog
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

private enum class Part(val label: Int) { SURAHS(R.string.surahs), JUZ(R.string.juz), HIZB(R.string.hizb), SAVED(R.string.saved) }

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
    // Saved: the collection shown (all when null), and the dialogs about collections.
    var collection by rememberSaveable { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<String?>(null) }
    var filing by remember { mutableStateOf<AyahKey?>(null) }
    var filingNew by remember { mutableStateOf<AyahKey?>(null) }
    if (adding) NameDialog(stringResource(R.string.collection_name), "", onDone = { if (marks.addCollection(it)) collection = it.trim(); adding = false }, onCancel = { adding = false })
    editing?.let { c ->
        NameDialog(
            stringResource(R.string.collection_name), c,
            onDone = { marks.renameCollection(c, it); if (collection == c) collection = it.trim(); editing = null },
            onCancel = { editing = null },
            onRemove = { marks.removeCollection(c); collection = null; editing = null },
            removeNote = stringResource(R.string.collection_forgotten)
        )
    }
    filing?.let { key ->
        val names = saved.collections
        ChoiceDialog(
            title = stringResource(R.string.put_in_collection),
            options = listOf<Pair<String?, String>>(null to stringResource(R.string.no_collection)) + names.map { it to it } + (NEW_COLLECTION to stringResource(R.string.new_collection)),
            selected = saved.bookmarks.firstOrNull { it.key == key }?.collection,
            onSelect = { c -> if (c == NEW_COLLECTION) filingNew = key else marks.file(key, c); filing = null },
            onDismiss = { filing = null }
        )
    }
    filingNew?.let { key ->
        NameDialog(stringResource(R.string.collection_name), "", onDone = { name ->
            if (marks.addCollection(name) || name.trim() in saved.collections) marks.file(key, name.trim())
            filingNew = null
        }, onCancel = { filingNew = null })
    }
    // "Seite 50", "صفحة 50": the word for page as the reader writes it.
    val pageWords = listOf(stringResource(R.string.page_title, 0), stringResource(R.string.page_n, 0))
        .map { w -> w.filterNot { it.isDigit() }.trim().lowercase() }.toSet()
    val found by produceState<List<Found>?>(null, query) {
        value = if (query.isBlank()) null else {
            delay(250)
            search.find(query, store.current.translations.firstOrNull { it != Translations.BUNDLED.id }, pageWords)
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
            finding = context.getString(R.string.finding_ayah)
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
                finding = context.getString(R.string.downloading_model)
                runCatching { recogniser.install { bytes -> finding = context.getString(R.string.downloading_model_progress, (bytes shr 20).toInt()) } }
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
            FloatingTop(stringResource(R.string.index), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) })
            EvenRows(Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp), minSlot = 64.dp) {
                // Warsh has no hizb list: its eighths are partly printed in the margin only.
                for (p in Part.entries.filter { it != Part.HIZB || meta?.quarters?.isNotEmpty() != false }) {
                    TextControl(stringResource(p.label), { part = p; query = "" }, accent = p == part && query.isBlank())
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchPill(query, { query = it; if (it.isNotEmpty()) heard = null }, stringResource(R.string.search_hint), modifier = Modifier.weight(1f), floating = true)
                FloatingAction(
                    AppIcons.Mic,
                    if (hearing.listening) stringResource(R.string.done_find) else stringResource(R.string.recite_to_find),
                    listenToFind,
                    tint = if (hearing.listening) MaterialTheme.colorScheme.error else Color.Unspecified
                )
            }
            val status = when {
                finding != null -> finding
                hearing.listening -> stringResource(R.string.listening_find)
                hearing.failed -> stringResource(R.string.mic_failed)
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
                SearchResults(hits, text.ifBlank { stringResource(R.string.nothing_heard) }, m, padding, inset, open, openAyah)
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
                        title = stringResource(R.string.juz_n, j.n),
                        detail = "${surah.title} ${j.ayah.surah}:${j.ayah.ayah} · " + stringResource(R.string.page_n, j.page),
                        here = page in j.page until next
                    ) { open(j.page) }
                }
                Part.HIZB -> items((1..60).toList(), key = { it }) { h ->
                    HizbRow(h, m, page, open)
                }
                Part.SAVED -> {
                    val all = (saved.bookmarks.map { it.key } + saved.notes.map { it.key }).distinct().sorted()
                    val shown = collection?.takeIf { it in saved.collections }
                    val keys = if (shown == null) all else saved.bookmarks.filter { it.collection == shown }.map { it.key }.sorted()
                    // The reader's collections, to show one; tapping the one shown renames or removes it.
                    if (all.isNotEmpty()) item {
                        EvenRows(minSlot = 96.dp) {
                            TextControl(stringResource(R.string.collection_all), { collection = null }, accent = shown == null)
                            for (c in saved.collections) TextControl(c, { if (c == shown) editing = c else collection = c }, accent = c == shown)
                            TextControl(stringResource(R.string.add_collection), { adding = true })
                        }
                    }
                    if (keys.isEmpty()) item {
                        if (shown == null) EmptyZone(
                            stringResource(R.string.nothing_saved),
                            stringResource(R.string.nothing_saved_hint),
                            icon = AppIcons.BookmarkOutline
                        ) else EmptyZone(
                            stringResource(R.string.collection_empty),
                            stringResource(R.string.collection_empty_hint),
                            icon = AppIcons.Folder
                        )
                    }
                    items(keys, key = { it.toString() }) { key ->
                        val note = saved.notes.firstOrNull { it.key == key }?.text
                        val mark = saved.bookmarks.firstOrNull { it.key == key }
                        SavedRow(key, m.surahs[key.surah - 1].title, mark != null, mark?.collection.takeIf { shown == null }, note, onFile = { filing = key }) { scope ->
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
                if (results.isEmpty()) stringResource(R.string.nothing_found, query) else pluralStringResource(R.plurals.results, results.size, results.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp)
            )
        }
        items(results, key = { (it as? Found.Ayah)?.key?.toString() ?: "p" }) { r ->
            when (r) {
                is Found.Page -> PlaceRow(r.page, stringResource(R.string.page_title, r.page), stringResource(R.string.open_the_page), here = false) { open(r.page) }
                is Found.Ayah -> ZoneSurface(shape = RoundedCornerShape(22.dp), onClick = { openAyah(r.key, r.page) }, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text(
                            "${m.surahs[r.key.surah - 1].title} ${r.key} · " + stringResource(R.string.page_n, r.page),
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
private fun SavedRow(
    key: AyahKey,
    surah: String,
    bookmarked: Boolean,
    collection: String?,
    note: String?,
    onFile: () -> Unit,
    onClick: (kotlinx.coroutines.CoroutineScope) -> Unit
) {
    val scope = rememberCoroutineScope()
    ZoneSurface(shape = RoundedCornerShape(22.dp), onClick = { onClick(scope) }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("$surah $key", style = MaterialTheme.typography.titleMedium)
                if (collection != null) Text(collection, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                if (note != null) Text(note, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
            if (bookmarked) Icon(AppIcons.Bookmark, contentDescription = stringResource(R.string.bookmarked), tint = MaterialTheme.colorScheme.primary)
            Box(Modifier.padding(start = 4.dp).width(ControlHeight)) { IconControl(AppIcons.Folder, stringResource(R.string.put_in_collection), onFile) }
        }
    }
}

@Composable
private fun SurahRow(s: Surah, hafs: FontFamily, here: Boolean, onClick: () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(s.n)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(s.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(s.meaningHere, pluralStringResource(R.plurals.surah_ayat, s.ayat, s.ayat), stringResource(if (s.meccan) R.string.meccan else R.string.medinan)).joinToString(" · "),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            // In Arabic the title already is the Arabic name.
            if (s.title != s.arabic) Text(s.arabic, style = TextStyle(fontFamily = hafs, fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
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
                    stringResource(R.string.hizb_juz, hizb, (hizb + 1) / 2),
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
                            Text("${surah.title} ${q.key} · " + stringResource(R.string.page_n, q.page), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** The choice that makes a new collection, apart from any name the reader could type. */
private const val NEW_COLLECTION = "\u0000new"
