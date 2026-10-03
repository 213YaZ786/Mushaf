package org.mushaf.app.data.quran

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mushaf.app.core.common.latinDigits

class PageQueryTest {

    @Test
    fun digitsFromAnyKeyboardReadAsLatin() {
        assertEquals("2:255", latinDigits("٢:٢٥٥"))
        assertEquals("2:255", latinDigits("۲:۲۵۵"))
        assertEquals("50", latinDigits("৫০"))
        assertEquals("page 50", latinDigits("page 50"))
    }

    @Test
    fun aPageIsNamedInTheReadersOwnWord() {
        val words = setOf("seite", "صفحة", "صفحهٔ", "страница")
        assertEquals(50, pageOf("50", words))
        assertEquals(50, pageOf("page 50", words))
        assertEquals(50, pageOf("p. 50", words))
        assertEquals(50, pageOf("Seite 50", words))
        assertEquals(50, pageOf("صفحة 50", words))
        assertEquals(50, pageOf("صفحهٔ 50", words))
        assertEquals(50, pageOf("Страница 50", words))
        assertNull(pageOf("juz 3", words))
        assertNull(pageOf("605", words))
        assertNull(pageOf("0", words))
    }
}
