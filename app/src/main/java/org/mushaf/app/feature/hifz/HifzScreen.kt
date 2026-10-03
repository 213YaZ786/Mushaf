package org.mushaf.app.feature.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.BoxWithConstraints
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.core.hifz.Order
import org.mushaf.app.core.hifz.Schedule
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.hifz.Plan
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.Section
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** What the reader already knows by heart when setting up. */
private enum class Known(val label: Int) {
    NOTHING(R.string.known_nothing),
    AMMA(R.string.known_amma),
    TWO(R.string.known_two),
    ALL(R.string.known_all)
}

/**
 * Hifz: the day's work (the new lesson, the pages learnt this week, the
 * older pages whose turn has come), the days in a row, and the map of the
 * 604 pages, each as firm as its last revisions. The first time, three
 * questions set it up.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HifzScreen(onBack: () -> Unit, onOpenMushaf: () -> Unit, onOpenTest: () -> Unit, onOpenSimilar: () -> Unit) {
    val hifz: Hifz = koinInject()
    val session: HifzSession = koinInject()
    val quran: Quran = koinInject()
    val state by hifz.state.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var editing by rememberSaveable { mutableStateOf(false) }
    val plan = state.plan

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop(
                stringResource(R.string.hifz),
                leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) },
                trailing = { if (plan != null) FloatingAction(AppIcons.Settings, stringResource(R.string.plan), { editing = !editing }) }
            )
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            if (plan == null || editing) {
                Setup(plan) { p, known ->
                    scope.launch {
                        hifz.setPlan(p)
                        if (known != null) {
                            val ayat = quran.ayat()
                            val keys = when (known) {
                                Known.NOTHING -> emptyList()
                                Known.AMMA -> ayat.filter { it.juz == 30 }.map { it.key }
                                Known.TWO -> ayat.filter { it.juz >= 29 }.map { it.key }
                                Known.ALL -> ayat.map { it.key }
                            }
                            if (keys.isNotEmpty()) hifz.setKnown(keys, true)
                        }
                        editing = false
                    }
                }
            } else {
                Today(hifz, session, onOpenMushaf)
                Section(stringResource(R.string.test_yourself)) {
                    EvenRows(Modifier.padding(12.dp), minSlot = 140.dp) {
                        TextControl(stringResource(R.string.continue_ayah), onOpenTest, accent = true)
                        TextControl(stringResource(R.string.similar_ayat), onOpenSimilar)
                    }
                }
                Slipped(hifz, session, onOpenMushaf)
                Section(stringResource(R.string.the_pages)) { PageMap(hifz, onOpenMushaf) }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Setup(plan: Plan?, onDone: (Plan, Known?) -> Unit) {
    val haptics = rememberHaptics()
    var known by remember { mutableStateOf<Known?>(if (plan == null) Known.NOTHING else null) }
    var lines by remember { mutableStateOf(plan?.lines ?: 7) }
    var order by remember { mutableStateOf(plan?.order ?: Order.FROM_AN_NAS) }
    if (plan == null) {
        Section(stringResource(R.string.known_question)) {
            Choices(Known.entries.map { it to stringResource(it.label) }, known) { haptics.tick(); known = it }
            Text(
                stringResource(R.string.known_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
    Section(stringResource(R.string.learn_each_day)) {
        Choices(listOf(0 to stringResource(R.string.revision_only), 3 to stringResource(R.string.three_lines), 7 to stringResource(R.string.half_page), 15 to stringResource(R.string.a_page), 30 to stringResource(R.string.two_pages_learn)), lines) { haptics.tick(); lines = it }
    }
    Section(stringResource(R.string.in_this_order)) {
        Choices(listOf(Order.FROM_AN_NAS to stringResource(R.string.order_an_nas), Order.FROM_AL_FATIHAH to stringResource(R.string.order_fatihah)), order) { haptics.tick(); order = it }
    }
    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.End) {
        BoldButton(filled = true, onClick = { haptics.done(); onDone(Plan(lines = lines, order = order, perDay = plan?.perDay ?: 10), known) }) {
            Text(if (plan == null) stringResource(R.string.start) else stringResource(R.string.save))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(options: List<Pair<T, String>>, chosen: T?, onChoose: (T) -> Unit) {
    EvenRows(Modifier.padding(16.dp), minSlot = 96.dp) {
        for ((value, label) in options) TextControl(label, { onChoose(value) }, accent = value == chosen)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Today(hifz: Hifz, session: HifzSession, onOpenMushaf: () -> Unit) {
    val quran: Quran = koinInject()
    val state by hifz.state.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val lesson by produceState<List<AyahKey>?>(null, state.known, state.plan) { value = hifz.lesson() }
    val revision = remember(state) { hifz.revision() }
    val streak = remember(state) { hifz.streak() }
    val lessonPage by produceState<Int?>(null, lesson) { value = lesson?.firstOrNull()?.let { quran.pageOf(it) } }
    val surahName by produceState("", lesson) { value = lesson?.firstOrNull()?.let { quran.surah(it.surah).name }.orEmpty() }

    Section(if (streak > 1) stringResource(R.string.today_streak, streak) else stringResource(R.string.today)) {
        val l = lesson
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            when {
                l == null -> LoadingMark(size = 32.dp)
                l.isEmpty() -> Text(stringResource(R.string.no_new_lesson), style = MaterialTheme.typography.bodyMedium)
                else -> Card(
                    title = stringResource(R.string.sabaq),
                    detail = "$surahName ${l.first()}" + (if (l.size > 1) "–${l.last().ayah}" else "") + " · " + stringResource(R.string.page_n, lessonPage ?: 0),
                    action = stringResource(R.string.learn)
                ) {
                    haptics.tick()
                    lessonPage?.let { session.startLesson(l, it); onOpenMushaf() }
                }
            }
            if (revision.recent.isNotEmpty()) {
                PagesCard(stringResource(R.string.sabqi), revision.recent) { p -> scope.launch { session.startRevision(p); onOpenMushaf() } }
            }
            if (revision.due.isNotEmpty()) {
                PagesCard(
                    stringResource(R.string.manzil) + if (revision.waiting > 0) " · " + pluralStringResource(R.plurals.more_next_days, revision.waiting, revision.waiting) else "",
                    revision.due
                ) { p -> scope.launch { session.startRevision(p); onOpenMushaf() } }
            }
            if (revision.recent.isEmpty() && revision.due.isEmpty() && state.pages.isNotEmpty()) {
                Text(stringResource(R.string.revision_done), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Card(title: String, detail: String, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        BoldButton(filled = true, onClick = onClick) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PagesCard(title: String, pages: List<Int>, onPage: (Int) -> Unit) {
    val haptics = rememberHaptics()
    Column {
        Text(title, style = MaterialTheme.typography.titleMedium)
        EvenRows(Modifier.padding(top = 8.dp), minSlot = 72.dp) {
            for (p in pages) TextControl("p. $p", { onPage(p) })
        }
    }
}

/**
 * The 604 pages, a juz to a row: a page not known is an outline, a known
 * page fills with the accent as it gets firmer. The rows wrap on a narrow
 * screen. A tap revises the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PageMap(hifz: Hifz, onOpenMushaf: () -> Unit) {
    val haptics = rememberHaptics()
    val quran: Quran = koinInject()
    val session: HifzSession = koinInject()
    val state by hifz.state.collectAsState()
    val scope = rememberCoroutineScope()
    val meta by produceState(quran.metaNow, quran) { value = quran.meta() }
    val today = hifz.today()
    val byPage = remember(state) { state.pages.associateBy { it.page } }
    val weak = MaterialTheme.colorScheme.surfaceContainerHighest
    val firm = MaterialTheme.colorScheme.primary
    val outline = MaterialTheme.colorScheme.outlineVariant
    val m = meta ?: return
    // The longest juz fills the width: each page as large as the screen allows.
    val longest = remember(m) { m.juz.maxOf { j -> (m.juz.getOrNull(j.n)?.page ?: 605) - j.page } }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(12.dp)) {
    val cell = ((maxWidth - 26.dp) / longest - 3.dp).coerceIn(8.dp, 28.dp)
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(stringResource(R.string.pages_known, state.pages.size), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 5.dp))
        for (j in m.juz) {
            val end = m.juz.getOrNull(j.n)?.page ?: 605
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("${j.n}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(23.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (p in j.page until end) {
                        val s = byPage[p]
                        Box(
                            Modifier
                                .size(cell)
                                .clip(RoundedCornerShape(3.dp))
                                .then(
                                    if (s == null) Modifier.border(1.dp, outline, RoundedCornerShape(3.dp))
                                    else Modifier.background(lerp(weak, firm, Schedule.strength(s, today)))
                                )
                                .clickable(enabled = s != null) { haptics.tick(); scope.launch { session.startRevision(p); onOpenMushaf() } }
                        )
                    }
                }
            }
        }
    }
    }
}

/**
 * The words that slipped most while reciting from memory, the ayat to
 * revise first: a tap revises their page.
 */
