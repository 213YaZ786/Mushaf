package org.mushaf.app.data.quran

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.RandomAccessFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TajweedPaletteTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val white = 0xffffffff.toInt()

    // WCAG 2.2: black on white is 21:1, a colour on itself 1:1; #767676 on white
    // (4.54:1) is the lightest grey that passes 4.5:1 and #777777 (4.48:1) fails.
    @Test
    fun contrastOfKnownPairs() {
        assertEquals(21.0, TajweedPalette.contrast(0xff000000.toInt(), white), 0.001)
        assertEquals(1.0, TajweedPalette.contrast(white, white), 0.001)
        assertEquals(4.54, TajweedPalette.contrast(0xff767676.toInt(), white), 0.01)
        assertEquals(4.48, TajweedPalette.contrast(0xff777777.toInt(), white), 0.01)
        assertEquals(1.0, TajweedPalette.luminance(white), 0.0001)
        assertEquals(0.0, TajweedPalette.luminance(0xff000000.toInt()), 0.0001)
    }

    @Test
    fun theFontsGreysAreTooLightOnALightPageAndFixed() {
        assertTrue(TajweedPalette.contrast(0xffa5a5a5.toInt(), white) < 3.0)
        assertEquals(0x76, TajweedPalette.greyFor(white))
        val lavender = 0xffd9d3f5.toInt()
        val level = TajweedPalette.greyFor(lavender)!!
        fun grey(v: Int) = 0xff000000.toInt() or (v shl 16) or (v shl 8) or v
        assertTrue(TajweedPalette.contrast(grey(level), lavender) >= 4.5)
        assertTrue(TajweedPalette.contrast(grey(level + 1), lavender) < 4.5)
        assertTrue(level in 1 until TajweedPalette.ORIGINAL_GREY)
        // A ground the fonts' own greys already suit keeps the font as it is.
        assertNull(TajweedPalette.greyFor(0xff000000.toInt()))
    }

    /** A font reduced to its table directory and a CPAL of two palettes of four entries. */
    private fun font(): java.io.File {
        val palette0 = listOf(
            intArrayOf(0x00, 0x00, 0x00, 0xff), // black ink
            intArrayOf(0xa5, 0xa5, 0xa5, 0xff), // silent grey
            intArrayOf(0xa5, 0xa5, 0x9f, 0xff), // silent grey, slightly tinted (blue, green, red)
            intArrayOf(0x00, 0x00, 0xb5, 0xff)  // a rule's red
        )
        val palette1 = listOf(
            intArrayOf(0xff, 0xff, 0xff, 0xff),
            intArrayOf(0x99, 0x99, 0x9d, 0xff),
            intArrayOf(0x99, 0x99, 0x99, 0xff),
            intArrayOf(0x00, 0x00, 0xe3, 0xff)
        )
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).apply {
            writeInt(0x00010000); writeShort(1); writeShort(16); writeShort(0); writeShort(0)
            writeBytes("CPAL"); writeInt(0); writeInt(28); writeInt(0)
            // CPAL v0 header, https://learn.microsoft.com/en-us/typography/opentype/spec/cpal
            writeShort(0); writeShort(4); writeShort(2); writeShort(8); writeInt(16)
            writeShort(0); writeShort(4)
            for (c in palette0 + palette1) c.forEach { writeByte(it) }
        }
        return folder.newFile("font.ttf").apply { writeBytes(bytes.toByteArray()) }
    }

    private fun entry(f: RandomAccessFile, record: Int): List<Int> {
        f.seek(28L + 16 + 4L * record)
        return ByteArray(4).also { f.readFully(it) }.map { it.toInt() and 0xff }
    }

    @Test
    fun onlyTheGreysOfPaletteZeroAreRewritten() {
        RandomAccessFile(font(), "rw").use { f ->
            assertEquals(2, TajweedPalette.greyen(f, 0x65))
            assertEquals(listOf(0, 0, 0, 0xff), entry(f, 0))
            assertEquals(listOf(0x65, 0x65, 0x65, 0xff), entry(f, 1))
            assertEquals(listOf(0x65, 0x65, 0x65, 0xff), entry(f, 2))
            assertEquals(listOf(0, 0, 0xb5, 0xff), entry(f, 3))
            // Palette 1 (night) is untouched.
            assertEquals(listOf(0xff, 0xff, 0xff, 0xff), entry(f, 4))
            assertEquals(listOf(0x99, 0x99, 0x9d, 0xff), entry(f, 5))
            assertEquals(listOf(0x99, 0x99, 0x99, 0xff), entry(f, 6))
            // Run again at the same level: nothing left to change.
            assertEquals(0, TajweedPalette.greyen(f, 0x65))
        }
    }
}
