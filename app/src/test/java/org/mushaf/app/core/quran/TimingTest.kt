package org.mushaf.app.core.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mushaf.app.core.audio.AyahTime
import org.mushaf.app.core.audio.SurahAudio
import org.mushaf.app.core.audio.Timing
import org.mushaf.app.core.audio.WordTime

class TimingTest {

    private val audio = SurahAudio(
        7, 1, "x",
        listOf(
            AyahTime(1, 1, 0, 6090, listOf(WordTime(1, 0, 580), WordTime(2, 580, 1409))),
            AyahTime(1, 2, 6090, 11680)
        )
    )

    @Test
    fun theAyahAndWordHeardAreFound() {
        assertEquals(1, Timing.at(audio, 600)!!.ayah.ayah)
        assertEquals(2, Timing.at(audio, 600)!!.word)
        assertEquals(2, Timing.at(audio, 7000)!!.ayah.ayah)
        assertEquals(null, Timing.at(audio, 7000)!!.word)
        assertEquals(2, Timing.at(audio, 99_000)!!.ayah.ayah)
    }

    @Test
    fun segmentsFromAnotherOriginAreDropped() {
        // Minshawi kids repeat, 112:2: the ayah starts at 5475 but its segments start at 0.
        assertTrue(Timing.words(listOf(listOf(1L, 0L, 6673L), listOf(2L, 6673L, 7734L)), 5475, 10118).isEmpty())
        assertEquals(2, Timing.words(listOf(listOf(1L, 6025L, 7025L), listOf(2L, 7025L, 7885L), listOf(3L)), 6090, 11680).size)
    }
}
