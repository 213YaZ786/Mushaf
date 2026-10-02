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
    fun aTitleWithNoRoomOnItsPageClosesThePageBefore() {
        // Yunus opens page 208 on line 2: its title is printed on line 15 of page 207.
        val page = PageLayout.page(207, listOf(word(14, 9, 129, 1)), next = word(2, 10, 1, 1))
        assertEquals(PageLine.Title(15, 10), page.lines.last())
        // A surah whose first ayah is on line 1: title and basmala both go before.
        val both = PageLayout.page(300, listOf(word(13, 20, 135, 1)), next = word(1, 21, 1, 1))
        assertEquals(listOf(PageLine.Title(14, 21), PageLine.Basmala(15)), both.lines.takeLast(2))
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
