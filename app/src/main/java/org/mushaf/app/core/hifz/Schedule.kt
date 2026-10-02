package org.mushaf.app.core.hifz

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.serialization.Serializable

/** How a page went when recited from memory. */
enum class Grade { AGAIN, HARD, GOOD, EASY }

/**
 * A page known by heart and when to recite it again. Days are counted as
 * epoch days (LocalDate.toEpochDay).
 */
@Serializable
data class PageState(
    val page: Int,
    /** When it was first known; a page learnt in the last week is revised every day (sabqi). */
    val learnedOn: Int,
    val due: Int,
    val interval: Int = 0,
    val ease: Float = 2.5f,
    val reps: Int = 0,
    val lapses: Int = 0,
    val last: Int = -1
)

/**
 * Spaced revision of the pages known by heart, in the spirit of SM-2 but
 * held to the way the Quran is kept: no page waits more than [MAX_DAYS]
 * days, a page that slipped comes back the next day, a page just learnt
 * comes back every day for a week.
 */
object Schedule {

    const val MAX_DAYS = 30
    const val RECENT_DAYS = 7

    fun learnt(page: Int, today: Int) = PageState(page = page, learnedOn = today, due = today + 1)

    fun review(s: PageState, grade: Grade, today: Int): PageState {
        val ease = when (grade) {
            Grade.AGAIN -> s.ease - 0.2f
            Grade.HARD -> s.ease - 0.15f
            Grade.GOOD -> s.ease
            Grade.EASY -> s.ease + 0.15f
        }.coerceIn(1.3f, 3.0f)
        val interval = when (grade) {
            Grade.AGAIN -> 1
            Grade.HARD -> max(1, (max(1, s.interval) * 1.2f).roundToInt())
            Grade.GOOD -> when (s.reps) {
                0 -> 1
                1 -> 3
                else -> max(s.interval + 1, (s.interval * ease).roundToInt())
            }
            Grade.EASY -> when (s.reps) {
                0 -> 3
                1 -> 6
                else -> max(s.interval + 2, (s.interval * ease * 1.3f).roundToInt())
            }
        }.let { min(it, MAX_DAYS) }
        // While the page is new it comes back daily whatever the grade.
        val recent = today - s.learnedOn < RECENT_DAYS
        return s.copy(
            ease = ease,
            interval = interval,
            reps = if (grade == Grade.AGAIN) 0 else s.reps + 1,
            lapses = s.lapses + if (grade == Grade.AGAIN) 1 else 0,
            last = today,
            due = today + if (recent) 1 else interval
        )
    }

    /** How firm a page is, 0 to 1, for the map: firm when reviewed well and not overdue. */
    fun strength(s: PageState, today: Int): Float {
        val base = (s.interval / MAX_DAYS.toFloat()).coerceIn(0.15f, 1f) * (s.ease / 3f).coerceIn(0.5f, 1f)
        val overdue = (today - s.due).coerceAtLeast(0)
        return (base - overdue * 0.05f).coerceIn(0.05f, 1f)
    }

    /** The revision of the day. */
    data class Today(
        /** Learnt this week, recited every day. */
        val recent: List<Int>,
        /** Older pages whose turn has come, the most overdue first, as many as [limit] allows. */
        val due: List<Int>,
        /** Due pages left for the next days. */
        val waiting: Int
    )

    fun today(pages: Collection<PageState>, today: Int, limit: Int): Today {
        val recent = pages.filter { today - it.learnedOn in 1 until RECENT_DAYS && it.last != today }.map { it.page }.sorted()
        val older = pages.filter { today - it.learnedOn >= RECENT_DAYS && it.due <= today && it.last != today }
            .sortedWith(compareBy({ it.due }, { it.page }))
        return Today(recent, older.take(limit).map { it.page }.sorted(), (older.size - limit).coerceAtLeast(0))
    }
}
