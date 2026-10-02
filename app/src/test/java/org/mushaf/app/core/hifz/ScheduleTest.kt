package org.mushaf.app.core.hifz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleTest {

    @Test
    fun aNewPageComesBackEveryDayForAWeek() {
        var s = Schedule.learnt(582, today = 100)
        for (day in 101..106) {
            assertEquals(day, s.due)
            s = Schedule.review(s, Grade.EASY, day)
        }
        assertEquals(107, s.due)
        // Then it spaces out.
        s = Schedule.review(s, Grade.GOOD, 107)
        assertTrue(s.due > 108)
    }

    @Test
    fun noPageWaitsMoreThanAMonth() {
        var s = Schedule.learnt(1, today = 0)
        var day = 10
        repeat(20) {
            s = Schedule.review(s, Grade.EASY, day)
            assertTrue(s.interval <= Schedule.MAX_DAYS)
            day = s.due
        }
        assertEquals(Schedule.MAX_DAYS, s.interval)
    }

    @Test
    fun aForgottenPageComesBackTomorrow() {
        val s = Schedule.review(PageState(5, learnedOn = 0, due = 50, interval = 20, reps = 4), Grade.AGAIN, 50)
        assertEquals(51, s.due)
        assertEquals(1, s.lapses)
        assertEquals(0, s.reps)
    }

    @Test
    fun todaySplitsRecentAndDueAndCapsTheDue() {
        val pages = listOf(
            PageState(600, learnedOn = 98, due = 100),
            PageState(10, learnedOn = 0, due = 90),
            PageState(11, learnedOn = 0, due = 95),
            PageState(12, learnedOn = 0, due = 99),
            PageState(13, learnedOn = 0, due = 120)
        )
        val t = Schedule.today(pages, today = 100, limit = 2)
        assertEquals(listOf(600), t.recent)
        assertEquals(listOf(10, 11), t.due)
        assertEquals(1, t.waiting)
    }
}
