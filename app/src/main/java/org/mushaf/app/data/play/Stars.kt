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

/** The stars won on each surah, in a small file on the phone. */
class Stars(context: Context) {
    private val file = File(context.filesDir, "stars.json")
    private val json = Json { ignoreUnknownKeys = true }

    private val _stars = MutableStateFlow(load())
    val stars: StateFlow<Map<Int, Set<Star>>> = _stars.asStateFlow()

    private fun load(): Map<Int, Set<Star>> =
        runCatching { json.decodeFromString<Map<Int, Set<Star>>>(file.readText()) }.getOrDefault(emptyMap())

    /** True when this star is new. */
    fun win(surah: Int, star: Star): Boolean {
        val now = _stars.value[surah].orEmpty()
        if (star in now) return false
        val updated = _stars.value + (surah to now + star)
        _stars.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
        return true
    }
}
