package org.mushaf.app.feature.mushaf

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
class Reader(private val settings: SettingsStore) {

    private val _page = MutableStateFlow(settings.current.page.coerceIn(1, PAGES))
    val page: StateFlow<Int> = _page.asStateFlow()

    /** A page asked for by another screen, taken once by the mushaf. */
    private val _goTo = MutableStateFlow<Int?>(null)
    val goTo: StateFlow<Int?> = _goTo.asStateFlow()

    /** The ayah to point at after a jump, until the reader moves on. */
    private val _marked = MutableStateFlow<AyahKey?>(null)
    val marked: StateFlow<AyahKey?> = _marked.asStateFlow()

    /** The mushaf shows [page]: remembered. */
    fun shown(page: Int) {
        val p = page.coerceIn(1, PAGES)
        if (_page.value == p) return
        _page.value = p
        settings.update { it.copy(page = p) }
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
