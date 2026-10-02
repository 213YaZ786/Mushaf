package org.mushaf.app.data.quran

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.quran.AyahKey

/** A book of tafsir offered by Quran.com. */
data class TafsirBook(val id: Int, val name: String, val language: String, val rtl: Boolean)

/**
 * Explanations of an ayah from the classical books of tafsir, fetched from
 * Quran.com when asked and kept, so an ayah read once opens offline.
 */
class Tafsir(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.cacheDir, "tafsir")

    /**
     * The tafsir of [key] in [book], as paragraphs; a tafsir that explains a
     * group of ayat gives the same text for each of them.
     */
    suspend fun of(book: TafsirBook, key: AyahKey): List<String> = withContext(Dispatchers.IO) {
        val file = File(dir, "${book.id}/${key.surah}_${key.ayah}.txt")
        if (file.exists()) return@withContext file.readLines()
        val root = json.parseToJsonElement(get("https://api.quran.com/api/v4/tafsirs/${book.id}/by_ayah/$key")).jsonObject
        val html = root["tafsir"]!!.jsonObject["text"]!!.jsonPrimitive.content
        val paragraphs = paragraphs(html)
        file.parentFile?.mkdirs()
        file.writeTextAtomically(paragraphs.joinToString("\n"))
        paragraphs
    }

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mushaf")
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** The books offered, English first, then the classical Arabic ones. */
        val BOOKS = listOf(
            TafsirBook(169, "Ibn Kathir (abridged)", "English", false),
            TafsirBook(168, "Ma'arif al-Qur'an", "English", false),
            TafsirBook(817, "Tazkirul Quran", "English", false),
            TafsirBook(16, "Al-Muyassar", "Arabic", true),
            TafsirBook(91, "As-Sa'di", "Arabic", true),
            TafsirBook(14, "Ibn Kathir", "Arabic", true),
            TafsirBook(15, "At-Tabari", "Arabic", true),
            TafsirBook(90, "Al-Qurtubi", "Arabic", true),
            TafsirBook(94, "Al-Baghawi", "Arabic", true),
            TafsirBook(93, "Al-Wasit (Tantawi)", "Arabic", true)
        )

        /** Paragraphs of plain text from the tafsir's markup; headings start with "# ". */
        fun paragraphs(html: String): List<String> {
            val blocks = Regex("<(h\\d|p|div)[^>]*>(.*?)</\\1>", RegexOption.DOT_MATCHES_ALL).findAll(html)
                .map { m -> (if (m.groupValues[1].startsWith("h")) "# " else "") + Translations.clean(m.groupValues[2]) }
                .filter { it.isNotBlank() && it != "# " }
                .toList()
            return blocks.ifEmpty { Translations.clean(html.replace(Regex("<br\\s*/?>"), "\n")).split('\n').filter { it.isNotBlank() } }
        }
    }
}
