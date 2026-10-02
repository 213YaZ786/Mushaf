package org.mushaf.app.core.stt

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchTest {

    // Al-Ikhlas 112:1-2 as printed (QPC Hafs).
    private val text = listOf("قُلۡ", "هُوَ", "ٱللَّهُ", "أَحَدٌ", "ٱللَّهُ", "ٱلصَّمَدُ")

    @Test
    fun aRecitationHeardRightAdvancesWordByWord() {
        val r = Match.follow(text, 0, listOf("قل", "هو", "الله", "احد"))
        assertEquals(4, r.next)
        assertEquals(listOf(Heard.RIGHT, Heard.RIGHT, Heard.RIGHT, Heard.RIGHT), (0..3).map { r.marks[it] })
    }

    @Test
    fun aWordPassedOverIsMarkedSkipped() {
        val r = Match.follow(text, 0, listOf("قل", "الله", "احد"))
        assertEquals(Heard.SKIPPED, r.marks[1])
        assertEquals(4, r.next)
    }

    @Test
    fun noiseAndRepeatsAreLetGo() {
        val r = Match.follow(text, 0, listOf("قل", "قل", "مممم", "هو"))
        assertEquals(2, r.next)
        assertEquals(null, r.marks[2])
    }

    @Test
    fun aWordNearlyRightIsClose() {
        // The recogniser writes the alef that the mushaf writes small.
        val r = Match.follow(listOf("ٱلصَّمَدُ"), 0, listOf("الصمدد"))
        assertEquals(Heard.CLOSE, r.marks[0])
    }

    @Test
    fun followingGoesOnFromWhereItStopped() {
        val r = Match.follow(text, 4, listOf("الله", "الصمد"))
        assertEquals(6, r.next)
    }
}
