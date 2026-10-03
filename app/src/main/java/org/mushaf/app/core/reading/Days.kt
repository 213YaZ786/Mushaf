package org.mushaf.app.core.reading

/** The days read (epoch days), as a streak and a calendar. */
object Days {

    /** Days in a row up to [today], or up to yesterday when today is not read yet. */
    fun streak(days: Collection<Long>, today: Long): Int {
        val set = days.toHashSet()
        var d = if (today in set) today else today - 1
        var n = 0
        while (d in set) { n++; d-- }
        return n
    }

    /**
     * The [weeks] weeks up to the one of [today], as rows of seven days
     * starting on [firstDay] (1 Monday … 7 Sunday, as java.time numbers them).
     */
    fun calendar(today: Long, weeks: Int, firstDay: Int): List<List<Long>> {
        // 1970-01-01 was a Thursday (4).
        val weekday = ((today + 3) % 7 + 7) % 7 + 1
        val back = ((weekday - firstDay) % 7 + 7) % 7
        val start = today - back - 7L * (weeks - 1)
        return (0 until weeks).map { w -> (0 until 7).map { start + w * 7L + it } }
    }
}
