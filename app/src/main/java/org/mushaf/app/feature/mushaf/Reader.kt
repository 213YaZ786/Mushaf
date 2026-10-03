package org.mushaf.app.feature.mushaf

import androidx.compose.runtime.mutableStateSetOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.data.settings.SettingsStore

/**
 * Where the reader is: the page shown, kept so the app opens there again,
 * and the place another screen asks the mushaf to go to (the index, a
 * search result, a bookmark), with the ayah to point at once there.
 */
class Reader(private val settings: SettingsStore, private val quran: org.mushaf.app.data.quran.Quran) {

    private val _page = MutableStateFlow(settings.current.page.coerceIn(1, PAGES))
    val page: StateFlow<Int> = _page.asStateFlow()

    /** A page asked for by another screen, taken once by the mushaf. */
    private val _goTo = MutableStateFlow<Int?>(null)
    val goTo: StateFlow<Int?> = _goTo.asStateFlow()

    /** The ayah to point at after a jump, until the reader moves on. */
    private val _marked = MutableStateFlow<AyahKey?>(null)
    val marked: StateFlow<AyahKey?> = _marked.asStateFlow()

    /** Pages whose surah opening has played since the app started: it plays once. */
    val opened = mutableStateSetOf<Int>()

    /** The mushaf is open today: no reminder of the wird this evening. */
    fun read() {
        val today = java.time.LocalDate.now().toEpochDay()
        if (settings.current.readDay != today) settings.update { it.copy(readDay = today) }
    }

    /** The mushaf shows [page]: remembered. */
    fun shown(page: Int) {
        val p = page.coerceIn(1, PAGES)
        if (_page.value == p) return
        // A page turned (one or two on, or back), not a jump, counts as read today.
        val turned = kotlin.math.abs(p - _page.value) <= 2
        _page.value = p
        // A page of Al-Kahf seen today: no Friday reminder for it.
        val kahf = quran.metaNow?.surahs?.getOrNull(17)?.pages
        val today = java.time.LocalDate.now().toEpochDay()
        settings.update {
            it.copy(
                page = p,
                kahfDay = if (kahf != null && p in kahf.first()..kahf.last()) today else it.kahfDay,
                // A page turned: today is a day read.
                readDays = if (today in it.readDays) it.readDays else (it.readDays + today).takeLast(400),
                pagesByDay = if (!turned) it.pagesByDay else (it.pagesByDay + (today to (it.pagesByDay[today] ?: 0) + 1))
                    .entries.sortedBy { e -> e.key }.takeLast(400).associate { e -> e.key to e.value }
            )
        }
    }

    fun go(page: Int, mark: AyahKey? = null) {
        _marked.value = mark
        _goTo.value = page.coerceIn(1, PAGES)
    }

    fun wentTo() {
        _goTo.value = null
    }

    fun unmark() {
        _marked.value = null
    }
}
