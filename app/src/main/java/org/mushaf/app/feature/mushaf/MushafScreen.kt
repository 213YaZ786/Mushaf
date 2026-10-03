package org.mushaf.app.feature.mushaf

import android.app.Activity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.fontResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.data.khatmah.Khatmah
import org.mushaf.app.data.quran.Script
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import org.mushaf.app.core.quran.PageLine
import org.mushaf.app.data.quran.Riwayat
import org.mushaf.app.feature.hifz.Spot
import org.mushaf.app.ui.component.Glide
import org.mushaf.app.ui.component.reducedMotion
import org.mushaf.app.ui.theme.quranFont
import org.mushaf.app.ui.theme.basmalaFor
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.R
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.MushafPage
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.feature.hifz.HifzPane
import org.mushaf.app.feature.hifz.HifzSession
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.data.offline.Pack
import org.mushaf.app.feature.offline.OfferDownload
import org.mushaf.app.feature.listen.ListenPane

/**
 * The mushaf: its pages fill the window, turned from right to left. A tap
 * shows the glass over them (where the reader is, the index, settings) or
 * hides it with the system bars; a long press on a word opens its ayah.
 * On a wide window two pages face each other, as in a book.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MushafScreen(
    onOpenIndex: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMeaning: (AyahKey) -> Unit,
    onOpenTafsir: (AyahKey) -> Unit,
    onOpenHifz: () -> Unit,
    onOpenPlay: () -> Unit
) {
    val quran: Quran = koinInject()
    val fonts: PageFonts = koinInject()
    val reader: Reader = koinInject()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()

    // The riwayah's own font; Hafs alone has the print fonts of each page.
    val hafs = quranFont()
    val warsh = settings.riwayah == Riwayah.WARSH
    val surahNames = remember { FontFamily(Font(R.font.surah_names)) }
    val arrived by fonts.arrived.collectAsState()
    val marked by reader.marked.collectAsState()
    val listen: Listen = koinInject()
    val heard by listen.state.collectAsState()
    val hifz: HifzSession = koinInject()
    val session by hifz.session.collectAsState()
    val meta by produceState(quran.metaNow, quran) { value = quran.meta() }

    var chrome by rememberSaveable { mutableStateOf(true) }
    var opened by remember { mutableStateOf<Word?>(null) }
    var legend by remember { mutableStateOf(false) }
    var khatmahOpen by remember { mutableStateOf(false) }
    val khatmah: Khatmah = koinInject()
    val reduce = reducedMotion()
    // A riwayah just chosen: its name in the top pill a moment, the pages written again.
    val riwayat: Riwayat = koinInject()
    val announced by riwayat.announced.collectAsState()
    LaunchedEffect(announced) {
        if (announced == null) return@LaunchedEffect
        chrome = true
        delay(2200)
        riwayat.announcedShown()
    }
    // The page reached by a jump (the index, a search): a surah opening there rises into place.
    var jumped by remember { mutableStateOf<Int?>(null) }

    val recitations: Recitations = koinInject()
    val surahPlaying = heard.key?.surah
    OfferDownload(
        pack = Pack.Recitation(store.reciter(settings.riwayah)),
        wanted = heard.playing && remember(settings.reciter, settings.warshReciter, settings.riwayah) { recitations.kept(store.reciter(settings.riwayah)) } < 114,
        title = "Keep this recitation offline?",
        text = Recitations.reciter(store.reciter(settings.riwayah)).name + "'s recitation of the whole Quran, kept on the phone to listen without a connection. It downloads in the background" +
            if (settings.wifiOnly) ", on Wi-Fi." else ".",
        one = "This surah only" to {
            surahPlaying?.let { n -> scope.launch { runCatching { recitations.downloadSurah(store.reciter(settings.riwayah), n) } } }
            Unit
        }
    )

    // The basmala as this riwayah's mushaf writes it: Warsh's from its first page.
    val basmalaText by produceState(basmalaFor(settings.riwayah), settings.riwayah) {
        if (settings.riwayah == Riwayah.WARSH) {
            value = quran.page(1).words.filter { it.key.surah == 1 && it.key.ayah == 0 }.joinToString(" ") { it.text }
                .ifEmpty { basmalaFor(Riwayah.WARSH) }
        }
    }

    LaunchedEffect(Unit) { reader.read() }

    KeepScreenOn(settings.keepScreenOn)
    SystemBars(visible = chrome || opened != null)

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Two pages need a tablet's height too; a phone on its side shows one
        // page across its width, scrolled down like a page held close.
        val spread = settings.twoPages && maxWidth > maxHeight && maxWidth >= 700.dp && maxHeight >= 500.dp
        val scrolled = !spread && maxWidth > maxHeight && maxHeight < 500.dp
        val pageHeight = maxWidth * 1.45f
        val perItem = if (spread) 2 else 1
        val count = if (spread) PAGES / 2 else PAGES
        // A new pager when one page becomes two or back: its saved place counts in spreads or in pages.
        val pager = key(perItem) { rememberPagerState(initialPage = (reader.page.value - 1) / perItem) { count } }
        val current = pager.currentPage * perItem + 1
        val settled = !pager.isScrollInProgress
        val paper = MaterialTheme.colorScheme.surface
        val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f

        // The page shown is remembered; the fonts of the pages around it are fetched ahead.
        LaunchedEffect(pager, perItem) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { item ->
                val first = item * perItem + 1
                reader.shown(first)
                khatmah.shown(first + perItem - 1, perItem)
                fonts.prefetch(settings.script, (first - 2)..(first + perItem + 2))
            }
        }
        // The page follows the voice.
        val heardKey = heard.key
        LaunchedEffect(heardKey, settings.followVoice) {
            val key = heardKey ?: return@LaunchedEffect
            if (!settings.followVoice) return@LaunchedEffect
            val page = quran.pageOf(key)
            if (page !in current until current + perItem) pager.animateScrollToPage((page - 1) / perItem)
        }
        // A page asked for by the index or a search.
        val goTo by reader.goTo.collectAsState()
        LaunchedEffect(goTo, perItem) {
            val target = goTo ?: return@LaunchedEffect
            jumped = target
            pager.scrollToPage((target - 1) / perItem)
            reader.wentTo()
        }
        // The mark of a jump stays until the reader turns the page.
        LaunchedEffect(pager) {
            snapshotFlow { pager.currentPage }.distinctUntilChanged().collect {
                if (marked != null && reader.goTo.value == null) reader.unmark()
            }
        }

        FloatingFrame(
            bottom = 0.dp,
            top = {
                AnimatedVisibility(
                    visible = chrome,
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 }
                ) {
                    val surah = meta?.let { m -> pageSurah(m.pageStart, current)?.let { m.surahs[it - 1] } }
                    FloatingTop(
                        title = null,
                        leading = { FloatingAction(AppIcons.MenuBook, "Index", onOpenIndex) },
                        trailing = { FloatingAction(AppIcons.Settings, "Settings", onOpenSettings) },
                        center = {
                            FloatingPane(shape = CircleShape, onClick = { haptics.tick(); onOpenIndex() }) {
                                AnimatedContent(
                                    targetState = announced?.label ?: surah?.name ?: " ",
                                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(200)) },
                                    label = "title"
                                ) { title ->
                                    Text(
                                        title,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    )
                }
            },
            overlay = {
                Column(
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AnimatedVisibility(
                        visible = heard.active && opened == null,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        ListenPane()
                    }
                    val s = session
                    AnimatedVisibility(
                        // Hidden with the rest of the glass by a tap on the page, so the lesson's lines show whole.
                        visible = s != null && opened == null && chrome,
                        enter = fadeIn() + slideInVertically { it / 2 },
                        exit = fadeOut() + slideOutVertically { it / 2 }
                    ) {
                        var last by remember { mutableStateOf(s) }
                        s?.let { last = it }
                        val shownSession = last
                        if (shownSession != null) {
                            val pageWords by produceState(emptyList<Word>(), current, perItem) {
                                value = (current until current + perItem).flatMap { quran.page(it).words }
                            }
                            HifzPane(shownSession, pageWords)
                        }
                    }
                }
                AnimatedVisibility(
                    visible = chrome && opened == null && !legend && !khatmahOpen && !heard.active && session == null,
                    enter = fadeIn() + scaleIn(initialScale = 0.9f),
                    exit = fadeOut() + scaleOut(targetScale = 0.9f),
                    modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                ) {
                    val juz = meta?.juz?.lastOrNull { it.page <= current }?.n
                    val quarter = meta?.quarters?.lastOrNull { it.page <= current }?.n
                    // On a narrow screen the round actions wrap above the page's place.
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        FloatingAction(AppIcons.School, "Hifz", onOpenHifz)
                        FloatingAction(AppIcons.Puzzle, "Play", onOpenPlay)
                        if (!warsh && settings.script == Script.TAJWEED && settings.script.usable) {
                            FloatingAction(AppIcons.Palette, "Tajweed colours", { haptics.tick(); legend = true })
                        }
                        FloatingAction(AppIcons.Translate, "Read with meaning", {
                            scope.launch { onOpenMeaning(quran.firstAyah(current)) }
                        })
                        PagePill(current, if (spread) current + 1 else null, juz, quarter) { haptics.tick(); khatmahOpen = true }
                        FloatingAction(AppIcons.Play, "Listen", {
                            scope.launch { listen.play(quran.firstAyah(current)) }
                        })
                    }
                }
                AnimatedVisibility(
                    visible = khatmahOpen && opened == null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    KhatmahSheet(current, onGo = { p -> khatmahOpen = false; reader.go(p) }) { khatmahOpen = false }
                }
                AnimatedVisibility(
                    visible = legend && opened == null,
                    enter = fadeIn() + slideInVertically { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    TajweedLegend(dark) { legend = false }
                }
                AnimatedVisibility(
                    visible = opened != null,
                    // Once the ayah has lit up from the word held.
                    enter = fadeIn(tween(240, delayMillis = if (reduce) 0 else 260)) +
                        slideInVertically(tween(420, delayMillis = if (reduce) 0 else 260, easing = Glide)) { it / 2 },
                    exit = fadeOut() + slideOutVertically { it / 2 },
                    modifier = Modifier.align(Alignment.BottomCenter)
                ) {
                    var last by remember { mutableStateOf(opened) }
                    opened?.let { last = it }
                    last?.let { word ->
                        AyahSheet(
                            word, hafs,
                            onClose = { opened = null },
                            onOpenMeaning = { k -> opened = null; onOpenMeaning(k) },
                            onOpenTafsir = { k -> opened = null; onOpenTafsir(k) },
                            onPlay = { k -> opened = null; listen.play(k) }
                        )
                    }
                }
            }
        ) { _ ->
            // The pages keep clear of the system bars even when those are hidden,
            // so a page never moves when the glass comes and goes.
            val bars = WindowInsets.systemBarsIgnoringVisibility.union(WindowInsets.displayCutout).asPaddingValues()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HorizontalPager(
                    state = pager,
                    beyondViewportPageCount = 1,
                    modifier = Modifier.fillMaxSize()
                ) { item ->
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            Modifier
                                .fillMaxSize()
                                .then(if (reduce) Modifier else Modifier.pageTurn(pager, item, paper))
                                .padding(bars)
                                .padding(horizontal = if (spread) 24.dp else 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(32.dp)
                        ) {
                            // Right to left: the odd page on the right, as in a printed mushaf.
                            val numbers = if (spread) listOf(item * 2 + 2, item * 2 + 1) else listOf(item + 1)
                            for (n in numbers) {
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .then(if (scrolled) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                                ) {
                                    val page by produceState<MushafPage?>(null, n, settings.riwayah) { value = quran.page(n) }
                                    val shown = page
                                    if (shown != null) {
                                        val opensSurah = remember(shown) { shown.lines.any { it is PageLine.Title } }
                                        // Read again when a font arrives.
                                        val print = remember(n, settings.script, arrived, warsh, dark) {
                                            if (!warsh && settings.script.usable) fonts.family(settings.script, n, dark) else null
                                        }
                                        PageView(
                                            page = shown,
                                            print = print,
                                            hafs = hafs,
                                            basmala = remember(settings.script, arrived, warsh, dark) {
                                                if (!warsh && settings.script.usable) fonts.family(settings.script, 1, dark) else null
                                            },
                                            basmalaText = basmalaText,
                                            surahNames = surahNames,
                                            marked = marked ?: heard.key.takeIf { !settings.followVoice },
                                            heard = heard.key.takeIf { settings.followVoice },
                                            heardWord = heard.heard?.word,
                                            held = opened,
                                            slipped = { w -> session?.slipped?.contains(Spot(w.key, w.position)) == true },
                                            opening = opensSurah && n !in reader.opened,
                                            entrance = when {
                                                announced != null -> Entrance.INK
                                                jumped == n && opensSurah && n !in reader.opened -> Entrance.RISE
                                                else -> null
                                            },
                                            active = settled && n in current until current + perItem,
                                            onShown = {
                                                if (opensSurah) reader.opened += n
                                                if (jumped == n) jumped = null
                                            },
                                            show = { w -> session?.showOf(w) ?: WordShow.ALL },
                                            onWordTap = { w ->
                                                val sess = session
                                                if (sess != null && sess.showOf(w) != WordShow.ALL) { haptics.tick(); hifz.reveal(w); true } else false
                                            },
                                            onTap = {
                                                when {
                                                    opened != null -> opened = null
                                                    legend -> legend = false
                                                    khatmahOpen -> khatmahOpen = false
                                                    else -> chrome = !chrome
                                                }
                                            },
                                            modifier = if (scrolled) Modifier.height(pageHeight) else Modifier,
                                            onLongPress = { w ->
                                                // The basmala Warsh prints before al-Fatihah is not an ayah.
                                                if (w.key.ayah == 0) return@PageView
                                                haptics.firm()
                                                if (session != null && w.key in session!!.keys) hifz.slip(w) else opened = w
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The page turned as paper: held in place, it lifts from the spine on its
 * right and turns over the next one, which waits beneath in the shadow it
 * casts. Follows the finger; let go early, it falls back.
 */
