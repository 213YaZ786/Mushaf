package org.mushaf.app.core.reading

import android.icu.text.DateFormat
import android.icu.util.IslamicCalendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.util.Locale

/**
 * The Hijri date of a day, by the Umm al-Qura calendar (Makkah's). A
 * country that follows the moon's sighting may be a day apart; the
 * Gregorian date is shown beside it for that reason.
 */
object Hijri {

    private const val DAY_MS = 86_400_000L

    private fun calendar(epochDay: Long, locale: Locale) =
        IslamicCalendar(TimeZone.GMT_ZONE, ULocale.forLocale(locale)).apply {
            calculationType = IslamicCalendar.CalculationType.ISLAMIC_UMALQURA
            // Noon, so no time zone rounding moves the day.
            timeInMillis = epochDay * DAY_MS + DAY_MS / 2
        }

    /** The day of the Hijri month. */
    fun day(epochDay: Long, locale: Locale = Locale.getDefault()): Int =
        calendar(epochDay, locale).get(IslamicCalendar.DAY_OF_MONTH)

    /** "Rabiʻ II 1448", or "Rabiʻ I – Rabiʻ II 1448" when the days span two months, in the reader's language. */
    fun months(from: Long, to: Long, locale: Locale = Locale.getDefault()): String {
        val format = { day: Long, skeleton: String ->
            val cal = calendar(day, locale)
            DateFormat.getInstanceForSkeleton(cal, skeleton, ULocale.forLocale(locale)).apply {
                timeZone = TimeZone.GMT_ZONE
            }.format(cal.time)
        }
        val a = calendar(from, locale)
        val b = calendar(to, locale)
        return if (a.get(IslamicCalendar.MONTH) == b.get(IslamicCalendar.MONTH) && a.get(IslamicCalendar.YEAR) == b.get(IslamicCalendar.YEAR)) {
            format(to, "yMMMM")
        } else {
            format(from, "MMMM") + " – " + format(to, "yMMMM")
        }
    }
}
