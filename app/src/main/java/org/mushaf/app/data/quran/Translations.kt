package org.mushaf.app.data.quran

import android.content.Context
import android.util.LruCache
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Kabyle
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.sources.Sources

/** A translation of the meanings, from one of the catalogues. */
@Serializable
data class TranslationInfo(
    /** "qc:20" for Quran.com's resource 20, "fa:eng-ummmuhammad" for fawazahmed0's quran-api. */
    val id: String,
    val name: String,
    val language: String,
    val rtl: Boolean = false
)

/**
 * The translations of the meanings: Saheeh International comes with the
 * app; the others are listed from Quran.com (about 130, reviewed) and from
 * fawazahmed0's quran-api (about 500 editions in nearly 100 languages,
 * gathered from QuranEnc, Tanzil and others), and fetched whole when the
 * reader picks one. Each is kept as one small file, read only when shown.
 */
class Translations(private val context: Context, private val quran: Quran, private val sources: Sources) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "translations")
    private val texts = LruCache<String, Map<AyahKey, String>>(4)

    private val _installed = MutableStateFlow(readInstalled())
    /** The translations on the phone, the bundled one first. */
    val installed: StateFlow<List<TranslationInfo>> = _installed.asStateFlow()

    private fun readInstalled(): List<TranslationInfo> {
        val list = runCatching {
            json.decodeFromString<List<TranslationInfo>>(File(dir, "installed.json").readText())
        }.getOrDefault(emptyList())
        return listOf(BUNDLED) + list.filter { it.id != BUNDLED.id && File(dir, fileName(it.id)).exists() }
    }

    private fun saveInstalled(list: List<TranslationInfo>) {
        dir.mkdirs()
        File(dir, "installed.json").writeTextAtomically(json.encodeToString(list.filter { it.id != BUNDLED.id }))
        _installed.value = listOf(BUNDLED) + list.filter { it.id != BUNDLED.id }
    }

    fun info(id: String): TranslationInfo? = _installed.value.firstOrNull { it.id == id }

    /** Both catalogues, fetched once a day at most and kept for when the phone is offline. */
    suspend fun catalog(): List<TranslationInfo> = withContext(Dispatchers.IO) {
        val cache = File(dir, "catalog.json")
        val fresh = cache.exists() && System.currentTimeMillis() - cache.lastModified() < 24 * 3600_000L
        if (!fresh) {
            runCatching {
                val ours = quranCom()
                // An edition both catalogues hold is kept once, from Quran.com.
                val seen = ours.map { it.language.lowercase() + "|" + it.name.lowercase() }.toSet()
                val list = ours + fawaz().filter { (it.language.lowercase() + "|" + it.name.lowercase()) !in seen }
                dir.mkdirs()
                cache.writeTextAtomically(json.encodeToString(list))
            }
        }
        runCatching { json.decodeFromString<List<TranslationInfo>>(cache.readText()) }.getOrDefault(emptyList())
            .filter { it.id != BUNDLED.id }
    }

    private fun quranCom(): List<TranslationInfo> {
        val root = json.parseToJsonElement(first(sources.list.quranCom) { "$it/resources/translations" }).jsonObject
        return root["translations"]!!.jsonArray.map { it.jsonObject }.map { t ->
            val language = t["language_name"]!!.jsonPrimitive.content.replaceFirstChar { it.uppercase() }
            TranslationInfo(
                id = "qc:" + t["id"]!!.jsonPrimitive.int,
                name = t["translated_name"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: t["name"]!!.jsonPrimitive.content,
                language = language,
                rtl = language in RTL_LANGUAGES
            )
        }
    }

    private fun fawaz(): List<TranslationInfo> {
        val root = json.parseToJsonElement(first(sources.list.fawaz) { "$it/editions.min.json" }).jsonObject
        return root.values.map { it.jsonObject }.mapNotNull { e ->
            val name = e["name"]!!.jsonPrimitive.content
            val language = e["language"]!!.jsonPrimitive.content
            // The Arabic editions are the Quran's own text, not a translation.
            if (language == "Arabic" && name.startsWith("ara-quran")) return@mapNotNull null
            // The same edition again, transliterated into Latin letters: left out.
            if (name.endsWith("-la") || name.endsWith("-lad")) return@mapNotNull null
            TranslationInfo(
                id = "fa:$name",
                name = e["author"]!!.jsonPrimitive.content,
                // Ramdane At Mensour's is Kabyle, listed as Berber.
                language = if (Kabyle.isLegacy("fa:$name")) "Kabyle (Taqbaylit)" else language,
                rtl = e["direction"]?.jsonPrimitive?.contentOrNull == "rtl"
            )
        }
    }

    /**
     * Fetches the translations chosen but not on the phone: a first launch
     * without a connection leaves them out. Run when the app opens; quiet
     * while it still cannot, as the meaning then shows the others.
     */
    suspend fun ensure(ids: List<String>) {
        val missing = ids.filter { it != BUNDLED.id && info(it) == null }
        if (missing.isEmpty()) return
        val known = catalog().associateBy { it.id }
        for (id in missing) known[id]?.let { runCatching { install(it) } }
    }

    /** Fetches [info] whole and keeps it. */
    suspend fun install(info: TranslationInfo) = withContext(Dispatchers.IO) {
        val rows: List<Pair<String, String>> = when {
            info.id.startsWith("qc:") -> {
                val id = info.id.removePrefix("qc:")
                require(id.all { it.isDigit() })
                val root = json.parseToJsonElement(first(sources.list.quranCom, BIG) { "$it/quran/translations/$id" }).jsonObject
                val list = root["translations"]!!.jsonArray
                // In the Quran's order, one per ayah.
                // Numbered as Hafs, whichever riwayah is read.
                val keys = quran.hafsOrder().map { it.toString() }
                if (list.size != keys.size) error("translation has ${list.size} ayat")
                keys.zip(list.map { clean(it.jsonObject["text"]!!.jsonPrimitive.content) })
            }
            info.id.startsWith("fa:") -> {
                val name = info.id.removePrefix("fa:")
                require(name.matches(Regex("^[a-z0-9-]+$")))
                val root = json.parseToJsonElement(first(sources.list.fawaz, BIG) { "$it/editions/$name.min.json" }).jsonObject
                (root["quran"] as JsonArray).map { it.jsonObject }.map { v ->
                    val text = clean(v["text"]!!.jsonPrimitive.content)
                    "${v["chapter"]!!.jsonPrimitive.int}:${v["verse"]!!.jsonPrimitive.int}" to
                        if (Kabyle.isLegacy(info.id)) Kabyle.fromLegacy(text) else text
                }
            }
            else -> error("unknown catalogue")
        }
        // A translation must give every ayah of the Quran, once.
        if (rows.size != 6236 || rows.map { it.first }.toSet().size != 6236) error("translation incomplete")
        dir.mkdirs()
        File(dir, fileName(info.id)).writeTextAtomically(rows.joinToString("\n") { (k, t) -> "$k\t$t" })
        saveInstalled(_installed.value.filter { it.id != info.id } + info)
    }

    fun remove(id: String) {
        if (id == BUNDLED.id) return
        File(dir, fileName(id)).delete()
        texts.remove(id)
        saveInstalled(_installed.value.filter { it.id != id })
    }

    /** The meaning of [key] in translation [id], empty when it is not on the phone. */
    suspend fun text(id: String, key: AyahKey): String {
        if (id == BUNDLED.id) return quran.english(key)
        val m = map(id)
        // Numbered as Hafs: a Warsh ayah shows the meaning of the Hafs ayat its words are in.
        return quran.hafsKeys(key).joinToString(" ") { m[it].orEmpty() }.trim()
    }

    private suspend fun map(id: String): Map<AyahKey, String> = texts.get(id) ?: withContext(Dispatchers.IO) {
        val f = File(dir, fileName(id))
        if (!f.exists()) return@withContext emptyMap()
        f.readLines().mapNotNull { row ->
            val tab = row.indexOf('\t')
            if (tab < 0) null else AyahKey.parse(row.substring(0, tab))?.let { it to row.substring(tab + 1) }
        }.toMap()
    }.also { if (it.isNotEmpty()) texts.put(id, it) }

    private fun fileName(id: String) = id.replace(':', '_').replace('/', '_') + ".txt"

    /** The answer of the first source that gives one. */
    private fun first(bases: List<String>, maxBytes: Long = 4L shl 20, path: (String) -> String): String {
        var last: Exception? = null
        for (base in bases) {
            try {
                return Net.text(path(base), maxBytes)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: error("no source")
    }

    companion object {
        val BUNDLED = TranslationInfo("qc:20", "Saheeh International", "English")
        private const val BIG = 24L shl 20
        private val RTL_LANGUAGES = setOf("Arabic", "Urdu", "Persian", "Pashto", "Kurdish", "Sindhi", "Uyghur", "Hebrew", "Divehi", "Dhivehi")

        /** Footnote marks and any markup dropped, plain text left. */
        fun clean(text: String): String = text
            .replace(Regex("<sup[^>]*>.*?</sup>"), "")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
            .replace('\t', ' ').replace('\n', ' ')
            .trim()
    }
}