private fun Modifier.pageTurn(pager: PagerState, item: Int, paper: Color): Modifier = this
    .zIndex(-item.toFloat())
    .graphicsLayer {
        // 0 at rest, 0 to 1 while this page turns away, -1 to 0 while it waits beneath.
        val off = (pager.currentPage - item) + pager.currentPageOffsetFraction
        if (off == 0f || off <= -1f || off >= 1f) return@graphicsLayer
        // The pager slides its pages (right to left); here they keep still.
        translationX = -off * size.width
        if (off > 0f) {
            transformOrigin = TransformOrigin(1f, 0.5f)
            cameraDistance = 60f * density
            rotationY = TURN_SIGN * 90f * off
        }
    }
    .drawWithContent {
        val off = (pager.currentPage - item) + pager.currentPageOffsetFraction
        if (off > 0f && off < 1f) {
            // The sheet turning is opaque, and darkens as it lifts.
            drawRect(paper)
            drawContent()
            drawRect(
                Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.32f * off), 1f to Color.Transparent),
                alpha = 1f
            )
        } else if (off < 0f && off > -1f) {
            drawContent()
            // The shadow the lifted page casts, strongest half way.
            val lift = 1f + off
            val k = 4f * lift * (1f - lift)
            drawRect(Brush.horizontalGradient(0f to Color.Transparent, 0.55f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.22f * k)))
        } else drawContent()
    }

