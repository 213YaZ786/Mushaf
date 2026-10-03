package org.mushaf.app.data.sources

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The list of sources shipped is the one signed: a change not signed again fails the build. */
class SourcesSignatureTest {

    private val list = File("src/main/assets/sources.json").readBytes()
    private val signature = File("src/main/assets/sources.json.sig").readText().trim()

    @Test
    fun theListIsSigned() {
        assertTrue(Sources.signed(list, signature))
    }

    @Test
    fun aChangedListIsRefused() {
        val changed = String(list).replace("https://", "https://evil.").toByteArray()
        assertFalse(Sources.signed(changed, signature))
    }

    @Test
    fun noSignatureIsRefused() {
        assertFalse(Sources.signed(list, ""))
        assertFalse(Sources.signed(list, "bm90IGEgc2lnbmF0dXJl"))
    }
}
