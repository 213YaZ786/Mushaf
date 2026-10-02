package org.mushaf.app.data.stt

/** whisper.cpp, through the app's small native bridge (src/main/cpp/stt.cpp). */
internal object Whisper {
    init {
        System.loadLibrary("mushafstt")
    }

    /** A loaded model, or 0 when the file could not be read as one. */
    external fun load(path: String): Long

    /** The Arabic heard in [audio] (16 kHz, -1..1, at most 30 s). */
    external fun transcribe(handle: Long, audio: FloatArray): String

    external fun free(handle: Long)
}
