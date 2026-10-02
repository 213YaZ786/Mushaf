package org.mushaf.app.data.hifz

import android.content.Context
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.hifz.Grade
import org.mushaf.app.core.hifz.Lesson
import org.mushaf.app.core.hifz.Order
import org.mushaf.app.core.hifz.PageState
import org.mushaf.app.core.hifz.Runs
import org.mushaf.app.core.hifz.Schedule
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.quran.Quran

/** How new ayat are learnt. */
@Serializable
data class Plan(
    /** Lines of the mushaf learnt each day; 0 for revision only. */
    val lines: Int = 7,
    val order: Order = Order.FROM_AN_NAS,
    /** Old pages revised a day at most (the manzil). */
    val perDay: Int = 10
)

/** A word that slipped while reciting from memory. */
@Serializable
data class Slip(val key: AyahKey, val position: Int, val count: Int, val last: Int)

@Serializable
data class HifzFile(
    /** The ayat known by heart, as runs of their place in the Quran (Runs). */
    val known: String = "",
    val pages: List<PageState> = emptyList(),
    val slips: List<Slip> = emptyList(),
    val plan: Plan? = null,
    /** Pages revised each day, for the days in a row. */
    val days: Map<Int, Int> = emptyMap()
)

/**
 * What the reader knows by heart and when to revise it, in one file on the
 * phone. An ayah is known or not; a page is revised as a whole, the ayat of
 * it that are known.
 */
class Hifz(context: Context, private val quran: Quran) {

    private val file = File(context.filesDir, "hifz.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow(load())
    val state: StateFlow<HifzFile> = _state.asStateFlow()

    private fun load(): HifzFile = runCatching { json.decodeFromString<HifzFile>(file.readText()) }.getOrDefault(HifzFile())

    private fun save(updated: HifzFile) {
        _state.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }

    fun today(): Int = LocalDate.now().toEpochDay().toInt()

    private suspend fun indexOf(): Map<AyahKey, Int> = quran.ayat().withIndex().associate { (i, a) -> a.key to i }

    suspend fun knownSet(): Set<AyahKey> {
        val bits = Runs.decode(_state.value.known)
        val ayat = quran.ayat()
        return buildSet { var i = bits.nextSetBit(0); while (i >= 0 && i < ayat.size) { add(ayat[i].key); i = bits.nextSetBit(i + 1) } }
    }

    fun setPlan(plan: Plan) = save(_state.value.copy(plan = plan))

    /** Marks [keys] known (or not), and starts revising the pages they are on. */
    suspend fun setKnown(keys: Collection<AyahKey>, known: Boolean) {
        val index = indexOf()
        val bits = Runs.decode(_state.value.known)
        for (k in keys) index[k]?.let { bits.set(it, known) }
        val day = today()
        val pages = _state.value.pages.associateBy { it.page }.toMutableMap()
        val ayat = quran.ayat()
        val touched = keys.mapNotNull { k -> index[k]?.let { ayat[it].page } }.toSet()
        for (p in touched) {
            val anyKnown = ayat.withIndex().any { (i, a) -> a.page == p && bits[i] }
            if (anyKnown && p !in pages) {
                // Pages known from before the app are not new: they go straight to the manzil.
                pages[p] = if (keys.size > 40) PageState(p, learnedOn = day - Schedule.RECENT_DAYS, due = day + (p % 10)) else Schedule.learnt(p, day)
            }
            if (!anyKnown) pages.remove(p)
        }
        save(_state.value.copy(known = Runs.encode(bits), pages = pages.values.sortedBy { it.page }))
    }

    /** The next lesson of the plan, empty when there is nothing new to learn. */
    suspend fun lesson(): List<AyahKey> {
        val plan = _state.value.plan ?: return emptyList()
        if (plan.lines <= 0) return emptyList()
        val known = knownSet()
        val ayat = quran.ayat()
        val ordered = Lesson.ordered(ayat.map { it.key }, plan.order)
        val lines = linesPerAyah()
        return Lesson.next(ordered, { it in known }, { lines[it] ?: 1 }, plan.lines)
    }

    private var lineCache: Map<AyahKey, Int>? = null

    /** How many lines of the mushaf each ayah takes. */
    private suspend fun linesPerAyah(): Map<AyahKey, Int> = lineCache ?: buildMap {
        for (p in 1..604) {
            for ((key, words) in quran.page(p).words.groupBy { it.key }) {
                put(key, (get(key) ?: 0) + words.map { it.line }.distinct().size)
            }
        }
    }.also { lineCache = it }

    fun grade(page: Int, grade: Grade) {
        val day = today()
        val s = _state.value
        val pages = s.pages.map { if (it.page == page) Schedule.review(it, grade, day) else it }
        save(s.copy(pages = pages, days = s.days + (day to (s.days[day] ?: 0) + 1)))
    }

    fun revision(): Schedule.Today = Schedule.today(_state.value.pages, today(), _state.value.plan?.perDay ?: 10)

    /** A word slipped: counted, so the weak places come back in practice. */
    fun slip(key: AyahKey, position: Int) {
        val s = _state.value
        val old = s.slips.firstOrNull { it.key == key && it.position == position }
        val rest = s.slips.filter { it !== old }
        save(s.copy(slips = rest + Slip(key, position, (old?.count ?: 0) + 1, today())))
    }

    /** Days in a row with some revision, today included if done. */
    fun streak(): Int {
        val days = _state.value.days
        var d = today()
        if (days[d] == null) d--
        var n = 0
        while (days[d] != null) { n++; d-- }
        return n
    }
}
