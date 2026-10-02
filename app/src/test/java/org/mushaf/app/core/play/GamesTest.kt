package org.mushaf.app.core.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mushaf.app.core.quran.Arabic
import org.mushaf.app.core.quran.AyahKey

class GamesTest {

    private val ikhlas1 = listOf("قُلۡ", "هُوَ", "ٱللَّهُ", "أَحَدٌ")

    @Test
    fun aPuzzleHasTheSameWordsInAnotherOrder() {
        repeat(20) { seed ->
            val p = Games.puzzle(AyahKey(112, 1), ikhlas1, Random(seed))
            assertEquals(ikhlas1.sorted(), p.shuffled.map { it.text }.sorted())
            assertNotEquals(p.answer, p.shuffled)
        }
    }

    @Test
    fun theMissingWordIsAmongTheChoicesOnceAndOnlyOnce() {
        val pool = listOf("ٱلصَّمَدُ", "يَلِدۡ", "يُولَدۡ", "كُفُوًا", "أَحَدٌ", "ٱللَّهُ")
        repeat(20) { seed ->
            val m = Games.missing(AyahKey(112, 1), ikhlas1, pool, Arabic::normalize, Random(seed))
            val answer = Arabic.normalize(m.words[m.hole])
            assertEquals(1, m.choices.count { Arabic.normalize(it) == answer })
            assertEquals(3, m.choices.size)
        }
    }

    @Test
    fun theAyahHeardIsOneOfTheChoices() {
        val ayat = (1..4).map { AyahKey(112, it) }
        repeat(20) { seed ->
            val w = Games.which(ayat, Random(seed))
            assertTrue(w.answer in w.choices)
            assertEquals(3, w.choices.distinct().size)
        }
    }
}
