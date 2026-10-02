package org.mushaf.app.data.audio

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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

/** A reciter whose recordings carry the timing of each ayah and word. */
data class Reciter(val id: Int, val name: String, val style: String) {
    val label: String get() = if (style.isBlank() || style == "Murattal") name else "$name · $style"
}

/**
 * The recitations of Quran.com (quranicaudio.com): one file per surah,
 * with the moment each ayah and each word is heard, so the page follows
 * the voice. The timing of a surah is fetched once and kept.
 */
class Recitations(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val dir = File(context.filesDir, "recitations")

    suspend fun surah(reciter: Int, surah: Int): SurahAudio = withContext(Dispatchers.IO) {
        val file = File(dir, "$reciter/$surah.json")
        runCatching { json.decodeFromString<SurahAudio>(file.readText()) }.getOrNull()?.let { return@withContext it }
        val root = json.parseToJsonElement(get("https://api.quran.com/api/qdc/audio/reciters/$reciter/audio_files?chapter=$surah&segments=true")).jsonObject
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
        val audio = SurahAudio(reciter, surah, f["audio_url"]!!.jsonPrimitive.content, ayat)
        file.parentFile?.mkdirs()
        file.writeTextAtomically(json.encodeToString(audio))
        audio
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

        fun reciter(id: Int): Reciter = RECITERS.firstOrNull { it.id == id } ?: RECITERS.first()
    }
}

