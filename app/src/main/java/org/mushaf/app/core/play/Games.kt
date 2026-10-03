package org.mushaf.app.core.play

import kotlin.random.Random
import org.mushaf.app.core.quran.AyahKey

/** A word of a game: its text and its place in the ayah. */
data class Tile(val text: String, val position: Int)

/** Rebuild the ayah: its words shuffled, to be tapped back in order. */
data class Puzzle(val key: AyahKey, val answer: List<Tile>, val shuffled: List<Tile>)

/** One word left out of the ayah, to be found among a few. */
data class Missing(val key: AyahKey, val words: List<String>, val hole: Int, val choices: List<String>)

/** A word and what it means, to be matched. */
data class Meaning(val word: String, val meaning: String)

/** An ayah heard, to be found among a few written ones. */
data class Which(val answer: AyahKey, val choices: List<AyahKey>)

/**
 * The games for children, built from the text itself so nothing can be
 * wrong in them: the ayah is always the mushaf's, only its order or one
 * of its words is hidden.
 */
object Games {

    /** [words] of one ayah in order. Never shuffled into its own order, when it has two words or more. */
    fun puzzle(key: AyahKey, words: List<String>, random: Random): Puzzle {
        val answer = words.mapIndexed { i, w -> Tile(w, i) }
        var shuffled = answer.shuffled(random)
        var tries = 0
        while (answer.size > 1 && shuffled == answer && tries++ < 10) shuffled = answer.shuffled(random)
        return Puzzle(key, answer, shuffled)
    }

    /**
     * One word of the ayah left out, with [count] choices: the word and
     * others from [pool] (the surah's words) that read differently.
     */
    fun missing(key: AyahKey, words: List<String>, pool: List<String>, plain: (String) -> String, random: Random, count: Int = 3): Missing {
        val candidates = words.indices.filter { plain(words[it]).length >= 2 }.ifEmpty { words.indices.toList() }
        val hole = candidates.random(random)
        val answer = words[hole]
        val others = pool.filter { plain(it) != plain(answer) }.distinctBy(plain).shuffled(random).take(count - 1)
        return Missing(key, words, hole, (others + answer).shuffled(random))
    }

    /**
     * Rounds of [size] words and their meanings, taken from [words] (the
     * surah's, with the meaning given for each): within the game no word and
     * no meaning comes twice, so every word has one meaning to match, and
     * one only. A last round too small to play is left out.
     */
    fun matching(words: List<Meaning>, plain: (String) -> String, random: Random, size: Int = 4, rounds: Int = 3): List<List<Meaning>> {
        val usable = words.filter { plain(it.word).isNotEmpty() && it.meaning.isNotBlank() }
        val byWord = usable.groupBy { plain(it.word) }
        val byMeaning = usable.groupBy { it.meaning.trim().lowercase() }
        // A word read with two meanings, or a meaning given to two words, could be matched two ways: left out.
        val clear = usable.filter { w ->
            byWord[plain(w.word)]!!.map { it.meaning.trim().lowercase() }.distinct().size == 1 &&
                byMeaning[w.meaning.trim().lowercase()]!!.map { plain(it.word) }.distinct().size == 1
        }.distinctBy { plain(it.word) }
        return clear.shuffled(random).chunked(size).filter { it.size == size }.take(rounds)
    }

    /** An ayah of [ayat] to hear, among [count] of them. */
    fun which(ayat: List<AyahKey>, random: Random, count: Int = 3): Which {
        val choices = ayat.shuffled(random).take(minOf(count, ayat.size))
        return Which(choices.random(random), choices)
    }
}
