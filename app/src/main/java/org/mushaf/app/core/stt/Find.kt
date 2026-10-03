package org.mushaf.app.core.stt

import org.mushaf.app.core.quran.Arabic
import org.mushaf.app.core.quran.AyahKey

/**
 * Finds the ayat a few heard words come from: the words heard are laid in
 * order along each ayah and the one after it (a passage may run over an
 * ayah's end), a word nearly right counting as nearly found; the ayat
 * where most of what was heard falls in order come first.
 */
object Find {

    data class Hit(val key: AyahKey, val score: Float)

    /** [ayat] in reading order, each with its words; at most [limit] hits, the best first. */
    fun rank(heard: String, ayat: List<Pair<AyahKey, List<String>>>, limit: Int = 5): List<Hit> {
        val words = Arabic.words(heard).map { it.replace(" ", "") }.filter { it.length > 1 }
        if (words.size < 2) return emptyList()
        val wanted = words.toSet()
        val norm = ayat.map { (k, w) -> k to w.map { Arabic.normalize(it).replace(" ", "") } }
        val hits = ArrayList<Hit>()
        for (i in norm.indices) {
            val (key, own) = norm[i]
            val next = norm.getOrNull(i + 1)?.takeIf { it.first.surah == key.surah }?.second.orEmpty()
            val span = own + next
            // Cheap first: an ayah none of whose words were heard is passed over.
            if (own.none { it in wanted } && own.none { o -> words.any { Match.similarity(it, o) >= 0.8f } }) continue
            val (score, firstAt) = inOrder(words, span)
            // The passage must start in this ayah; starting in the next one, that ayah's own turn finds it.
            if (firstAt < 0 || firstAt >= own.size) continue
            hits += Hit(key, score / words.size)
        }
        return hits.sortedByDescending { it.score }.filter { it.score >= 0.4f }.take(limit)
    }

    /** How much of [heard] falls in order along [text] (each word scored by its likeness), and where the first one falls. */
    internal fun inOrder(heard: List<String>, text: List<String>): Pair<Float, Int> {
        val n = heard.size
        val m = text.size
        val best = Array(n + 1) { FloatArray(m + 1) }
        for (i in 1..n) for (j in 1..m) {
            val s = Match.similarity(heard[i - 1], text[j - 1])
            val take = if (s >= 0.75f) best[i - 1][j - 1] + s else 0f
            best[i][j] = maxOf(best[i - 1][j], best[i][j - 1], take)
        }
        // Back along the best path to the first word of the text that was used.
        var i = n
        var j = m
        var first = -1
        while (i > 0 && j > 0) {
            val s = Match.similarity(heard[i - 1], text[j - 1])
            when {
                s >= 0.75f && best[i][j] == best[i - 1][j - 1] + s -> { first = j - 1; i--; j-- }
                best[i][j] == best[i - 1][j] -> i--
                else -> j--
            }
        }
        return best[n][m] to first
    }
}
