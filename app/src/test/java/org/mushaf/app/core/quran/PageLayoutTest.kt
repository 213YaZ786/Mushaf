package org.mushaf.app.core.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageLayoutTest {

    private fun word(line: Int, s: Int, a: Int, p: Int, end: Boolean = false) =
        Word(line, AyahKey(s, a), p, end, "w", "g", "w", "", "")

    @Test
    fun aSurahStartingMidPageGetsItsTitleAndBasmala() {
        val words = listOf(word(1, 2, 286, 1), word(1, 2, 286, 2, end = true), word(4, 3, 1, 1), word(4, 3, 1, 2, end = true))
        val page = PageLayout.page(50, words)
        assertEquals(listOf(1, 2, 3, 4), page.lines.map { it.number })
        assertTrue(page.lines[1] is PageLine.Title)
        assertEquals(3, (page.lines[1] as PageLine.Title).surah)
        assertTrue(page.lines[2] is PageLine.Basmala)
    }

    @Test
    fun atTawbahAndAlFatihahHaveATitleOnly() {
        val tawbah = PageLayout.page(187, listOf(word(2, 9, 1, 1)))
        assertEquals(listOf(1, 2), tawbah.lines.map { it.number })
        assertTrue(tawbah.lines[0] is PageLine.Title)
        val fatihah = PageLayout.page(1, listOf(word(2, 1, 1, 1)))
        assertTrue(fatihah.lines[0] is PageLine.Title)
        assertEquals(2, fatihah.lines.size)
    }

    @Test
    fun aRowOfThePageFileIsRead() {
        val w = PageLayout.parseWord("3\t2:2:1\tw\tذَٰلِكَ\tﱃ\tذالك\tThat\tdhālika")!!
        assertEquals(AyahKey(2, 2), w.key)
        assertEquals(1, w.position)
        assertEquals("That", w.meaning)
        val end = PageLayout.parseWord("3\t2:1:2\te\t١\tﱂ\t\t\t")!!
        assertTrue(end.end)
    }

    @Test
    fun keysParse() {
        assertEquals(AyahKey(2, 255), AyahKey.parse("2:255"))
        assertEquals(AyahKey(2, 255), AyahKey.parse("2:255:3"))
        assertEquals(null, AyahKey.parse("115:1"))
        assertTrue(AyahKey(2, 10) < AyahKey(3, 1))
    }
}
