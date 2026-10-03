package org.mushaf.app.feature.index

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import org.mushaf.app.core.quran.Arabic

/** Where the words searched for stand in a result's text: Arabic as typed, any other language word by word. */
internal fun searchHits(text: String, query: String, arabic: Boolean): List<IntRange> {
    if (arabic) return Arabic.find(text, query)
    val lower = text.lowercase()
    return query.lowercase().split(Regex("\\s+")).filter { it.length > 1 }.flatMap { w ->
        generateSequence(lower.indexOf(w).takeIf { it >= 0 }) { i -> lower.indexOf(w, i + w.length).takeIf { it >= 0 } }
            .map { it until it + w.length }.toList()
    }.sortedBy { it.first }
}

/**
 * The result's text with what was searched for lit in [color], starting a
 * little before the first hit when that lies far in, so it is never cut off.
 */
internal fun highlighted(text: String, hits: List<IntRange>, color: Color): AnnotatedString {
    val first = hits.firstOrNull()?.first ?: 0
    // Far into a long text: start at a word a little before the hit.
    val start = if (first > 70) (text.lastIndexOf(' ', first - 30).takeIf { it > 0 } ?: 0) else 0
    return buildAnnotatedString {
        if (start > 0) append("… ")
        val shift = length - start
        append(text.substring(start))
        for (h in hits) {
            if (h.first < start) continue
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), h.first + shift, h.last + 1 + shift)
        }
    }
}
