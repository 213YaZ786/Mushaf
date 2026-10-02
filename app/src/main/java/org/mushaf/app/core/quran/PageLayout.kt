package org.mushaf.app.core.quran

/**
 * Builds a page's lines from its words. The data gives every word its line;
 * the lines without words are a surah's title and its basmala, printed just
 * above its first ayah. Al-Fatihah counts the basmala as its first ayah and
 * at-Tawbah has none, so both take a title only.
 */
object PageLayout {

    fun page(number: Int, words: List<Word>): MushafPage {
        val byLine = words.groupBy { it.line }
        val extra = mutableMapOf<Int, PageLine>()
        for (first in words.filter { it.key.ayah == 1 && it.position == 1 && !it.end }) {
            val surah = first.key.surah
            val titleOnly = surah == 1 || surah == 9
            val title = first.line - if (titleOnly) 1 else 2
            if (title >= 1) extra[title] = PageLine.Title(title, surah)
            if (!titleOnly && first.line - 1 >= 1) extra[first.line - 1] = PageLine.Basmala(first.line - 1)
        }
        val last = maxOf(byLine.keys.maxOrNull() ?: 0, extra.keys.maxOrNull() ?: 0)
        val lines = (1..last).mapNotNull { n ->
            byLine[n]?.let { PageLine.Words(n, it) } ?: extra[n]
        }
        return MushafPage(number, lines)
    }

    /** The first two pages are printed in a frame, their lines centred. */
    fun centred(page: Int): Boolean = page <= 2

    /** Lines a page is laid out on: 15, or 8 for the first two. */
    fun lineCount(page: Int): Int = if (page <= 2) 8 else 15

    /** One line of a page file: line, s:a:w, kind, text, glyph, plain, meaning, transliteration. */
    fun parseWord(row: String): Word? {
        val f = row.split('\t')
        if (f.size < 5) return null
        val loc = f[1].split(':')
        if (loc.size < 3) return null
        return Word(
            line = f[0].toIntOrNull() ?: return null,
            key = AyahKey(loc[0].toIntOrNull() ?: return null, loc[1].toIntOrNull() ?: return null),
            position = loc[2].toIntOrNull() ?: return null,
            end = f[2] == "e",
            text = f[3],
            glyph = f[4],
            plain = f.getOrElse(5) { "" },
            meaning = f.getOrElse(6) { "" },
            transliteration = f.getOrElse(7) { "" }
        )
    }
}
