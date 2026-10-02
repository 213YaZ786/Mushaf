package org.mushaf.app

import android.app.Activity
import android.os.Bundle
import android.util.Log
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.mushaf.app.data.stt.Recogniser

/**
 * Debug builds only: transcribes files/stt_check.pcm (16 kHz mono, 16 bit)
 * and logs what was heard and how long it took, to check the recogniser on
 * a device without a microphone.
 */
class SttCheckActivity : Activity() {
    private val recogniser: Recogniser by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CoroutineScope(Dispatchers.Main).launch {
            val bytes = File(filesDir, "stt_check.pcm").readBytes()
            val shorts = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
            val audio = FloatArray(shorts.remaining()) { shorts.get(it) / 32768f }
            val start = System.currentTimeMillis()
            val text = runCatching { recogniser.transcribe(audio) }.getOrElse { "failed: $it" }
            Log.i("STTCHECK", "$text | ${System.currentTimeMillis() - start} ms for ${audio.size / 16} ms of audio")
            finish()
        }
    }
}
