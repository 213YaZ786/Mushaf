package org.mushaf.app.data.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PackTest {

    @Test
    fun oneSurahReadsBackFromItsId() {
        val p = Pack.parse(Pack.Surah(7, 24).id)
        assertTrue(p is Pack.Surah)
        p as Pack.Surah
        assertEquals(7, p.reciter)
        assertEquals(24, p.surah)
        assertEquals(1, p.total)
    }

    @Test
    fun aWrongIdIsNoPack() {
        assertNull(Pack.parse("surah-7"))
        assertNull(Pack.parse("surah-7-115"))
        assertNull(Pack.parse("surah-x-2"))
    }
}
