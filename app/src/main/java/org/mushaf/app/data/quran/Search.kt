package org.mushaf.app.data.quran

import org.mushaf.app.core.common.latinDigits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mushaf.app.core.quran.Arabic
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.PAGES

/** What a search found. */
sealed interface Found {
    data class Page(val page: Int) : Found
    /** An ayah, with the text the query was found in. */
    data class Ayah(val key: AyahKey, val page: Int, val text: String, val arabic: Boolean) : Found
}

/**
 * Search in the Quran: Arabic typed with or without vowels against the
 * text, any other language against the English meaning and the first
 * translation chosen; "2:255" goes to the ayah, "page 50" (or the word
 * for page in the reader's language, or the number alone) to the page.
 * Digits are read whatever the keyboard writes: ٢:٢٥٥ is 2:255.
 */
class Search(private val quran: Quran, private val translations: Translations) {

    /** Each ayah's letters, kept per riwayah (the lists differ in length and order). */
    private val plain = java.util.concurrent.ConcurrentHashMap<org.mushaf.app.core.quran.Riwayah, List<String>>()

    suspend fun find(query: String, translation: String?, pageWords: Set<String> = emptySet(), limit: Int = 300): List<Found> = withContext(Dispatchers.Default) {
        val q = latinDigits(query.trim())
        if (q.isEmpty()) return@withContext emptyList()
        pageOf(q, pageWords)?.let { return@withContext listOf(Found.Page(it)) }
        AyahKey.parse(q)?.let { key ->
            val ayah = quran.ayah(key) ?: return@withContext emptyList()
            return@withContext listOf(Found.Ayah(key, ayah.page, quran.english(key), false))
        }
        val ayat = quran.ayat()
        if (Arabic.isArabic(q)) {
            val needle = Arabic.normalize(q)
            if (needle.isEmpty()) return@withContext emptyList()
            val texts = plain.getOrPut(quran.riwayah) { ayat.map { Arabic.normalize(it.plain) } }
            ayat.indices.filter { texts[it].contains(needle) }.take(limit).map { i ->
                Found.Ayah(ayat[i].key, ayat[i].page, ayat[i].plain, true)
            }
        } else {
            val words = q.lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
            val out = mutableListOf<Found>()
            for (a in ayat) {
                val english = quran.english(a.key)
                val other = translation?.takeIf { it != Translations.BUNDLED.id }?.let { translations.text(it, a.key) }.orEmpty()
                val hit = when {
                    words.all { english.lowercase().contains(it) } -> english
                    other.isNotEmpty() && words.all { other.lowercase().contains(it) } -> other
                    else -> null
                }
                if (hit != null) out += Found.Ayah(a.key, a.page, hit, false)
                if (out.size >= limit) break
            }
            out
        }
    }
}

/** The page a query names: "50", "p. 50", "page 50" or [words] (the reader's own word for page) followed by a number. */
fun pageOf(query: String, words: Set<String>): Int? {
    val m = Regex("""^(?:(\p{L}[\p{L}\p{M}]*)\.?\s*)?(\d{1,3})$""").find(query.trim()) ?: return null
    val word = m.groupValues[1].lowercase()
    if (word.isNotEmpty() && word !in setOf("p", "page") + words) return null
    return m.groupValues[2].toInt().takeIf { it in 1..PAGES }
}
