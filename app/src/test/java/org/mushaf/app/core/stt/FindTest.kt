package org.mushaf.app.core.stt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mushaf.app.core.quran.AyahKey

class FindTest {

    private val ayat = listOf(
        AyahKey(112, 1) to listOf("قُلۡ", "هُوَ", "ٱللَّهُ", "أَحَدٌ"),
        AyahKey(112, 2) to listOf("ٱللَّهُ", "ٱلصَّمَدُ"),
        AyahKey(112, 3) to listOf("لَمۡ", "يَلِدۡ", "وَلَمۡ", "يُولَدۡ"),
        AyahKey(112, 4) to listOf("وَلَمۡ", "يَكُن", "لَّهُۥ", "كُفُوًا", "أَحَدُۢ"),
        AyahKey(113, 1) to listOf("قُلۡ", "أَعُوذُ", "بِرَبِّ", "ٱلۡفَلَقِ")
    )

    @Test
    fun theAyahHeardComesFirst() {
        assertEquals(AyahKey(112, 3), Find.rank("لم يلد ولم يولد", ayat).first().key)
    }

    @Test
    fun aPassageOverAnAyahsEndIsFoundWhereItStarts() {
        assertEquals(AyahKey(112, 1), Find.rank("الله احد الله الصمد", ayat).first().key)
    }

    @Test
    fun aWordHeardALittleWrongStillCounts() {
        // كفوا heard as كفؤا, as the recogniser may write it.
        assertEquals(AyahKey(112, 4), Find.rank("ولم يكن له كفؤا احد", ayat).first().key)
    }

    @Test
    fun wordsInTheWrongOrderScoreLess() {
        val hits = Find.rank("قل اعوذ برب الفلق", ayat)
        assertEquals(AyahKey(113, 1), hits.first().key)
        assertTrue(hits.none { it.key == AyahKey(112, 1) && it.score > hits.first().score })
    }

    @Test
    fun oneWordIsNotEnough() {
        assertTrue(Find.rank("الله", ayat).isEmpty())
    }
}
