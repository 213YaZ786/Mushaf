package org.mushaf.app.core.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class ArabicFindTest {

    @Test
    fun aWordTypedWithoutVowelsIsFoundInTheVowelledText() {
        val text = "ٱللَّهُ لَآ إِلَٰهَ إِلَّا هُوَ ٱلۡحَيُّ ٱلۡقَيُّومُۚ"
        val hits = Arabic.find(text, "الحي")
        assertEquals(1, hits.size)
        // The written word with its vowels, up to the space after it.
        assertEquals("ٱلۡحَيُّ", text.substring(hits[0].first, hits[0].last + 1))
    }

    @Test
    fun everyPlaceIsFound() {
        val text = "إِلَٰهَ إِلَّا"
        assertEquals(2, Arabic.find(text, "ال").size)
    }

    @Test
    fun nothingTypedFindsNothing() {
        assertEquals(0, Arabic.find("بِسۡمِ", "").size)
    }
}
