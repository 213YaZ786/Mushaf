package org.mushaf.app.data.play

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically

/** The three stars of a surah: heard whole, built word by word, recited from memory. */
@Serializable
enum class Star { LISTENED, BUILT, RECITED }

/** A child playing, with the stars they won; no name until one is given. */
@Serializable
data class Child(val id: Int, val name: String = "", val stars: Map<Int, Set<Star>> = emptyMap())

@Serializable
data class Children(val active: Int = 1, val list: List<Child> = listOf(Child(1)))

/**
 * The children playing on this phone and the stars each won on each surah,
 * in a small file on the phone. Stars won before there were names go to
 * the first child named.
 */
class Stars(context: Context) {
    private val file = File(context.filesDir, "children.json")
    /** The file of the stars before there were children. */
    private val before = File(context.filesDir, "stars.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _children = MutableStateFlow(load())
    val children: StateFlow<Children> = _children.asStateFlow()

    private val _stars = MutableStateFlow(activeStars(_children.value))
    /** The stars of the child playing now. */
    val stars: StateFlow<Map<Int, Set<Star>>> = _stars.asStateFlow()

    /** Reads the file again, after a backup was restored into it. */
    fun reload() {
        _children.value = load()
        _stars.value = activeStars(_children.value)
    }

    private fun load(): Children {
        runCatching { return json.decodeFromString<Children>(file.readText()) }
        val old = runCatching { json.decodeFromString<Map<Int, Set<Star>>>(before.readText()) }.getOrDefault(emptyMap())
        return Children(list = listOf(Child(1, stars = old)))
    }

    private fun activeStars(c: Children) = c.list.firstOrNull { it.id == c.active }?.stars.orEmpty()

    private fun save(c: Children) {
        _children.value = c
        _stars.value = activeStars(c)
        runCatching { file.writeTextAtomically(json.encodeToString(c)) }
    }

    /** True when this star is new. */
    fun win(surah: Int, star: Star): Boolean {
        val c = _children.value
        val child = c.list.firstOrNull { it.id == c.active } ?: return false
        val now = child.stars[surah].orEmpty()
        if (star in now) return false
        save(c.copy(list = c.list.map { if (it.id == child.id) it.copy(stars = it.stars + (surah to now + star)) else it }))
        return true
    }

    fun select(id: Int) {
        val c = _children.value
        if (c.list.any { it.id == id }) save(c.copy(active = id))
    }

    /** A new child, who plays from now on; the first name given goes to the stars won so far. */
    fun add(name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        val c = _children.value
        val unnamed = c.list.singleOrNull()?.takeIf { it.name.isEmpty() }
        if (unnamed != null) {
            save(c.copy(list = listOf(unnamed.copy(name = n)), active = unnamed.id))
            return
        }
        val id = (c.list.maxOfOrNull { it.id } ?: 0) + 1
        save(c.copy(list = c.list + Child(id, n), active = id))
    }

    fun rename(id: Int, name: String) {
        val n = name.trim().take(30)
        if (n.isEmpty()) return
        val c = _children.value
        save(c.copy(list = c.list.map { if (it.id == id) it.copy(name = n) else it }))
    }

    /** The child and their stars are forgotten; the last one left keeps playing unnamed. */
    fun remove(id: Int) {
        val c = _children.value
        val rest = c.list.filter { it.id != id }.ifEmpty { listOf(Child((c.list.maxOfOrNull { it.id } ?: 0) + 1)) }
        save(c.copy(list = rest, active = if (c.active == id) rest.first().id else c.active))
    }
}
