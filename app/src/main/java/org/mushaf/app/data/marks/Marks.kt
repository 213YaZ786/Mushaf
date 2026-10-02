package org.mushaf.app.data.marks

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Riwayah

@Serializable
data class Bookmark(val key: AyahKey, val at: Long)

@Serializable
data class Note(val key: AyahKey, val text: String, val at: Long)

@Serializable
data class MarksFile(val bookmarks: List<Bookmark> = emptyList(), val notes: List<Note> = emptyList())

/**
 * The reader's bookmarks and notes on ayat, in one small file on the
 * phone per riwayah (their ayat are not numbered alike), written
 * atomically. Nothing leaves the device.
 */
class Marks(context: Context, riwayah: Riwayah) {

    private val dir = context.filesDir
    @Volatile private var riwayah = riwayah
    private val file: File get() = File(dir, riwayah.file("marks"))
    private val json = Json { ignoreUnknownKeys = true }

    private val _marks = MutableStateFlow(load())
    val marks: StateFlow<MarksFile> = _marks.asStateFlow()

    private fun load(): MarksFile =
        runCatching { json.decodeFromString<MarksFile>(file.readText()) }.getOrDefault(MarksFile())

    private fun save(updated: MarksFile) {
        _marks.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }

    /** Reads the marks of [r], once the reader has changed riwayah. */
    fun use(r: Riwayah) {
        if (r == riwayah) return
        riwayah = r
        _marks.value = load()
    }

    fun isBookmarked(key: AyahKey) = _marks.value.bookmarks.any { it.key == key }

    /** Adds the bookmark, or takes it away; true when it is now there. */
    fun toggleBookmark(key: AyahKey): Boolean {
        val m = _marks.value
        val on = m.bookmarks.none { it.key == key }
        save(m.copy(bookmarks = if (on) m.bookmarks + Bookmark(key, System.currentTimeMillis()) else m.bookmarks.filter { it.key != key }))
        return on
    }

    fun note(key: AyahKey): String? = _marks.value.notes.firstOrNull { it.key == key }?.text

    /** Writes the note on [key]; an empty text removes it. */
    fun setNote(key: AyahKey, text: String) {
        val m = _marks.value
        val rest = m.notes.filter { it.key != key }
        save(m.copy(notes = if (text.isBlank()) rest else rest + Note(key, text.trim(), System.currentTimeMillis())))
    }
}
