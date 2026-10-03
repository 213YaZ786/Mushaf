package org.mushaf.app.data.khatmah

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.quran.PAGES

/** A whole reading of the Quran in [days] days, from page [from], begun on day [start] (epoch day). */
@Serializable
data class KhatmahPlan(
    val start: Long,
    val days: Int,
    val from: Int = 1,
    /** The last page read in order. */
    val reached: Int = from - 1,
    /** The day the whole Quran was read, once it is. */
    val ended: Long? = null
) {
    val total: Int get() = PAGES - from + 1
    val read: Int get() = (reached - from + 1).coerceIn(0, total)
    val finished: Boolean get() = reached >= PAGES

    /** The portion of day [on] (epoch day). */
    fun portion(on: Long): Portion {
        val day = ((on - start).toInt() + 1).coerceAtLeast(1)
        val mark = mark(day)
        val next = (reached + 1).coerceAtMost(PAGES)
        return Portion(day, next, mark.coerceAtLeast(next), reached >= mark)
    }

    /** The last page to have read by the end of [day]: the pages spread evenly, rounded up. */
    fun mark(day: Int): Int {
        val d = day.coerceIn(0, days)
        return (from - 1 + (total * d + days - 1) / days).coerceAtMost(PAGES)
    }
}

/** Where the khatmah stands today. */
data class Portion(
    /** Today, 1 for the first day; past the last day when behind. */
    val day: Int,
    /** The pages to read today: from the next one to today's mark. */
    val fromPage: Int,
    val toPage: Int,
    /** Today's mark is reached. */
    val done: Boolean
)

/**
 * The reader's khatmah: the pages are spread evenly over the days, and the
 * pages read in order (one after the other, as the mushaf is turned) move
 * it on; a jump elsewhere does not. One small file, nothing leaves the phone.
 */
class Khatmah(context: Context) {

    private val file = File(context.filesDir, "khatmah.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _plan = MutableStateFlow(load())
    val plan: StateFlow<KhatmahPlan?> = _plan.asStateFlow()

    private fun load(): KhatmahPlan? = runCatching { json.decodeFromString<KhatmahPlan>(file.readText()) }.getOrNull()

    private fun save(p: KhatmahPlan?) {
        _plan.value = p
        runCatching { if (p == null) file.delete() else file.writeTextAtomically(json.encodeToString(p)) }
    }

    fun start(days: Int, from: Int = 1) =
        save(KhatmahPlan(start = today(), days = days.coerceIn(1, 3650), from = from.coerceIn(1, PAGES)))

    fun end() = save(null)

    /**
     * The mushaf shows pages up to [last], [step] pages at a time (two side
     * by side): the khatmah moves on when they follow the last page read.
     */
    fun shown(last: Int, step: Int) {
        val p = _plan.value ?: return
        if (p.finished) return
        if (last > p.reached && last <= p.reached + step) {
            val reached = last.coerceAtMost(PAGES)
            save(p.copy(reached = reached, ended = if (reached >= PAGES) today() else null))
        }
    }

    fun today(): Long = LocalDate.now().toEpochDay()
}
