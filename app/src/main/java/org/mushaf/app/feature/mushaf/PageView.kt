package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.Canvas
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
    surahNames: FontFamily,
    marked: AyahKey?,
    /** The ayah and word heard in the recitation: the word takes the accent. */
    heard: AyahKey?,
    heardWord: Int?,
    show: (Word) -> WordShow,
    onTap: () -> Unit,
    onLongPress: (Word) -> Unit,
    modifier: Modifier = Modifier,
    /** A tap on a word, before the page's own tap; true when it was used. */
    onWordTap: (Word) -> Boolean = { false }
) {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    val family = print ?: hafs
    val color = LocalContentColor.current
    val mark = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    val voice = MaterialTheme.colorScheme.primary

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

        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            Column(Modifier.fillMaxSize()) {
                // The first two pages sit in the middle of the height, as printed.
                if (centred) Box(Modifier.weight(1f))
                val shown = if (centred) page.lines else (1..count).map { n -> page.lines.firstOrNull { it.number == n } }
                for (line in shown) {
                    Box(Modifier.fillMaxWidth().height(lineHeight), contentAlignment = Alignment.Center) {
                        when (line) {
                            is PageLine.Title -> SurahTitle(line.surah, surahNames, size, lineHeight)
                            is PageLine.Basmala -> Basmala(basmala, hafs, style)
                            is PageLine.Words -> WordsLine(
                                words = line.words,
                                justify = !centred,
                                glyphs = print != null,
                                style = style,
                                mark = mark,
                                voice = voice,
                                marked = marked,
                                heard = heard,
                                heardWord = heardWord,
                                show = show,
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
    voice: Color,
    marked: AyahKey?,
    heard: AyahKey?,
    heardWord: Int?,
    show: (Word) -> WordShow,
    onTap: () -> Unit,
    onWordTap: (Word) -> Boolean,
    onLongPress: (Word) -> Unit
) {
    Layout(
        modifier = Modifier.fillMaxWidth(),
        content = {
            for (w in words) {
                val hidden = if (w.end) WordShow.ALL else show(w)
                val isPlaying = heard == w.key && heardWord == w.position
                Box(
                    Modifier
                        .then(if (marked == w.key) Modifier.background(mark, RoundedCornerShape(6.dp)) else Modifier)
                        .pointerInput(w) { detectTapGestures(onTap = { if (!onWordTap(w)) onTap() }, onLongPress = { onLongPress(w) }) }
                ) {
                    val text = when {
                        hidden == WordShow.FIRST_LETTER && !glyphs -> w.text.take(firstLetterLength(w.text))
                        glyphs -> w.glyph
                        else -> w.text
                    }
                    WordInk(
                        text,
                        style,
                        color = if (isPlaying) voice else style.color,
                        alpha = when (hidden) {
                            WordShow.ALL -> 1f
                            WordShow.FIRST_LETTER -> if (glyphs) 0.12f else 1f
                            WordShow.HIDDEN -> 0f
                        }
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
private fun WordInk(text: String, style: TextStyle, color: Color, alpha: Float) {
    val measurer = rememberTextMeasurer(cacheSize = 8)
    val layout = remember(text, style) { measurer.measure(text, style, softWrap = false, maxLines = 1) }
    val box = remember(layout) { inkBox(layout) }
    val density = LocalDensity.current
    Canvas(Modifier.size(with(density) { box.width.toDp() }, with(density) { box.height.toDp() })) {
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
private fun SurahTitle(surah: Int, names: FontFamily, size: TextUnit, height: Dp) {
    ZoneSurface(
        shape = RoundedCornerShape(50),
        accent = true,
        modifier = Modifier.fillMaxWidth(0.86f).height(height * 0.86f)
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
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
private fun Basmala(print: FontFamily?, hafs: FontFamily, style: TextStyle) {
    if (print == null) {
        Text(BASMALA, style = style.copy(fontFamily = hafs, fontSize = style.fontSize * 0.92f), maxLines = 1)
        return
    }
    val glyphStyle = style.copy(fontFamily = print, fontSize = style.fontSize * 1.25f)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        for (g in BASMALA_GLYPHS) WordInk(g, glyphStyle, glyphStyle.color, 1f)
    }
}

/** The room between two words on a line that is not justified, in word heights. */
private const val WORD_GAP = 0.18f

private const val BASMALA ="بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"

/** The four words of 1:1 in page 1's print font. */
private val BASMALA_GLYPHS = listOf("ﱁ", "ﱂ", "ﱃ", "ﱄ")
