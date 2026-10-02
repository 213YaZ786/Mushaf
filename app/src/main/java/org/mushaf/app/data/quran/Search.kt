package org.mushaf.app.data.quran

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
 * translation chosen; "2:255" goes to the ayah and "page 50" to the page.
 */
class Search(private val quran: Quran, private val translations: Translations) {

    @Volatile private var plain: List<String>? = null

    suspend fun find(query: String, translation: String?, limit: Int = 300): List<Found> = withContext(Dispatchers.Default) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        Regex("""^(?:p|page)\s*(\d{1,3})$""", RegexOption.IGNORE_CASE).find(q)?.let { m ->
            val p = m.groupValues[1].toInt()
            if (p in 1..PAGES) return@withContext listOf(Found.Page(p))
        }
        AyahKey.parse(q)?.let { key ->
            val ayah = quran.ayah(key) ?: return@withContext emptyList()
            return@withContext listOf(Found.Ayah(key, ayah.page, quran.english(key), false))
        }
        val ayat = quran.ayat()
        if (Arabic.isArabic(q)) {
            val needle = Arabic.normalize(q)
            if (needle.isEmpty()) return@withContext emptyList()
            val texts = plain ?: ayat.map { Arabic.normalize(it.plain) }.also { plain = it }
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
