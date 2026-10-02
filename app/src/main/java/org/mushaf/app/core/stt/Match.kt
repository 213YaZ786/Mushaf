package org.mushaf.app.core.stt

import org.mushaf.app.core.quran.Arabic

/** How a word of the text was recited. */
enum class Heard { RIGHT, CLOSE, SKIPPED }

/**
 * Follows a recitation through the text: what the recogniser heard,
 * word by word, is laid along the words expected from [start] on. A word
 * heard as written is right; nearly so (a letter or a vowel length off,
 * which the recogniser also does) is close; a word passed over is skipped.
 * Heard words that fit nothing nearby (a cough, a word said twice) are let go.
 */
object Match {

    data class Result(val marks: Map<Int, Heard>, val next: Int)

    fun follow(expected: List<String>, start: Int, heard: List<String>, lookAhead: Int = 3): Result {
        val want = expected.map { Arabic.normalize(it).replace(" ", "") }
        val marks = linkedMapOf<Int, Heard>()
        var cursor = start
        for (h0 in heard) {
            val h = Arabic.normalize(h0).replace(" ", "")
            if (h.isEmpty() || cursor >= want.size) continue
            var best = -1
            var bestScore = 0f
            for (i in cursor until minOf(want.size, cursor + lookAhead + 1)) {
                val score = similarity(h, want[i]) - (i - cursor) * 0.05f
                if (score > bestScore) { bestScore = score; best = i }
            }
            if (best < 0 || bestScore < 0.6f) continue
            for (i in cursor until best) marks[i] = Heard.SKIPPED
            marks[best] = if (want[best] == h) Heard.RIGHT else Heard.CLOSE
            cursor = best + 1
        }
        return Result(marks, cursor)
    }

    /** 1 for the same letters, falling towards 0 with each edit. */
    fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        val n = maxOf(a.length, b.length)
        if (n == 0) return 1f
        return 1f - distance(a, b).toFloat() / n
    }

    private fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }
}
