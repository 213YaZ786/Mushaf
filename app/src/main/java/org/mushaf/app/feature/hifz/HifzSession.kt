package org.mushaf.app.feature.hifz

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.mushaf.app.core.hifz.Grade
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.feature.mushaf.Reader
import org.mushaf.app.feature.mushaf.WordShow

/** A new lesson to learn, or a page to recite from memory. */
enum class SessionKind { LESSON, REVISION }

/** A word by its place: ayah and position. */
data class Spot(val key: AyahKey, val position: Int)

data class Session(
    val kind: SessionKind,
    val page: Int,
    /** The ayat hidden: the lesson, or the known ayat of the page. */
    val keys: Set<AyahKey>,
    val show: WordShow,
    val revealed: Set<Spot> = emptySet(),
    val slipped: Set<Spot> = emptySet()
) {
    fun showOf(w: Word): WordShow =
        if (w.end || w.key !in keys || Spot(w.key, w.position) in revealed) WordShow.ALL else show
}

/**
 * The page while learning or revising by heart: the ayat of the session
 * hidden as the reader wants, a tap shows a word, a long press marks a
 * word that slipped; at the end the page is graded, or the lesson known.
 */
class HifzSession(private val hifz: Hifz, private val reader: Reader) {

    private val _session = MutableStateFlow<Session?>(null)
    val session: StateFlow<Session?> = _session.asStateFlow()

    fun startLesson(keys: List<AyahKey>, page: Int) {
        if (keys.isEmpty()) return
        // Learning starts with the words shown; they hide when the reader is ready.
        _session.value = Session(SessionKind.LESSON, page, keys.toSet(), WordShow.ALL)
        reader.go(page, keys.first())
    }

    suspend fun startRevision(page: Int) {
        val known = hifz.knownSet()
        val keys = known
        _session.value = Session(SessionKind.REVISION, page, keys, WordShow.HIDDEN)
        reader.go(page)
    }

    fun setShow(show: WordShow) = _session.update { it?.copy(show = show, revealed = emptySet()) }

    fun reveal(w: Word) = _session.update { it?.copy(revealed = it.revealed + Spot(w.key, w.position)) }

    /** Shows the next hidden word, in reading order, on [words] (the page's words). */
    fun revealNext(words: List<Word>) = _session.update { s ->
        s ?: return@update null
        val next = words.firstOrNull { !it.end && s.showOf(it) != WordShow.ALL }
        if (next == null) s else s.copy(revealed = s.revealed + Spot(next.key, next.position))
    }

    fun slip(w: Word) {
        val s = _session.value ?: return
        val spot = Spot(w.key, w.position)
        if (spot in s.slipped) return
        hifz.slip(w.key, w.position)
        _session.value = s.copy(slipped = s.slipped + spot, revealed = s.revealed + spot)
    }

    fun grade(grade: Grade) {
        val s = _session.value ?: return
        hifz.grade(s.page, grade)
        _session.value = null
    }

    suspend fun learnt() {
        val s = _session.value ?: return
        hifz.setKnown(s.keys, true)
        _session.value = null
    }

    fun stop() {
        _session.value = null
    }
}
