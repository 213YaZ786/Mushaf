package org.mushaf.app.data.khatmah

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KhatmahTest {

    @Test
    fun thirtyDaysAreAJuzOrSoADay() {
        val p = KhatmahPlan(start = 100, days = 30)
        // 604 pages over 30 days: 21 pages on day 1 (rounded up), all 604 by day 30.
        assertEquals(21, p.mark(1))
        assertEquals(604, p.mark(30))
        val today = p.portion(100)
        assertEquals(1, today.day)
        assertEquals(1, today.fromPage)
        assertEquals(21, today.toPage)
        assertFalse(today.done)
    }

    @Test
    fun aDayBehindAddsItsPagesToToday() {
        val p = KhatmahPlan(start = 100, days = 30, reached = 10)
        val today = p.portion(101) // day 2, only 10 pages read
        assertEquals(2, today.day)
        assertEquals(11, today.fromPage)
        assertEquals(p.mark(2), today.toPage)
    }

    @Test
    fun aheadOfTheMarkIsDone() {
        val p = KhatmahPlan(start = 100, days = 7, reached = 200)
        assertTrue(p.portion(100).done)
    }

    @Test
    fun fromAPageCountsOnlyTheRest() {
        val p = KhatmahPlan(start = 0, days = 2, from = 600)
        assertEquals(5, p.total)
        assertEquals(602, p.mark(1))
        assertEquals(604, p.mark(2))
    }

    @Test
    fun pastTheLastDayTheWholeRestIsDue() {
        val p = KhatmahPlan(start = 0, days = 10, reached = 300)
        val late = p.portion(20)
        assertEquals(301, late.fromPage)
        assertEquals(604, late.toPage)
    }
}
