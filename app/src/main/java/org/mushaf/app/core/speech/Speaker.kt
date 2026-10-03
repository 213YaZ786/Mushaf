package org.mushaf.app.core.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The phone's own text-to-speech, for the recitation's "meaning after each
 * ayah": [say] speaks and returns once done. It starts on the first use and
 * is let go by [release] when the recitation stops.
 */
class Speaker(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val ids = AtomicInteger()
    private val waiting = mutableMapOf<String, (Boolean) -> Unit>()

    private suspend fun engine(): TextToSpeech? {
        tts?.takeIf { ready }?.let { return it }
        return suspendCancellableCoroutine { cont ->
            var made: TextToSpeech? = null
            made = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts = made
                    made?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = done(utteranceId, true)
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = done(utteranceId, false)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = done(utteranceId, false)
                    })
                    if (cont.isActive) cont.resume(made)
                } else {
                    made?.shutdown()
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun done(id: String?, ok: Boolean) {
        val call = synchronized(waiting) { waiting.remove(id) } ?: return
        call(ok)
    }

    /** Speaks [text] in [language] (a tag) and returns when it is over; false when no voice could say it. */
    suspend fun say(text: String, language: String?): Boolean {
        val t = engine() ?: return false
        val locale = language?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
        val fit = t.isLanguageAvailable(locale)
        if (fit == TextToSpeech.LANG_MISSING_DATA || fit == TextToSpeech.LANG_NOT_SUPPORTED) return false
        t.language = locale
        val id = "m" + ids.incrementAndGet()
        return suspendCancellableCoroutine { cont ->
            synchronized(waiting) { waiting[id] = { ok -> if (cont.isActive) cont.resume(ok) } }
            cont.invokeOnCancellation { t.stop(); synchronized(waiting) { waiting.remove(id) } }
            if (t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) != TextToSpeech.SUCCESS) done(id, false)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
    }
}
