package org.mushaf.app.core.stt

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mushaf.app.core.quran.Arabic
import org.mushaf.app.core.quran.AyahKey

/**
 * Finding an ayah in the whole Quran from what the speech model really
 * wrote, hearing seven seconds of a recitation (whisper.cpp with Tarteel's
 * model, on the computer, 2026-10-03).
 */
class FindQuranTest {

    private val ayat = File("src/main/assets/quran/ayat.txt").readLines().filter { it.isNotBlank() }.map { row ->
        val f = row.split('\t')
        AyahKey.parse(f[0])!! to Arabic.words(f[4])
    }

    @Test
    fun theOpeningOfAlMulk() {
        assertEquals(AyahKey(67, 1), Find.rank("تَبَارَكَ الَّذِي بِيَدِهِ الْمُلْكُ وَهُوَ عَلَى كُلِّ شَيْءٍ", ayat).first().key)
    }

    @Test
    fun wordsThatOpenTwoAyatBringBoth() {
        // Ayat al-Kursi opens as Al Imran's second ayah does.
        val keys = Find.rank("اللَّهُ لَا إِلَهَ إِلَّا هُوَ الْحَيُّ الْقَيُّومُ", ayat).take(3).map { it.key }
        assertTrue(AyahKey(2, 255) in keys)
        assertTrue(AyahKey(3, 2) in keys)
    }

    @Test
    fun aShortSurah() {
        assertEquals(AyahKey(113, 1), Find.rank("قل اعوذ برب الفلق", ayat).first().key)
    }
}
