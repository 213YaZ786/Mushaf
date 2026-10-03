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
data class Bookmark(val key: AyahKey, val at: Long, val collection: String? = null)

@Serializable
data class Note(val key: AyahKey, val text: String, val at: Long)

@Serializable
data class MarksFile(
    val bookmarks: List<Bookmark> = emptyList(),
    val notes: List<Note> = emptyList(),
    /** The reader's own collections (du'as, to revise...), in the order made. */
    val collections: List<String> = emptyList()
)

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

    /** Reads the file again, after a backup was restored into it. */
    fun reload() {
        _marks.value = load()
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

    /** Puts the ayah in [collection] (none when null), bookmarking it if it was not. */
    fun file(key: AyahKey, collection: String?) {
        val m = _marks.value
        val kept = m.bookmarks.firstOrNull { it.key == key } ?: Bookmark(key, System.currentTimeMillis())
        save(m.copy(bookmarks = m.bookmarks.filter { it.key != key } + kept.copy(collection = collection?.takeIf { it in m.collections })))
    }

    /** Adds a collection; false when the name is empty or already taken. */
    fun addCollection(name: String): Boolean {
        val n = name.trim()
        val m = _marks.value
        if (n.isEmpty() || n in m.collections) return false
        save(m.copy(collections = m.collections + n))
        return true
    }

    fun renameCollection(old: String, name: String) {
        val n = name.trim()
        val m = _marks.value
        if (n.isEmpty() || n == old || n in m.collections) return
        save(m.copy(
            collections = m.collections.map { if (it == old) n else it },
            bookmarks = m.bookmarks.map { if (it.collection == old) it.copy(collection = n) else it }
        ))
    }

    /** Removes the collection; its ayat stay bookmarked, outside any collection. */
    fun removeCollection(name: String) {
        val m = _marks.value
        save(m.copy(
            collections = m.collections - name,
            bookmarks = m.bookmarks.map { if (it.collection == name) it.copy(collection = null) else it }
        ))
    }

    fun note(key: AyahKey): String? = _marks.value.notes.firstOrNull { it.key == key }?.text

    /** Writes the note on [key]; an empty text removes it. */
    fun setNote(key: AyahKey, text: String) {
        val m = _marks.value
        val rest = m.notes.filter { it.key != key }
        save(m.copy(notes = if (text.isBlank()) rest else rest + Note(key, text.trim(), System.currentTimeMillis())))
    }
}
