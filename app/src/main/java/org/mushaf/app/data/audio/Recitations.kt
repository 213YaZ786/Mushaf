package org.mushaf.app.data.audio

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.longOrNull
import org.mushaf.app.core.audio.AyahTime
import org.mushaf.app.core.audio.SurahAudio
import org.mushaf.app.core.audio.Timing
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.sources.Sources

/**
 * A reciter whose recordings carry the timing of each ayah (and, from
 * Quran.com, of each word). Warsh reciters are mp3quran's, their ids past
 * [Recitations.WARSH_BASE].
 */
data class Reciter(val id: Int, val name: String, val style: String, val warsh: Boolean = false)

/**
 * The recitations: Hafs from Quran.com (quranicaudio.com), one file per
 * surah with the moment each ayah and each word is heard; Warsh from
 * mp3quran.net, one file per surah with the moment each ayah is heard. The
 * page follows the voice either way. The timing of a surah is fetched once
 * and kept.
 */
class Recitations(private val context: Context, private val sources: Sources, private val settings: org.mushaf.app.data.settings.SettingsStore) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "recitations")

    suspend fun surah(reciter: Int, surah: Int): SurahAudio = withContext(Dispatchers.IO) {
        val file = File(dir, "$reciter/$surah.json")
        runCatching { json.decodeFromString<SurahAudio>(file.readText()) }.getOrNull()?.let { return@withContext it }
        if (reciter > WARSH_BASE) return@withContext warsh(reciter, surah).also { a ->
            file.parentFile?.mkdirs()
            file.writeTextAtomically(json.encodeToString(a))
        }
        val root = json.parseToJsonElement(first { "$it/audio/reciters/$reciter/audio_files?chapter=$surah&segments=true" }).jsonObject
        val f = root["audio_files"]!!.jsonArray.first().jsonObject
        val ayat = f["verse_timings"]!!.jsonArray.map { it.jsonObject }.map { v ->
            val key = v["verse_key"]!!.jsonPrimitive.content.split(':')
            val from = v["timestamp_from"]!!.jsonPrimitive.long
            val to = v["timestamp_to"]!!.jsonPrimitive.long
            val segments = (v["segments"] as? JsonArray)?.map { seg ->
                seg.jsonArray.mapNotNull { it.jsonPrimitive.longOrNull }
            }.orEmpty()
            AyahTime(key[0].toInt(), key[1].toInt(), from, to, Timing.words(segments, from, to))
        }
        val audio = SurahAudio(reciter, surah, f["audio_url"]!!.jsonPrimitive.content.also(::requireSafe), ayat)
        file.parentFile?.mkdirs()
        file.writeTextAtomically(json.encodeToString(audio))
        audio
    }

    /** A Warsh surah from mp3quran: its file in the reciter's folder, the timing of each ayah. */
    private fun warsh(reciter: Int, surah: Int): SurahAudio {
        val read = reciter - WARSH_BASE
        val base = sources.list.mp3quran.firstOrNull() ?: "https://www.mp3quran.net/api/v3"
        // The reciter's folder from mp3quran's own list, so a moved server is followed.
        val folder = runCatching {
            json.parseToJsonElement(Net.text("$base/ayat_timing/reads", maxBytes = 1L shl 20)).jsonArray
                .map { it.jsonObject }.first { it["id"]!!.jsonPrimitive.content.toInt() == read }["folder_url"]!!.jsonPrimitive.content
        }.getOrElse { WARSH_FOLDERS[read] ?: throw it }
        requireSafe(folder)
        val timings = json.parseToJsonElement(Net.text("$base/ayat_timing?surah=$surah&read=$read", maxBytes = 2L shl 20)).jsonArray
        val ayat = timings.map { it.jsonObject }.mapNotNull { t ->
            val ayah = t["ayah"]!!.jsonPrimitive.content.toInt()
            if (ayah < 1) null else AyahTime(surah, ayah, t["start_time"]!!.jsonPrimitive.long, t["end_time"]!!.jsonPrimitive.long)
        }
        return SurahAudio(reciter, surah, folder.trimEnd('/') + "/%03d.mp3".format(surah), ayat)
    }

    /** The player reaches recordings on its own: only over HTTPS and on a server the app allows. */
    private fun requireSafe(url: String) {
        val uri = java.net.URI(url)
        require(uri.scheme == "https" && Net.allowed(uri.host.orEmpty().lowercase())) { "recording refused" }
    }

    private fun first(path: (String) -> String): String {
        var last: Exception? = null
        for (base in sources.list.recitations) {
            try {
                return Net.text(path(base), maxBytes = 2L shl 20)
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: error("no source")
    }

    private val audioDir = File(context.filesDir, "audio")

    /** The surah's recording kept on the phone, if it is. */
    fun local(reciter: Int, surah: Int): File? = File(audioDir, "$reciter/$surah.mp3").takeIf { it.exists() }

    fun kept(reciter: Int): Int = File(audioDir, "$reciter").list()?.count { it.endsWith(".mp3") } ?: 0

    fun remove(reciter: Int) {
        File(audioDir, "$reciter").deleteRecursively()
    }

    /** Keeps one surah of [reciter] on the phone. */
    suspend fun downloadSurah(reciter: Int, n: Int) = withContext(Dispatchers.IO) {
        val target = File(audioDir, "$reciter/$n.mp3")
        if (!target.exists()) Net.download(surah(reciter, n).url, target, maxBytes = 300L shl 20)
    }

    /** Keeps every surah of [reciter] on the phone, with its timing; [progress] hears each surah done. */
    suspend fun download(reciter: Int, progress: suspend (Int) -> Unit) = withContext(Dispatchers.IO) {
        for (n in 1..114) {
            val a = surah(reciter, n)
            val target = File(audioDir, "$reciter/$n.mp3")
            if (!target.exists()) Net.download(a.url, target, maxBytes = 300L shl 20)
            progress(n)
        }
    }

    companion object {
        /** Quran.com's timed reciters, Alafasy first: the most heard. */
        val RECITERS = listOf(
            Reciter(7, "Mishari Rashid al-Afasy", "Murattal"),
            Reciter(6, "Mahmoud Khalil al-Husary", "Murattal"),
            Reciter(12, "Mahmoud Khalil al-Husary", "Teaching, slow"),
            Reciter(168, "Mohamed Siddiq al-Minshawi", "With a child repeating"),
            Reciter(9, "Mohamed Siddiq al-Minshawi", "Murattal"),
            Reciter(2, "AbdulBaset AbdulSamad", "Murattal"),
            Reciter(1, "AbdulBaset AbdulSamad", "Mujawwad"),
            Reciter(3, "Abdur-Rahman as-Sudais", "Murattal"),
            Reciter(10, "Sa'ud ash-Shuraim", "Murattal"),
            Reciter(4, "Abu Bakr al-Shatri", "Murattal"),
            Reciter(5, "Hani ar-Rifai", "Murattal"),
            Reciter(97, "Yasser ad-Dussary", "Murattal"),
            Reciter(161, "Khalifah al-Tunaiji", "Murattal")
        )

        /** mp3quran's ids for Warsh, past this number so they never meet Quran.com's. */
        const val WARSH_BASE = 10000

        /** mp3quran's Warsh reciters whose recordings carry each ayah's timing. */
        val WARSH = listOf(
            Reciter(WARSH_BASE + 14, "Yassin al-Jazairi", "Warsh", warsh = true),
            Reciter(WARSH_BASE + 120, "Mahmoud Khalil al-Husary", "Warsh", warsh = true),
            Reciter(WARSH_BASE + 80, "Omar al-Qazabri", "Warsh", warsh = true),
            Reciter(WARSH_BASE + 16, "Al-Ayoun al-Koshi", "Warsh", warsh = true),
            Reciter(WARSH_BASE + 134, "Mohammad Saayed", "Warsh", warsh = true)
        )

        /** Their folders as of 2026-10, if mp3quran's list cannot be read. */
        private val WARSH_FOLDERS = mapOf(
            14 to "https://server11.mp3quran.net/qari/",
            120 to "https://server13.mp3quran.net/husr/Rewayat-Warsh-A-n-Nafi/",
            80 to "https://server9.mp3quran.net/omar_warsh/",
            16 to "https://server11.mp3quran.net/koshi/",
            134 to "https://server16.mp3quran.net/m_sayed/Rewayat-Warsh-A-n-Nafi/"
        )

        fun reciter(id: Int): Reciter = (RECITERS + WARSH).firstOrNull { it.id == id } ?: RECITERS.first()

        /** The reciters of [riwayah]. */
        fun of(riwayah: org.mushaf.app.core.quran.Riwayah): List<Reciter> =
            if (riwayah == org.mushaf.app.core.quran.Riwayah.WARSH) WARSH else RECITERS
    }
}