/** Which way rotationY lifts the page's free edge toward the reader. */
private const val TURN_SIGN = 1f

/** The surah the page starts in. */
private fun pageSurah(pageStart: List<String>, page: Int): Int? =
    pageStart.getOrNull(page - 1)?.let { AyahKey.parse(it)?.surah }

@Composable
private fun PagePill(page: Int, second: Int?, juz: Int?, quarter: Int?, onClick: () -> Unit) {
    // A tap opens the khatmah.
    FloatingPane(shape = CircleShape, onClick = onClick) {
        val pages = if (second != null) "Pages $page–$second" else "Page $page"
        val place = buildList {
            juz?.let { add("Juz $it") }
            quarter?.let { add(hizbLabel(it)) }
        }.joinToString(" · ")
        Text(
            if (place.isEmpty()) pages else "$pages · $place",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

/** "Hizb 3", "¼ Hizb 3", "½ Hizb 3", "¾ Hizb 3" for quarter n of 240. */
fun hizbLabel(quarter: Int): String {
    val hizb = (quarter - 1) / 4 + 1
    return when ((quarter - 1) % 4) {
        0 -> "Hizb $hizb"
        1 -> "¼ Hizb $hizb"
        2 -> "½ Hizb $hizb"
        else -> "¾ Hizb $hizb"
    }
}

@Composable
private fun KeepScreenOn(on: Boolean) {
    val view = LocalView.current
    DisposableEffect(on) {
        view.keepScreenOn = on
        onDispose { view.keepScreenOn = false }
    }
}

/** The status and navigation bars follow the glass: hidden with it, back with a swipe. */
@Composable
private fun SystemBars(visible: Boolean) {
    val view = LocalView.current
    DisposableEffect(visible) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (visible) controller?.show(WindowInsetsCompat.Type.systemBars())
        else controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
}
