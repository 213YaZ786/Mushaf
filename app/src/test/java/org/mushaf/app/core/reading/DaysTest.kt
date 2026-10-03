package org.mushaf.app.core.reading

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DaysTest {

    private val today = LocalDate.of(2026, 10, 3).toEpochDay() // a Saturday

    @Test
    fun aStreakCountsTheDaysInARow() {
        assertEquals(3, Days.streak(listOf(today, today - 1, today - 2, today - 4), today))
    }

    @Test
    fun todayNotReadYetKeepsYesterdaysStreak() {
        assertEquals(2, Days.streak(listOf(today - 1, today - 2), today))
        assertEquals(0, Days.streak(listOf(today - 2), today))
    }

    @Test
    fun theCalendarStartsOnTheWeeksFirstDay() {
        val monday = Days.calendar(today, 5, DayOfWeek.MONDAY.value)
        assertEquals(5, monday.size)
        assertEquals(DayOfWeek.MONDAY, LocalDate.ofEpochDay(monday[0][0]).dayOfWeek)
        // Today is in the last week.
        assertEquals(true, today in monday.last())
        val sunday = Days.calendar(today, 5, DayOfWeek.SUNDAY.value)
        assertEquals(DayOfWeek.SUNDAY, LocalDate.ofEpochDay(sunday[0][0]).dayOfWeek)
        assertEquals(today, sunday.last().last()) // Saturday closes a week starting on Sunday
    }
}
