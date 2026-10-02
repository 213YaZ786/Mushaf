package org.mushaf.app.core.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArabicTest {

    @Test
    fun printedAndTypedFormsMatch() {
        // The QPC Hafs form of "Allah" and "al-Rahman" against plain typing.
        assertEquals(Arabic.normalize("الرحمن"), Arabic.normalize("ٱلرَّحۡمَٰنِ"))
        assertEquals(Arabic.normalize("الله"), Arabic.normalize("ٱللَّهِ"))
        assertEquals("اهدنا", Arabic.normalize("ٱهۡدِنَا"))
    }

    @Test
    fun lettersWithSeveralFormsBecomeOne() {
        assertEquals(Arabic.normalize("هدى"), Arabic.normalize("هدي"))
        assertEquals(Arabic.normalize("رحمة"), Arabic.normalize("رحمه"))
        assertEquals(Arabic.normalize("إيمان"), Arabic.normalize("ايمان"))
    }

    @Test
    fun wordsAreSplitOnSpaces() {
        assertEquals(listOf("بسم", "الله"), Arabic.words("  بِسۡمِ   ٱللَّهِ "))
    }

    @Test
    fun arabicIsTold() {
        assertTrue(Arabic.isArabic("رب"))
        assertFalse(Arabic.isArabic("mercy"))
    }

    @Test
    fun numbersInMushafDigits() {
        assertEquals("٢٥٥", Arabic.digits(255))
    }
}
