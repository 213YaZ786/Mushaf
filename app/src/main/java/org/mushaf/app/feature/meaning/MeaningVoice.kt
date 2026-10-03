package org.mushaf.app.feature.meaning

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the phone's voice is doing with the meanings. */
data class VoiceState(
    /** The meaning being read aloud, by its row and translation. */
    val speaking: String? = null,
    /** The language asked for that the phone has no voice for (its tag), or "" when it has no voice at all. */
    val missing: String? = null
)

/**
 * Reads a translation aloud with the phone's own text-to-speech, in the
 * translation's language: only the meaning, never the Quran's Arabic, which
 * is recited, not read by a machine. The engine is the one the reader
 * chose in Android (Google's, or another installed); it starts on the first
 * use and is let go when the screen closes.
 */
class MeaningVoice(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var ready = false
    private var pending: (() -> Unit)? = null
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    /** Reads [text] in [language] (a tag: "fr", "kab"); [id] names what is read, to show it lit. */
    fun speak(id: String, text: String, language: String?) {
        val go = {
            val t = tts
            if (t != null) {
                val locale = language?.let { Locale.forLanguageTag(it) } ?: Locale.getDefault()
                val fit = t.isLanguageAvailable(locale)
                if (fit == TextToSpeech.LANG_MISSING_DATA || fit == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _state.value = VoiceState(missing = locale.toLanguageTag())
                } else {
                    t.language = locale
                    _state.value = VoiceState(speaking = id)
                    t.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
                }
            }
        }
        if (ready) go() else {
            pending = go
            if (tts == null) tts = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    ready = true
                    tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) = Unit
                        override fun onDone(utteranceId: String?) = finished(utteranceId)
                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) = finished(utteranceId)
                        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
                    })
                    pending?.invoke()
                } else {
                    // No text-to-speech on the phone at all.
                    _state.value = VoiceState(missing = "")
                    release()
                }
                pending = null
            }
        }
    }

    private fun finished(id: String?) {
        _state.update { if (it.speaking == id) it.copy(speaking = null) else it }
    }

    fun stop() {
        tts?.stop()
        _state.update { it.copy(speaking = null) }
    }

    fun dismiss() {
        _state.update { it.copy(missing = null) }
    }

    /** Lets the engine go: nothing stays awake once the meanings are closed. */
    fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        ready = false
        _state.update { it.copy(speaking = null) }
    }

    companion object {
        /** Android's screen where voices are chosen and installed. */
        fun settings(): Intent = Intent("com.android.settings.TTS_SETTINGS").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
