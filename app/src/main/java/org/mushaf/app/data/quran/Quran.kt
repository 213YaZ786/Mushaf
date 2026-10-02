package org.mushaf.app.data.quran

import android.content.Context
import android.util.LruCache
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
import org.mushaf.app.core.quran.Surah

/**
 * The Quran as bundled in the app (assets/quran, built by tools/quran_data.py):
 * the surahs and divisions, each page's words, every ayah's plain text and
 * the English translation. Read from the assets when first asked, then kept.
 */
class Quran(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val pages = LruCache<Int, MushafPage>(16)
    private val lock = Mutex()

    @Volatile private var metaCache: QuranMeta? = null
    @Volatile private var ayatCache: List<Ayah>? = null
    @Volatile private var indexCache: Map<AyahKey, Ayah>? = null
    @Volatile private var englishCache: Map<AyahKey, String>? = null

    suspend fun meta(): QuranMeta = metaCache ?: lock.withLock {
        metaCache ?: withContext(Dispatchers.IO) {
            json.decodeFromString<QuranMeta>(asset("quran/meta.json"))
        }.also { metaCache = it }
    }

    /** The meta when already read, for drawing without waiting. */
    val metaNow: QuranMeta? get() = metaCache

    suspend fun surahs(): List<Surah> = meta().surahs

    suspend fun surah(n: Int): Surah = meta().surahs[n - 1]

    suspend fun page(number: Int): MushafPage {
        val n = number.coerceIn(1, PAGES)
        pages.get(n)?.let { return it }
        return withContext(Dispatchers.IO) {
            val words = asset("quran/pages/%03d.txt".format(n)).lineSequence()
                .filter { it.isNotBlank() }
                .mapNotNull(PageLayout::parseWord)
                .toList()
            // The next page's first word, for a surah title printed at the foot of this one.
            val next = if (n < PAGES) asset("quran/pages/%03d.txt".format(n + 1)).lineSequence().firstOrNull()?.let(PageLayout::parseWord) else null
            PageLayout.page(n, words, next).also { pages.put(n, it) }
        }
    }

    /** Every ayah in order, with its page and divisions. */
    suspend fun ayat(): List<Ayah> = ayatCache ?: lock.withLock {
        ayatCache ?: withContext(Dispatchers.IO) {
            asset("quran/ayat.txt").lineSequence().filter { it.isNotBlank() }.mapNotNull { row ->
                val f = row.split('\t')
                val key = AyahKey.parse(f[0]) ?: return@mapNotNull null
                Ayah(key, f[1].toInt(), f[2].toInt(), f[3].toInt(), f.getOrElse(4) { "" })
            }.toList()
        }.also { list ->
            ayatCache = list
            indexCache = list.associateBy { it.key }
        }
    }

    suspend fun ayah(key: AyahKey): Ayah? {
        ayat()
        return indexCache?.get(key)
    }

    suspend fun pageOf(key: AyahKey): Int = ayah(key)?.page ?: 1

    /** The first ayah printed on [page]. */
    suspend fun firstAyah(page: Int): AyahKey =
        AyahKey.parse(meta().pageStart[page.coerceIn(1, PAGES) - 1]) ?: AyahKey(1, 1)

    /** The English meaning of [key] (Saheeh International). */
    suspend fun english(key: AyahKey): String = englishMap()[key].orEmpty()

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

    private fun asset(path: String): String = context.assets.open(path).bufferedReader().use { it.readText() }
}
