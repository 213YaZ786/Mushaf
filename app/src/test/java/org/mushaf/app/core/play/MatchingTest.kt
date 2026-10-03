package org.mushaf.app.core.play

import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mushaf.app.core.quran.Arabic

class MatchingTest {

    // Al-Ikhlas, word by word, as the data gives it.
    private val ikhlas = listOf(
        Meaning("قُلۡ", "Say"), Meaning("هُوَ", "He"), Meaning("ٱللَّهُ", "(is) Allah"), Meaning("أَحَدٌ", "the One"),
        Meaning("ٱللَّهُ", "Allah"), Meaning("ٱلصَّمَدُ", "the Eternal, the Absolute"),
        Meaning("لَمۡ", "Not"), Meaning("يَلِدۡ", "He begets"), Meaning("وَلَمۡ", "and not"), Meaning("يُولَدۡ", "He is begotten"),
        Meaning("وَلَمۡ", "And not"), Meaning("يَكُن", "is"), Meaning("لَّهُۥ", "for Him"), Meaning("كُفُوًا", "equivalent"), Meaning("أَحَدُۢ", "any [one]")
    )

    @Test
    fun aWordWithTwoMeaningsIsLeftOut() {
        // الله is "(is) Allah" and "Allah": either could be its match.
        val rounds = Games.matching(ikhlas, Arabic::normalize, Random(1), size = 4, rounds = 5)
        assertTrue(rounds.flatten().none { Arabic.normalize(it.word) == "الله" })
    }

    @Test
    fun noWordAndNoMeaningComesTwice() {
        repeat(20) { seed ->
            val all = Games.matching(ikhlas, Arabic::normalize, Random(seed), size = 4, rounds = 5).flatten()
            assertEquals(all.size, all.map { Arabic.normalize(it.word) }.distinct().size)
            assertEquals(all.size, all.map { it.meaning.lowercase() }.distinct().size)
        }
    }

    @Test
    fun onlyFullRounds() {
        val rounds = Games.matching(ikhlas, Arabic::normalize, Random(3), size = 4, rounds = 5)
        assertTrue(rounds.all { it.size == 4 })
    }
}
