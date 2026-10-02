package org.mushaf.app.data.quran

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.sources.Sources
import kotlinx.serialization.json.jsonArray

/** A book of tafsir offered by Quran.com. */
data class TafsirBook(val id: Int, val name: String, val language: String, val rtl: Boolean)

/**
 * Explanations of an ayah from the classical books of tafsir, fetched from
 * Quran.com when asked and kept, so an ayah read once opens offline.
 */
class Tafsir(private val context: Context, private val sources: Sources, private val quran: Quran) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "tafsir")

    /**
     * The tafsir of [key] in [book], as paragraphs; a tafsir that explains a
     * group of ayat gives the same text for each of them.
     */
    suspend fun of(book: TafsirBook, key: AyahKey): List<String> =
        // Numbered as Hafs: a Warsh ayah reads the tafsir of the Hafs ayat its words are in.
        quran.hafsKeys(key).flatMap { ofHafs(book, it) }.distinct()

    private suspend fun ofHafs(book: TafsirBook, key: AyahKey): List<String> = withContext(Dispatchers.IO) {
        val file = File(dir, "${book.id}/${key.surah}_${key.ayah}.txt")
        if (file.exists()) return@withContext file.readLines()
        val root = json.parseToJsonElement(first { "$it/tafsirs/${book.id}/by_ayah/$key" }).jsonObject
        val html = root["tafsir"]!!.jsonObject["text"]!!.jsonPrimitive.content
        val paragraphs = paragraphs(html)
        file.parentFile?.mkdirs()
        file.writeTextAtomically(paragraphs.joinToString("\n"))
        paragraphs
    }

    /** How many surahs of [book] are kept whole on the phone. */
    fun kept(book: TafsirBook): Int = File(dir, "${book.id}").list()?.count { it.startsWith("done_") } ?: 0

    fun remove(book: TafsirBook) {
        File(dir, "${book.id}").deleteRecursively()
    }

    /** Keeps the whole of [book] on the phone, surah by surah; [progress] hears each surah done. */
    suspend fun download(book: TafsirBook, progress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        for (surah in 1..114) {
            val done = File(dir, "${book.id}/done_$surah")
            if (!done.exists()) {
                val root = json.parseToJsonElement(first(16L shl 20) { "$it/tafsirs/${book.id}/by_chapter/$surah?per_page=300" }).jsonObject
                for (t in root["tafsirs"]!!.jsonArray) {
                    val o = t.jsonObject
                    val key = AyahKey.parse(o["verse_key"]!!.jsonPrimitive.content) ?: continue
                    val file = File(dir, "${book.id}/${key.surah}_${key.ayah}.txt")
                    file.parentFile?.mkdirs()
                    file.writeTextAtomically(paragraphs(o["text"]!!.jsonPrimitive.content).joinToString("\n"))
                }
                done.writeText("")
            }
            progress(surah)
        }
    }

    private fun first(maxBytes: Long = 2L shl 20, path: (String) -> String): String {
        var last: Exception? = null
        for (base in sources.list.quranCom) {
            try {
                return Net.text(path(base), maxBytes)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: error("no source")
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