@Composable
private fun Slipped(hifz: Hifz, session: HifzSession, onOpenMushaf: () -> Unit) {
    val quran: Quran = koinInject()
    val state by hifz.state.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val font = org.mushaf.app.ui.theme.quranFont()
    val top = remember(state.slips) { state.slips.sortedWith(compareByDescending<org.mushaf.app.data.hifz.Slip> { it.count }.thenByDescending { it.last }).take(8) }
    if (top.isEmpty()) return
    val rows by produceState<List<Triple<org.mushaf.app.data.hifz.Slip, String, Int>>>(emptyList(), top) {
        value = top.mapNotNull { s ->
            val page = runCatching { quran.pageOf(s.key) }.getOrNull() ?: return@mapNotNull null
            val word = quran.page(page).words.firstOrNull { it.key == s.key && it.position == s.position }?.text ?: return@mapNotNull null
            Triple(s, word, page)
        }
    }
    Section(stringResource(R.string.words_slipped)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((s, word, page) in rows) {
                val surah by produceState("", s.key.surah) { value = quran.surah(s.key.surah).name }
                ZoneSurface(
                    shape = RoundedCornerShape(20.dp),
                    onClick = { haptics.tick(); scope.launch { session.startRevision(page); onOpenMushaf() } },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("$surah ${s.key}", style = MaterialTheme.typography.titleSmall)
                            Text(
                                pluralStringResource(R.plurals.slipped_times, s.count, s.count) + " · " + stringResource(R.string.page_n, page),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(word, style = androidx.compose.ui.text.TextStyle(fontFamily = font, fontSize = 26.sp), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
