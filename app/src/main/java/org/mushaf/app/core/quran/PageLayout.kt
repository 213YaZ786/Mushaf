package org.mushaf.app.core.quran

/**
 * Builds a page's lines from its words. The data gives every word its line;
 * the lines without words are a surah's title and its basmala, printed just
 * above its first ayah. Al-Fatihah counts the basmala as its first ayah and
 * at-Tawbah has none, so both take a title only.
 */
object PageLayout {

    /**
     * [next] is the first word of the next page: when it opens a surah whose
     * title has no room above it there, the title (and the basmala) close
     * this page instead, on its last lines, as printed (Yunus, page 207).
     */
    fun page(number: Int, words: List<Word>, next: Word? = null, explicit: Map<Int, PageLine> = emptyMap()): MushafPage {
        val byLine = words.groupBy { it.line }
        // A mushaf whose file places its titles and basmalas itself (Warsh) needs no guessing.
        if (explicit.isNotEmpty()) {
            val last = maxOf(byLine.keys.maxOrNull() ?: 0, explicit.keys.maxOrNull() ?: 0)
            return MushafPage(number, (1..last).mapNotNull { n -> explicit[n] ?: byLine[n]?.let { PageLine.Words(n, it) } })
        }
        val extra = mutableMapOf<Int, PageLine>()
        for (first in words.filter { it.key.ayah == 1 && it.position == 1 && !it.end }) {
            val surah = first.key.surah
            val titleOnly = surah == 1 || surah == 9
            val title = first.line - if (titleOnly) 1 else 2
            if (title >= 1) extra[title] = PageLine.Title(title, surah)
            if (!titleOnly && first.line - 1 >= 1) extra[first.line - 1] = PageLine.Basmala(first.line - 1)
        }
        if (next != null && next.key.ayah == 1 && next.position == 1 && !next.end) {
            val surah = next.key.surah
            val titleOnly = surah == 1 || surah == 9
            val pushed = buildList {
                if (next.line - (if (titleOnly) 1 else 2) < 1) add(PageLine.Title(0, surah))
                if (!titleOnly && next.line - 1 < 1) add(PageLine.Basmala(0))
            }
            val count = lineCount(number)
            pushed.forEachIndexed { i, line ->
                val n = count - pushed.size + 1 + i
                if (n !in byLine) extra[n] = when (line) {
                    is PageLine.Title -> line.copy(number = n)
                    else -> PageLine.Basmala(n)
                }
            }
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

    /**
     * A title or basmala line of a page file (kind t or b), as the Warsh
     * files write them: line, s:0:0, kind.
     */
    fun parseLine(row: String): PageLine? {
        val f = row.split('\t')
        if (f.size < 3 || (f[2] != "t" && f[2] != "b")) return null
        val n = f[0].toIntOrNull() ?: return null
        val s = f[1].substringBefore(':').toIntOrNull() ?: return null
        return if (f[2] == "t") PageLine.Title(n, s) else PageLine.Basmala(n)
    }

    /** One line of a page file: line, s:a:w, kind, text, glyph, plain, meaning, transliteration. */
    fun parseWord(row: String): Word? {
        val f = row.split('\t')
        if (f.size < 5 || f[2] == "t" || f[2] == "b") return null
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
