package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import org.mushaf.app.ui.component.Glide
import org.mushaf.app.ui.component.reducedMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.MushafPage
import org.mushaf.app.core.quran.PageLayout
import org.mushaf.app.core.quran.PageLine
import org.mushaf.app.core.quran.Word
import org.mushaf.app.ui.component.ZoneSurface

/** How a word is shown while memorising. */
enum class WordShow { ALL, FIRST_LETTER, HIDDEN }

/** How a page's lines come in: rising into place, or written again from a blur of ink. */
enum class Entrance { RISE, INK }

/** A word by its place on the page. */
private data class WordAt(val key: AyahKey, val position: Int)

/** Between two lines coming in, and how long each takes. */
private const val LINE_STAGGER = 110
private const val LINE_IN = 420

/**
 * A page of the mushaf as printed: its lines spread over the height it is
 * given, each line's words justified across the width from right to left,
 * at the largest size where the widest line fits. [print] is the page's own
 * font, where each word is one glyph; without it the words are written in
 * the Hafs font the app carries, on the same lines.
 */
@Composable
fun PageView(
    page: MushafPage,
    print: FontFamily?,
    hafs: FontFamily,
    /** Page 1's print font, whose first ayah is the basmala as printed; null until it is here. */
    basmala: FontFamily?,
    /** The basmala as written in this mushaf, in [hafs] when page 1's print font is missing. */
    basmalaText: String,
    surahNames: FontFamily,
    marked: AyahKey?,
    /** The ayah and word heard in the recitation: a light glides from word to word. */
    heard: AyahKey?,
    heardWord: Int?,
    show: (Word) -> WordShow,
    onTap: () -> Unit,
    onLongPress: (Word) -> Unit,
    modifier: Modifier = Modifier,
    /** A tap on a word, before the page's own tap; true when it was used. */
    onWordTap: (Word) -> Boolean = { false },
    /** The word held: its ayah lights up from it, word after word. */
    held: Word? = null,
    /** A word that slipped while reciting from memory. */
    slipped: (Word) -> Boolean = { false },
    /** A surah opens on this page and has not been seen yet: the title's light, the basmala written. */
    opening: Boolean = false,
    /** How the lines come in, if they do. */
    entrance: Entrance? = null,
    /** The page is settled in front of the reader: what it has to play, plays. */
    active: Boolean = false,
    /** Its opening and entrance have played. */
    onShown: () -> Unit = {}
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    val family = print ?: hafs
    val color = LocalContentColor.current
    val scheme = MaterialTheme.colorScheme
    val mark = scheme.primaryContainer.copy(alpha = 0.55f)
    val reduce = reducedMotion()

    // What plays once the page is in front of the reader.
    val gleam = remember(page.number, opening) { Animatable(if (opening) 0f else 1f) }
    val ink = remember(page.number, opening) { Animatable(if (opening) 0f else 1f) }
    val enter = remember(page.number, entrance) { Animatable(if (entrance != null) 0f else 1f) }
    val lineCount = page.lines.size
    val enterTotal = LINE_IN + LINE_STAGGER * (lineCount - 1).coerceAtLeast(0)
    LaunchedEffect(page.number, active, opening, entrance) {
        if (!active || (!opening && entrance == null)) return@LaunchedEffect
        if (reduce) {
            gleam.snapTo(1f); ink.snapTo(1f); enter.snapTo(1f)
        } else coroutineScope {
            if (entrance != null) launch { enter.animateTo(1f, tween(enterTotal, easing = LinearEasing)) }
            if (opening) {
                gleam.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
                ink.animateTo(1f, tween(1100, easing = CubicBezierEasing(0.45f, 0.05f, 0.4f, 1f)))
            }
        }
        onShown()
    }
    // A line's own progress in the entrance.
    fun lineIn(index: Int): Float =
        ((enter.value * enterTotal - index * LINE_STAGGER) / LINE_IN).coerceIn(0f, 1f)

    // The recitation's light: one capsule that glides to the word heard.
    val bounds = remember(page.number) { HashMap<WordAt, Rect>() }
    val column = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val heardAt = if (heard != null && heardWord != null) WordAt(heard, heardWord) else null
    val capsule = remember(page.number) { Animatable(Rect.Zero, Rect.VectorConverter) }
    val capsuleAlpha by animateFloatAsState(if (heardAt != null) 1f else 0f, tween(300), label = "voice")
    LaunchedEffect(heardAt) {
        val target = heardAt?.let { bounds[it] } ?: return@LaunchedEffect
        if (capsule.value == Rect.Zero || reduce) capsule.snapTo(target)
        else capsule.animateTo(target, tween(320, easing = Glide))
    }
    val capsuleColor = scheme.primaryContainer

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures(onTap = { onTap() }) }
    ) {
        val count = PageLayout.lineCount(page.number)
        val lineHeight = maxHeight / count
        // The size where the widest line fills the width, kept within the
        // line's height: measured once per page and font.
        val size: TextUnit = remember(page.number, family, maxWidth, maxHeight) {
            val base = 20.sp
            val style = TextStyle(fontFamily = family, fontSize = base)
            var widest = 1f
            var tallest = 1f
            val lines = page.lines.mapNotNull { line ->
                val words = (line as? PageLine.Words)?.words ?: return@mapNotNull null
                var width = 0f
                for (w in words) {
                    val box = inkBox(measurer.measure(if (print != null) w.glyph else w.text, style))
                    width += box.width
                    tallest = maxOf(tallest, box.height)
                }
                width to words.size
            }
            // Room between the words: as WordsLine leaves it on a centred
            // line, a little less on a justified one.
            val gap = if (PageLayout.centred(page.number)) WORD_GAP else WORD_GAP / 2
            for ((width, n) in lines) widest = maxOf(widest, width + gap * tallest * (n - 1))
            val byWidth = with(density) { maxWidth.toPx() } / widest
            val byHeight = with(density) { lineHeight.toPx() } / tallest * 1.25f
            base * minOf(byWidth, byHeight)
        }
        val style = TextStyle(fontFamily = family, fontSize = size, textAlign = TextAlign.Center, color = color)
        val centred = PageLayout.centred(page.number)
        val padX = with(density) { 5.dp.toPx() }
        val radius = with(density) { 12.dp.toPx() }

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(
                Modifier
                    .fillMaxSize()
                    .onPlaced { column[0] = it }
                    .drawBehind {
                        val r = capsule.value
                        if (capsuleAlpha > 0f && r != Rect.Zero) drawRoundRect(
                            capsuleColor,
                            topLeft = Offset(r.left - padX, r.top),
                            size = Size(r.width + padX * 2, r.height),
                            cornerRadius = CornerRadius(radius),
                            alpha = capsuleAlpha * 0.9f
                        )
                    }
            ) {
                // The first two pages sit in the middle of the height, as printed.
                if (centred) Box(Modifier.weight(1f))
                val shown = if (centred) page.lines else (1..count).map { n -> page.lines.firstOrNull { it.number == n } }
                var lineIndex = 0
                for (line in shown) {
                    val index = if (line != null) lineIndex++ else -1
                    val entering = entrance != null && index >= 0
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(lineHeight)
                            .then(
                                if (!entering) Modifier else Modifier.graphicsLayer {
                                    val p = lineIn(index)
                                    alpha = p
                                    if (entrance == Entrance.RISE) translationY = (1f - p) * 8.dp.toPx()
                                    else renderEffect = if (p < 1f) BlurEffect((1f - p) * 6.dp.toPx() + 0.01f, (1f - p) * 6.dp.toPx() + 0.01f, TileMode.Decal) else null
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        when (line) {
                            is PageLine.Title -> SurahTitle(line.surah, surahNames, size, lineHeight) { gleam.value }
                            is PageLine.Basmala -> Box(Modifier.drawWithContent {
                                // Written from right to left, as the hand writes it.
                                val shownPart = ink.value
                                if (shownPart >= 1f) drawContent()
                                else if (shownPart > 0f) clipRect(left = this.size.width * (1f - shownPart)) { this@drawWithContent.drawContent() }
                            }) { Basmala(basmala, hafs, style, basmalaText) }
                            is PageLine.Words -> WordsLine(
                                words = line.words,
                                justify = !centred,
                                glyphs = print != null,
                                style = style,
                                mark = mark,
                                marked = marked,
                                heard = heard,
                                heardWord = heardWord,
                                held = held,
                                show = show,
                                slipped = slipped,
                                reduce = reduce,
                                onPlacedWord = { w, coords ->
                                    column[0]?.takeIf { it.isAttached && coords.isAttached }?.let { root ->
                                        bounds[WordAt(w.key, w.position)] = root.localBoundingBoxOf(coords)
                                    }
                                },
                                onTap = onTap,
                                onWordTap = onWordTap,
                                onLongPress = onLongPress
                            )
                            null -> Unit
                        }
                    }
                }
                if (centred) Box(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WordsLine(
    words: List<Word>,
    justify: Boolean,
    glyphs: Boolean,
    style: TextStyle,
    mark: Color,
    marked: AyahKey?,
    heard: AyahKey?,
    heardWord: Int?,
    held: Word?,
    show: (Word) -> WordShow,
    slipped: (Word) -> Boolean,
    reduce: Boolean,
    onPlacedWord: (Word, LayoutCoordinates) -> Unit,
    onTap: () -> Unit,
    onWordTap: (Word) -> Boolean,
    onLongPress: (Word) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val frostFill = scheme.surfaceVariant.copy(alpha = 0.62f)
    val frostRim = scheme.outlineVariant.copy(alpha = 0.7f)
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            for (w in words) {
                val hidden = if (w.end) WordShow.ALL else show(w)
                // The recitation: the word heard, and the words of its ayah already said.
                val voiced = heard == w.key && !w.end
                val now = voiced && heardWord == w.position
                val said = voiced && (heardWord == null || w.position < heardWord)
                // Held: the ayah lights up from the word pressed, one word after the other.
                val inHeld = held != null && held.key == w.key
                val hold by animateFloatAsState(
                    if (inHeld) 1f else 0f,
                    tween(260, delayMillis = if (inHeld && !reduce) abs(w.position - held!!.position) * 45 else 0),
                    label = "hold"
                )
                // Memorising: a hidden word lies under frosted glass, which melts when it is said.
                val slip = !w.end && slipped(w)
                val frost by animateFloatAsState(
                    if (hidden == WordShow.HIDDEN) 1f else 0f,
                    if (reduce) snap() else tween(550, delayMillis = if (slip && hidden != WordShow.HIDDEN) 300 else 0),
                    label = "frost"
                )
                val shake = remember(w.key, w.position) { Animatable(0f) }
                LaunchedEffect(slip) {
                    if (slip && !reduce) {
                        for (x in listOf(-4f, 4f, -2f, 0f)) shake.animateTo(x, tween(80))
                    }
                }
                val base = when {
                    slip -> scheme.error
                    now -> scheme.onPrimaryContainer
                    said -> scheme.primary
                    else -> style.color
                }
                val ink = if (hold > 0f && !now && !slip) lerp(base, scheme.primary, hold) else base
                val bgAlpha = maxOf(if (marked == w.key) 1f else 0f, hold)
                Box(
                    Modifier
                        .onGloballyPositioned { onPlacedWord(w, it) }
                        .graphicsLayer {
                            translationX = shake.value * density.density
                            translationY = -2.dp.toPx() * hold
                            scaleX = 1f + 0.03f * hold
                            scaleY = 1f + 0.03f * hold
                        }
                        .then(if (bgAlpha > 0f) Modifier.background(mark.copy(alpha = mark.alpha * bgAlpha), RoundedCornerShape(6.dp)) else Modifier)
                        .pointerInput(w) { detectTapGestures(onTap = { if (!onWordTap(w)) onTap() }, onLongPress = { onLongPress(w) }) }
                        .drawWithContent {
                            drawContent()
                            if (frost > 0.01f) {
                                val lift = (1f - frost) * 6.dp.toPx()
                                val grow = 1f + 0.06f * (1f - frost)
                                // A little narrower than the word, so two pieces of glass never touch.
                                val inset = -1.5.dp.toPx()
                                val w0 = size.width * grow + inset * 2
                                val h0 = size.height * 0.84f * grow
                                val topLeft = Offset((size.width - w0) / 2f, (size.height - h0) / 2f - lift)
                                val corner = CornerRadius(12.dp.toPx())
                                drawRoundRect(frostFill, topLeft, Size(w0, h0), corner, alpha = frost)
                                drawRoundRect(frostRim, topLeft, Size(w0, h0), corner, alpha = frost, style = Stroke(1.dp.toPx()))
                            }
                        }
                ) {
                    val text = when {
                        hidden == WordShow.FIRST_LETTER && !glyphs -> w.text.take(firstLetterLength(w.text))
                        glyphs -> w.glyph
                        else -> w.text
                    }
                    WordInk(
                        text,
                        style,
                        color = ink,
                        alpha = when (hidden) {
                            WordShow.ALL -> 1f - 0.65f * frost
                            WordShow.FIRST_LETTER -> if (glyphs) 0.12f else 1f
                            WordShow.HIDDEN -> 0.35f
                        },
                        blur = frost
                    )
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val height = placeables.maxOfOrNull { it.height } ?: 0
        val total = placeables.sumOf { it.width }
        val n = placeables.size
        // Justified as printed; a line much shorter than the width (the first
        // pages, the end of a few surahs) is centred instead.
        val spread = justify && n > 1 && total > width * 0.72f
        val gap = if (spread) ((width - total).toFloat() / (n - 1)).coerceAtLeast(0f) else (height * WORD_GAP)
        val used = total + gap * (n - 1)
        layout(width, height) {
            // Right to left: the first word at the right edge.
            var x = if (spread) width.toFloat() else (width + used) / 2f
            for (p in placeables) {
                x -= p.width
                p.place(x.toInt(), (height - p.height) / 2)
                x -= gap
            }
        }
    }
}

/**
 * A word drawn by its ink, not by the room its font claims: the print
 * fonts' glyphs reach well past their advance, and placed by advance alone
 * the words of the first pages overlap.
 */
@Composable
private fun WordInk(text: String, style: TextStyle, color: Color, alpha: Float, blur: Float = 0f) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val layout = remember(text, style) { measurer.measure(text, style, softWrap = false, maxLines = 1) }
    val box = remember(layout) { inkBox(layout) }
    val density = LocalDensity.current
    Canvas(
        Modifier
            .size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })
            .then(
                if (blur <= 0.01f) Modifier else Modifier.graphicsLayer {
                    val r = blur * 9.dp.toPx()
                    renderEffect = BlurEffect(r, r, TileMode.Decal)
                }
            )
    ) {
        if (alpha > 0f) drawText(layout, color = color, topLeft = Offset(-box.left, -box.top), alpha = alpha)
    }
}

/** The union of a word's advance and its ink: what it really covers. */
internal fun inkBox(layout: TextLayoutResult): Rect {
    val ink = layout.getPathForRange(0, layout.layoutInput.text.length).getBounds()
    return Rect(
        left = minOf(0f, ink.left),
        top = minOf(0f, ink.top),
        right = maxOf(layout.size.width.toFloat(), ink.right),
        bottom = maxOf(layout.size.height.toFloat(), ink.bottom)
    )
}

/** The first letter with its marks, for the memorising aid. */
private fun firstLetterLength(word: String): Int {
    var i = 1
    while (i < word.length && (word[i] in 'ً'..'ٟ' || word[i] == 'ٰ' || word[i] in 'ۖ'..'ۭ')) i++
    return i
}

/**
 * A surah's title as the mushaf prints it: its name in thuluth (the surah
 * names font of Quran.com, one ligature per surah) on a pane of glass.
 */
@Composable
private fun SurahTitle(surah: Int, names: FontFamily, size: TextUnit, height: Dp, gleam: () -> Float) {
    ZoneSurface(
        shape = RoundedCornerShape(50),
        accent = true,
        modifier = Modifier.fillMaxWidth(0.86f).height(height * 0.86f)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(50))
                .drawWithContent {
                    drawContent()
                    // A light crossing the title once, as on the app's icon.
                    val g = gleam()
                    if (g > 0f && g < 1f) {
                        val band = this.size.width * 0.4f
                        val x = -band + (this.size.width + band * 2) * g
                        drawRect(
                            Brush.horizontalGradient(
                                0f to Color.Transparent, 0.5f to Color.White.copy(alpha = 0.6f), 1f to Color.Transparent,
                                startX = x - band / 2, endX = x + band / 2
                            )
                        )
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "%03d".format(surah),
                style = TextStyle(fontFamily = names, fontSize = size * 1.7f, textAlign = TextAlign.Center),
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}

/**
 * The basmala above a surah: the first ayah of al-Fatihah in page 1's own
 * font, so it is the printed one; in the Hafs font until that font is here.
 */
@Composable
private fun Basmala(print: FontFamily?, hafs: FontFamily, style: TextStyle, text: String) {
    if (print == null) {
        // The words set apart and a little larger than the text, as the print sets its basmala.
        val wordStyle = style.copy(fontFamily = hafs, fontSize = style.fontSize * 1.08f)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            for (w in text.split(' ').filter { it.isNotEmpty() }) Text(w, style = wordStyle, maxLines = 1, softWrap = false)
        }
        return
    }
    val glyphStyle = style.copy(fontFamily = print, fontSize = style.fontSize * 1.25f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (g in BASMALA_GLYPHS) WordInk(g, glyphStyle, glyphStyle.color, 1f)
    }
}

/** The room between two words on a line that is not justified, in word heights. */
private const val WORD_GAP = 0.18f

/** The four words of 1:1 in page 1's print font. */
private val BASMALA_GLYPHS = listOf("ﱁ", "ﱂ", "ﱃ", "ﱄ")
