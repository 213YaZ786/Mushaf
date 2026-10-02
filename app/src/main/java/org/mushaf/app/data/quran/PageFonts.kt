package org.mushaf.app.data.quran

import android.content.Context
import android.os.Build
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable

/** How the mushaf's pages are drawn. */
@Serializable
enum class Script {
    /** The printed Madinah mushaf, each page in its own font, fetched once. */
    PRINT,
    /** The same pages with the tajweed rules in colour (Android 13 and later). */
    TAJWEED,
    /** The King Fahd Complex's Hafs font, in the app: no download, lines as printed. */
    HAFS;

    /** Tajweed colours need COLRv1 fonts, which Android draws from 13 on. */
    val usable: Boolean get() = this != TAJWEED || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}

/**
 * The print fonts of the Madinah mushaf (King Fahd Complex, served by
 * Quran.com): one font per page, about 300 KB, fetched the first time the
 * page is shown and kept. Until a page's font is here, the page is drawn
 * in the Hafs font the app carries, so reading never waits.
 */
class PageFonts(private val context: Context, private val scope: CoroutineScope) {

    private val families = ConcurrentHashMap<String, FontFamily>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(3)

    /** Bumped when a font arrives, so the pages waiting for it are drawn again. */
    private val _arrived = MutableStateFlow(0)
    val arrived: StateFlow<Int> = _arrived.asStateFlow()

    private fun file(script: Script, page: Int) = File(context.filesDir, "fonts/${script.name.lowercase()}/p$page.ttf")

    private fun url(script: Script, page: Int) = when (script) {
        Script.TAJWEED -> "https://verses.quran.foundation/fonts/quran/hafs/v4/colrv1/ttf/p$page.ttf"
        else -> "https://static.qurancdn.com/fonts/quran/hafs/v2/ttf/p$page.ttf"
    }

    /** The page's font if it is here; otherwise asks for it and returns null. */
    fun family(script: Script, page: Int): FontFamily? {
        if (script == Script.HAFS) return null
        val key = "${script.name}/$page"
        families[key]?.let { return it }
        val f = file(script, page)
        if (f.exists() && f.length() > 0) {
            runCatching { muteHdmx(f) }
            return runCatching { FontFamily(Font(f)) }.getOrNull()?.also { families[key] = it }
                ?: run { f.delete(); null }
        }
        fetch(script, page)
        return null
    }

    /** Fetches the fonts of [pages] ahead of time, the ones next to the page being read. */
    fun prefetch(script: Script, pages: Iterable<Int>) {
        if (script == Script.HAFS) return
        pages.filter { it in 1..604 }.forEach { if (!file(script, it).exists()) fetch(script, it) }
    }

    /** How many pages of [script] are kept on the phone. */
    fun count(script: Script): Int = File(context.filesDir, "fonts/${script.name.lowercase()}").list()?.size ?: 0

    private fun fetch(script: Script, page: Int) {
        val key = "${script.name}/$page"
        if (!running.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                gate.withPermit { download(url(script, page), file(script, page)) }
                _arrived.update { it + 1 }
            } catch (_: Exception) {
                // Offline or refused: the page stays in the Hafs font, and
                // is asked again the next time it is shown.
            } finally {
                running.remove(key)
            }
        }
    }

    /**
     * The print fonts carry an hdmx table (advances rounded for each size)
     * that is wrong for some glyphs: Android then measures a long word a few
     * pixels wide and the next word is drawn over it (page 1, line 7). The
     * table is renamed so Android ignores it and takes the true advances; the
     * new tag sorts where the old one did, so the table directory stays in order.
     */
    private fun muteHdmx(font: File) {
        RandomAccessFile(font, "rw").use { f ->
            f.seek(4)
            val tables = f.readUnsignedShort()
            for (i in 0 until tables) {
                val at = 12L + i * 16
                f.seek(at)
                val tag = ByteArray(4).also { f.readFully(it) }
                if (String(tag, Charsets.ISO_8859_1) == "hdmx") {
                    f.seek(at)
                    f.write("hdmw".toByteArray(Charsets.ISO_8859_1))
                    return
                }
            }
        }
    }

    private fun download(url: String, target: File) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mushaf")
            if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
            conn.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            // A font is at least a few KB; anything less is an error page.
            if (part.length() < 4_000) error("short font")
            muteHdmx(part)
            if (!part.renameTo(target)) error("rename")
        } finally {
            conn.disconnect()
            part.delete()
        }
    }
}
