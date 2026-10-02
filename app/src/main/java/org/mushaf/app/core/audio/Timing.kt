package org.mushaf.app.core.audio

import kotlinx.serialization.Serializable
import org.mushaf.app.core.quran.AyahKey

/** Where a word is heard in a surah's recording, in milliseconds. */
@Serializable
data class WordTime(val position: Int, val from: Long, val to: Long)

/** Where an ayah is heard, and its words when the recording gives them. */
@Serializable
data class AyahTime(val surah: Int, val ayah: Int, val from: Long, val to: Long, val words: List<WordTime> = emptyList()) {
    val key: AyahKey get() = AyahKey(surah, ayah)
}

/** A surah recited by one reciter: its file and the timing of each ayah. */
@Serializable
data class SurahAudio(val reciter: Int, val surah: Int, val url: String, val ayat: List<AyahTime>)

/** What is heard at a moment of the recording. */
data class Heard(val ayah: AyahTime, val word: Int?)

object Timing {

    /** The ayah and the word heard at [ms]; the last ayah once past the end. */
    fun at(audio: SurahAudio, ms: Long): Heard? {
        val list = audio.ayat
        if (list.isEmpty()) return null
        var lo = 0
        var hi = list.size - 1
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (list[mid].from <= ms) lo = mid else hi = mid - 1
        }
        val ayah = list[lo]
        val word = ayah.words.lastOrNull { it.from <= ms && ms < it.to }?.position
        return Heard(ayah, word)
    }

    /**
     * The segments of one ayah as the API gives them, [position, from, to],
     * kept only when they fall inside the ayah: some recordings carry
     * segments counted from another origin, which would light the wrong
     * words, and are better left without word timing.
     */
    fun words(segments: List<List<Long>>, from: Long, to: Long): List<WordTime> {
        val words = segments.filter { it.size >= 3 }.map { WordTime(it[0].toInt(), it[1], it[2]) }
        val slack = 300
        return if (words.isNotEmpty() && words.all { it.from >= from - slack && it.to <= to + slack && it.to >= it.from }) words else emptyList()
    }
}
