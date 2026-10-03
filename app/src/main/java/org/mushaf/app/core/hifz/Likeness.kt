package org.mushaf.app.core.hifz

import org.mushaf.app.core.quran.Arabic

/**
 * Two ayat that read alike, word against word: the words they share in
 * order, and so the ones that set them apart.
 */
object Likeness {

    /** For each word of [a] and of [b], true when it is shared in order with the other ayah. */
    fun shared(a: List<String>, b: List<String>): Pair<BooleanArray, BooleanArray> {
        val x = a.map { Arabic.normalize(it) }
        val y = b.map { Arabic.normalize(it) }
        val n = x.size
        val m = y.size
        val len = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) for (j in m - 1 downTo 0) {
            len[i][j] = if (x[i] == y[j]) len[i + 1][j + 1] + 1 else maxOf(len[i + 1][j], len[i][j + 1])
        }
        val inA = BooleanArray(n)
        val inB = BooleanArray(m)
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                x[i] == y[j] -> { inA[i] = true; inB[j] = true; i++; j++ }
                len[i + 1][j] >= len[i][j + 1] -> i++
                else -> j++
            }
        }
        return inA to inB
    }
}
