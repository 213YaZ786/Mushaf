package org.mushaf.app.data.quran

import android.content.Context
import java.util.Locale
import android.util.LruCache
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.mushaf.app.core.quran.Ayah
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.MushafPage
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.core.quran.PageLayout
import org.mushaf.app.core.quran.QuranMeta
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.core.quran.Surah
import org.mushaf.app.data.settings.SettingsStore

/**
 * The Quran as bundled in the app, in the riwayah the reader chose:
 * Hafs in assets/quran (tools/quran_data.py), Warsh in assets/warsh
 * (tools/warsh_data.py), both checked against a second source before any
 * release. The surahs and divisions, each page's words, every ayah's plain
 * text; read from the assets when first asked, then kept.
 *
 * The meanings, the tafsir and the word by word are numbered as Hafs: a
 * Warsh ayah reaches them through the Hafs ayat its words are in.
 */
class Quran(private val context: Context, private val settings: SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val lock = Mutex()

    private class Edition {
        val pages = LruCache<Int, MushafPage>(16)
        @Volatile var meta: QuranMeta? = null
        @Volatile var ayat: List<Ayah>? = null
        @Volatile var index: Map<AyahKey, Ayah>? = null
    }

    private val editions = ConcurrentHashMap<Riwayah, Edition>()
    private fun edition(r: Riwayah = riwayah) = editions.getOrPut(r) { Edition() }

    val riwayah: Riwayah get() = settings.current.riwayah

    @Volatile private var englishCache: Map<AyahKey, String>? = null
    @Volatile private var hafsMap: Map<AyahKey, List<AyahKey>>? = null

    suspend fun meta(): QuranMeta {
        val r = riwayah
        val e = edition(r)
        e.meta?.let { return it }
        return lock.withLock {
            e.meta ?: withContext(Dispatchers.IO) {
                json.decodeFromString<QuranMeta>(asset("${r.folder}/meta.json"))
            }.also { e.meta = it }
        }
    }

    /** The meta when already read, for drawing without waiting. */
    val metaNow: QuranMeta? get() = edition().meta

    suspend fun surahs(): List<Surah> = meta().surahs

    suspend fun surah(n: Int): Surah = meta().surahs[n - 1]

    suspend fun page(number: Int): MushafPage {
        val n = number.coerceIn(1, PAGES)
        val r = riwayah
        val e = edition(r)
        e.pages.get(n)?.let { return it }
        return withContext(Dispatchers.IO) {
            val rows = asset("${r.folder}/pages/%03d.txt".format(Locale.ROOT, n)).lineSequence().filter { it.isNotBlank() }.toList()
            val words = rows.mapNotNull(PageLayout::parseWord)
            val explicit = rows.mapNotNull(PageLayout::parseLine).associateBy { it.number }
            // The next page's first word, for a surah title printed at the foot of this one.
            val next = if (explicit.isEmpty() && n < PAGES) {
                asset("${r.folder}/pages/%03d.txt".format(Locale.ROOT, n + 1)).lineSequence().firstOrNull()?.let(PageLayout::parseWord)
            } else null
            PageLayout.page(n, words, next, explicit).also { e.pages.put(n, it) }
        }
    }

    /** Every ayah in order, with its page and divisions. */
    suspend fun ayat(): List<Ayah> {
        val r = riwayah
        val e = edition(r)
        e.ayat?.let { return it }
        return lock.withLock {
            e.ayat ?: withContext(Dispatchers.IO) {
                asset("${r.folder}/ayat.txt").lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
                    val f = row.split('\t')
                    val key = AyahKey.parse(f[0]) ?: return@mapNotNull null
                    Ayah(key, f[1].toInt(), f[2].toInt(), f[3].toInt(), f.getOrElse(4) { "" })
                }.toList()
            }.also { list ->
                e.ayat = list
                e.index = list.associateBy { it.key }
            }
        }
    }

    suspend fun ayah(key: AyahKey): Ayah? {
        ayat()
        return edition().index?.get(key)
    }

    suspend fun pageOf(key: AyahKey): Int = ayah(key)?.page ?: 1

    /** The first ayah printed on [page]. */
    suspend fun firstAyah(page: Int): AyahKey =
        AyahKey.parse(meta().pageStart[page.coerceIn(1, PAGES) - 1]) ?: AyahKey(1, 1)

    @Volatile private var hafsOrderCache: List<AyahKey>? = null

    /** Every ayah as Hafs numbers them (6236), whatever the riwayah read: the order of the translations. */
    suspend fun hafsOrder(): List<AyahKey> = hafsOrderCache ?: withContext(Dispatchers.IO) {
        asset("quran/ayat.txt").lineSequence().filter { it.isNotBlank() }
            .mapNotNull { AyahKey.parse(it.substringBefore('\t')) }.toList()
    }.also { hafsOrderCache = it }

    /** The Hafs ayat [key] covers: itself in Hafs; in Warsh, the Hafs ayat its words are in. */
    suspend fun hafsKeys(key: AyahKey): List<AyahKey> {
        if (riwayah == Riwayah.HAFS) return listOf(key)
        val map = hafsMap ?: withContext(Dispatchers.IO) { hafsMapLoad() }
        return map[key] ?: listOf(key)
    }

    private fun hafsMapLoad(): Map<AyahKey, List<AyahKey>> =
        hafsMap ?: asset("warsh/hafs.txt").lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
            val tab = row.indexOf('\t')
            if (tab < 0) return@mapNotNull null
            val k = AyahKey.parse(row.substring(0, tab)) ?: return@mapNotNull null
            k to row.substring(tab + 1).split(',').mapNotNull(AyahKey::parse)
        }.toMap().also { hafsMap = it }

    /** The Warsh ayah whose words hold the Hafs ayah [hafs] (its first words, when split). */
    suspend fun warshKey(hafs: AyahKey): AyahKey {
        if (hafsMap == null) withContext(Dispatchers.IO) { hafsMapLoad() }
        val map = hafsMap.orEmpty()
        return map.entries.firstOrNull { hafs in it.value }?.key
            ?: map.entries.lastOrNull { e -> e.value.any { it < hafs } }?.key ?: hafs
    }

    /** The English meaning of [key] (Saheeh International), through Hafs's numbering. */
    suspend fun english(key: AyahKey): String {
        val map = englishMap()
        return hafsKeys(key).joinToString(" ") { map[it].orEmpty() }.trim()
    }

    private suspend fun englishMap(): Map<AyahKey, String> = englishCache ?: withContext(Dispatchers.IO) {
        asset("quran/en.txt").lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
            val tab = row.indexOf('\t')
            if (tab < 0) null else AyahKey.parse(row.substring(0, tab))?.let { it to row.substring(tab + 1) }
        }.toMap()
    }.also { englishCache = it }

    /**
     * The surah's introduction: paragraphs, headings starting with "# ",
     * and its source on a last line starting with "@ ".
     */
    suspend fun introduction(surah: Int): List<String> = withContext(Dispatchers.IO) {
        runCatching { asset("quran/info/$surah.txt") }.getOrDefault("").lines().filter { it.isNotBlank() }
    }

    @Volatile private var similarCache: Map<AyahKey, List<String>>? = null

    /**
     * Ayat that resemble [key], as "s:a" or "s:a+n" for a run of n + 1
     * ayat (Quran Revision Companion's list of mutashabihat), numbered as
     * Hafs: offered in Hafs only.
     */
    suspend fun similar(key: AyahKey): List<String> {
        if (riwayah != Riwayah.HAFS) return emptyList()
        return (similarCache ?: withContext(Dispatchers.IO) {
            asset("quran/mutashabihat.txt").lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
                val tab = row.indexOf('\t')
                if (tab < 0) return@mapNotNull null
                val src = AyahKey.parse(row.substring(0, tab).substringBefore('+')) ?: return@mapNotNull null
                src to row.substring(tab + 1).split(';')
            }.groupBy({ it.first }, { it.second }).mapValues { (_, v) -> v.flatten().distinct() }
        }.also { similarCache = it })[key].orEmpty()
    }

    private fun asset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }
}
