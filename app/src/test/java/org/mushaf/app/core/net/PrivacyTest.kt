package org.mushaf.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * What the reader says, records or films never leaves the phone. These
 * checks fail the build (and so the release) the day a change would let it.
 */
class PrivacyTest {

    private val main = File("src/main")
    private val sources = main.walkTopDown().filter { it.isFile && (it.extension == "kt" || it.extension == "cpp") }.toList()
    private val manifest = File(main, "AndroidManifest.xml").readText()

    @Test
    fun theAppAsksOnlyForWhatItNeeds() {
        val asked = Regex("""uses-permission[^>]*?android:name="([^"]+)"""").findAll(manifest).map { it.groupValues[1] }.toSet()
        assertEquals(
            setOf(
                "android.permission.INTERNET",
                "android.permission.REQUEST_INSTALL_PACKAGES",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK",
                "android.permission.FOREGROUND_SERVICE_DATA_SYNC",
                "android.permission.WAKE_LOCK",
                // Reciting from memory, heard on the phone itself.
                "android.permission.RECORD_AUDIO"
            ),
            asked
        )
    }

    @Test
    fun theMicrophoneIsNeverOpenInTheBackground() {
        // Without a microphone service, Android gives the microphone to the app only while it is on screen.
        assertTrue(Regex("""foregroundServiceType="[^"]*(microphone|camera)""").find(manifest) == null)
    }

    @Test
    fun nothingIsSent() {
        val forbidden = listOf(
            // A request that carries a body: an upload.
            "doOutput = true", "setDoOutput(true)", "\"POST\"", "\"PUT\"", "\"PATCH\"",
            // Other ways out: raw sockets, web pages, other HTTP clients.
            "java.net.Socket", "DatagramSocket", "WebView", "okhttp", "ktor",
            // Recording to a file, or the camera.
            "setOutputFile", "MediaMuxer", "CameraManager", "android.hardware.camera", "CameraX",
            // In the speech engine: no file written, no network.
            "fopen(", "fwrite(", "socket("
        )
        val found = sources.flatMap { f ->
            val text = f.readText()
            forbidden.filter { it in text }.map { "${f.path}: $it" }
        }
        assertEquals(emptyList<String>(), found)
    }

    @Test
    fun onlyTheSpeechScreenOpensTheMicrophone() {
        val users = sources.filter { "AudioRecord(" in it.readText() }.map { it.name }
        assertEquals(listOf("Recite.kt"), users)
    }
}
