package org.mushaf.app.feature.recite

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mushaf.app.core.stt.Heard

import org.mushaf.app.core.stt.Match
import org.mushaf.app.data.stt.Recogniser

data class ReciteState(
    val listening: Boolean = false,
    /** Each expected word heard so far, by its place in the list given to start. */
    val marks: Map<Int, Heard> = emptyMap(),
    /** The next word expected. */
    val next: Int = 0,
    val failed: Boolean = false
)

/**
 * Listens while the reader recites from memory and follows the text: the
 * microphone is read on the phone, the last seconds are heard again every
 * moment, and what was heard is laid along the words expected. A long
 * recitation is taken in pieces of about 20 seconds, each starting where
 * the last one stopped. Nothing is recorded to a file, nothing is sent.
 */
class Recite(private val recogniser: Recogniser, private val scope: CoroutineScope) {

    private val _state = MutableStateFlow(ReciteState())
    val state: StateFlow<ReciteState> = _state.asStateFlow()

    private var job: Job? = null

    @SuppressLint("MissingPermission") // asked by the screen before start
    fun start(expected: List<String>) {
        stop()
        _state.value = ReciteState(listening = true)
        job = scope.launch(Dispatchers.IO) {
            val size = maxOf(AudioRecord.getMinBufferSize(Recogniser.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), Recogniser.RATE)
            val record = runCatching {
                AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, Recogniser.RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, size)
            }.getOrNull()
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                _state.update { it.copy(listening = false, failed = true) }
                return@launch
            }
            val piece = FloatArray(Recogniser.RATE * 22)
            var filled = 0
            var base = 0
            var heardUpTo = 0
            val chunk = ShortArray(Recogniser.RATE / 10)
            var lastRun = System.currentTimeMillis()
            record.startRecording()
            try {
                while (isActive) {
                    val n = record.read(chunk, 0, chunk.size)
                    if (n <= 0) continue
                    for (i in 0 until n) if (filled < piece.size) piece[filled++] = chunk[i] / 32768f
                    val now = System.currentTimeMillis()
                    val full = filled >= piece.size
                    if ((now - lastRun > 1500 && filled - heardUpTo > Recogniser.RATE / 2) || full) {
                        lastRun = now
                        heardUpTo = filled
                        if (loud(piece, filled)) {
                            val text = runCatching { recogniser.transcribe(piece.copyOf(filled)) }.getOrDefault("")
                            val result = Match.follow(expected, base, text.split(' ').filter { it.isNotBlank() })
                            _state.update { s -> s.copy(marks = s.marks + result.marks, next = maxOf(s.next, result.next)) }
                            if (result.next >= expected.size) break
                            if (full) {
                                // The next piece starts where this one was followed to.
                                base = result.next
                                filled = 0
                                heardUpTo = 0
                            }
                        } else if (full) {
                            filled = 0
                            heardUpTo = 0
                        }
                    }
                }
            } finally {
                record.stop()
                record.release()
                // The sound heard was only ever in memory; nothing of it is kept.
                piece.fill(0f)
                chunk.fill(0)
                withContext(Dispatchers.Main) { _state.update { it.copy(listening = false) } }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(listening = false) }
    }

    fun reset() {
        stop()
        _state.value = ReciteState()
    }

    /** Some voice in what was taken, not only the room. */
    private fun loud(audio: FloatArray, n: Int): Boolean {
        var sum = 0.0
        for (i in 0 until n) sum += audio[i] * audio[i]
        return n > 0 && sqrt(sum / n) > 0.006
    }

}
