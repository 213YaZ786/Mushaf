package org.mushaf.app.data.stt

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.sources.Sources

/**
 * Hears the Quran recited, on the phone: Tarteel's Whisper model trained
 * on Quran recitation (Apache-2.0), run by whisper.cpp. Nothing heard
 * leaves the phone. The model (about 80 MB) is fetched once when the
 * reader first wants it, and kept only if its SHA-256 is the one recorded
 * in the app (assets/stt.sha256).
 */
class Recogniser(private val context: Context, private val sources: Sources) {

    private val dir = File(context.filesDir, "stt")
    private val lock = Mutex()
    private var handle = 0L

    private val expected: Pair<String, String> by lazy {
        val p = context.assets.open("stt.sha256").bufferedReader().readLine().trim().split(Regex("\\s+"))
        p[1] to p[0]
    }

    private val file: File get() = File(dir, expected.first)

    // After the file's name is known.
    private val _ready = MutableStateFlow(installed())
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    fun installed(): Boolean = file.exists()

    /** Fetches the model; [progress] hears the bytes as they come. */
    suspend fun install(progress: (Long) -> Unit = {}) = withContext(Dispatchers.IO) {
        if (installed()) return@withContext
        val (name, sha) = expected
        var last: Exception? = null
        for (template in sources.list.stt) {
            try {
                Net.download(template.replace("{file}", name), file, maxBytes = 200L shl 20, sha256 = sha, progress = progress)
                _ready.value = true
                return@withContext
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: error("no source")
    }

    suspend fun remove() = lock.withLock {
        if (handle != 0L) Whisper.free(handle)
        handle = 0L
        dir.deleteRecursively()
        _ready.value = false
    }

    /** What is recited in [audio] (16 kHz, -1..1, at most 30 s), with vowels. */
    suspend fun transcribe(audio: FloatArray): String = lock.withLock {
        withContext(Dispatchers.Default) {
            if (handle == 0L) handle = Whisper.load(file.path)
            if (handle == 0L) error("model not readable")
            Whisper.transcribe(handle, audio).trim()
        }
    }

    companion object {
        const val RATE = 16_000
    }
}
