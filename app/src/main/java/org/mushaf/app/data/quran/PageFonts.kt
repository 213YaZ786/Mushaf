package org.mushaf.app.data.quran

import android.content.Context
import android.os.Build
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.io.RandomAccessFile
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
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.sources.Sources

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
 * Quran.com): one font per page, about 300 KB. Each is fetched once, from
 * the first server of the sources list that answers, and kept only if its
 * SHA-256 is the one recorded when the app was built (assets/quran/
 * fonts.sha256): a font altered on the way or on a server is refused, and
 * the page stays in the Hafs font the app carries, so reading never waits.
 */
class PageFonts(private val context: Context, private val sources: Sources, private val scope: CoroutineScope) {

    private val families = ConcurrentHashMap<String, FontFamily>()
    private val running = ConcurrentHashMap.newKeySet<String>()
    private val gate = Semaphore(3)

    private val hashes: Map<String, String> by lazy {
        context.assets.open("quran/fonts.sha256").bufferedReader().useLines { lines ->
            lines.mapNotNull { line ->
                val p = line.trim().split(' ')
                if (p.size == 3) "${p[0]} ${p[1]}" to p[2] else null
            }.toMap()
        }
    }

    /** Bumped when a font arrives, so the pages waiting for it are drawn again. */
    private val _arrived = MutableStateFlow(0)
    val arrived: StateFlow<Int> = _arrived.asStateFlow()

    private fun dir(script: Script) = File(context.filesDir, "fonts/${script.name.lowercase()}")

    private fun file(script: Script, page: Int) = File(dir(script), "p$page.ttf")

    fun has(script: Script, page: Int) = file(script, page).exists()

    /**
     * The page's font if it is here; otherwise asks for it and returns null.
     * On a dark page the tajweed font draws with its own dark palette.
     */
    fun family(script: Script, page: Int, dark: Boolean = false): FontFamily? {
        if (script == Script.HAFS) return null
        val night = dark && script == Script.TAJWEED
        val key = "${script.name}/$page" + if (night) "/dark" else ""
        families[key]?.let { return it }
        val f = file(script, page)
        if (f.exists() && f.length() > 0) {
            val use = if (night) runCatching { darkCopy(f, page) }.getOrNull() ?: f else f
            return runCatching { FontFamily(Font(use)) }.getOrNull()?.also { families[key] = it }
                ?: run { f.delete(); null }
        }
        fetch(script, page)
        return null
    }

    /** Fetches the fonts of [pages] ahead of time, the ones next to the page being read. */
    fun prefetch(script: Script, pages: Iterable<Int>) {
        if (script == Script.HAFS) return
        pages.filter { it in 1..604 }.forEach { if (!has(script, it)) fetch(script, it) }
    }

    /** How many pages of [script] are kept on the phone. */
    fun count(script: Script): Int = dir(script).list()?.count { it.endsWith(".ttf") } ?: 0

    fun remove(script: Script) {
        dir(script).deleteRecursively()
        families.keys.removeAll { it.startsWith(script.name + "/") }
        _arrived.update { it + 1 }
    }

    private fun fetch(script: Script, page: Int) {
        val key = "${script.name}/$page"
        if (!running.add(key)) return
        scope.launch(Dispatchers.IO) {
            try {
                gate.withPermit { get(script, page) }
            } catch (_: Exception) {
                // Offline or refused: the page stays in the Hafs font, and
                // is asked again the next time it is shown.
            } finally {
                running.remove(key)
            }
        }
    }

    /** Fetches one page's font now, from the first source that gives the right file. */
    suspend fun get(script: Script, page: Int) = withContext(Dispatchers.IO) {
        if (has(script, page)) return@withContext
        val name = if (script == Script.TAJWEED) "tajweed" else "print"
        val sha = sources.list.fontHashes["$name $page"] ?: hashes["$name $page"] ?: error("no fingerprint for $name $page")
        val places = if (script == Script.TAJWEED) sources.list.tajweedFont else sources.list.printFont
        var last: Exception? = null
        for (template in places) {
            try {
                val target = file(script, page)
                Net.download(template.replace("{page}", page.toString()), target, maxBytes = 4L shl 20, sha256 = sha)
                muteHdmx(target)
                _arrived.update { it + 1 }
                return@withContext
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: error("no source")
    }

    /**
     * The tajweed fonts paint the text with the first colour of their
     * palette, black, which a dark page would hide. They carry a palette for
     * dark pages too (white text, the rules' colours lightened, the second
     * one), which Android never chooses: a copy of the font is made whose
     * first palette points to the second one's colours. Checked fonts only:
     * the copy is made from the file whose fingerprint was checked.
     */
    private fun darkCopy(font: File, page: Int): File {
        val copy = File(dir(Script.TAJWEED), "dark/p$page.ttf")
        if (copy.exists() && copy.length() == font.length()) return copy
        copy.parentFile?.mkdirs()
        val part = File(copy.path + ".part")
        font.copyTo(part, overwrite = true)
        RandomAccessFile(part, "rw").use { f ->
            f.seek(4)
            val tables = f.readUnsignedShort()
            var cpal = -1L
            for (i in 0 until tables) {
                val at = 12L + i * 16
                f.seek(at)
                val tag = ByteArray(4).also { f.readFully(it) }
                if (String(tag, Charsets.ISO_8859_1) == "CPAL") {
                    f.seek(at + 8)
                    cpal = f.readInt().toLong() and 0xffffffffL
                    break
                }
            }
            require(cpal > 0) { "no palette" }
            f.seek(cpal + 4)
            require(f.readUnsignedShort() >= 2) { "no dark palette" }
            // colorRecordIndices: where each palette's colours start.
            f.seek(cpal + 14)
            val second = f.readUnsignedShort()
            f.seek(cpal + 12)
            f.writeShort(second)
        }
        if (!part.renameTo(copy)) error("not kept")
        return copy
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
}
