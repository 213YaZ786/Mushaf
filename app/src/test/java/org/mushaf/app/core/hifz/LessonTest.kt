package org.mushaf.app.core.hifz

import java.util.BitSet
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mushaf.app.core.quran.AyahKey

class LessonTest {

    private val ayat = listOf(AyahKey(112, 1), AyahKey(112, 2), AyahKey(112, 3), AyahKey(112, 4), AyahKey(113, 1), AyahKey(113, 2), AyahKey(114, 1))

    @Test
    fun fromAnNasStartsWithTheLastSurah() {
        val order = Lesson.ordered(ayat, Order.FROM_AN_NAS)
        assertEquals(AyahKey(114, 1), order.first())
        assertEquals(AyahKey(113, 1), order[1])
        assertEquals(AyahKey(112, 4), order.last())
    }

    @Test
    fun aLessonStopsAtTheLinesAskedAndAtTheSurahsEnd() {
        val order = Lesson.ordered(ayat, Order.FROM_AL_FATIHAH)
        val known = setOf(AyahKey(112, 1))
        val lesson = Lesson.next(order, { it in known }, { 1 }, lines = 2)
        assertEquals(listOf(AyahKey(112, 2), AyahKey(112, 3)), lesson)
        val rest = Lesson.next(order, { it in known || it in lesson }, { 1 }, lines = 10)
        assertEquals(listOf(AyahKey(112, 4)), rest)
    }

    @Test
    fun aLongAyahIsALessonOnItsOwn() {
        assertEquals(listOf(AyahKey(112, 1)), Lesson.next(ayat, { false }, { 20 }, lines = 7))
    }

    @Test
    fun runsRoundTrip() {
        val bits = BitSet().apply { set(0, 7); set(9); set(11, 21) }
        assertEquals("0-6,9,11-20", Runs.encode(bits))
        assertEquals(bits, Runs.decode("0-6,9,11-20"))
        assertEquals(BitSet(), Runs.decode(""))
    }
}
