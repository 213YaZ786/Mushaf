package org.mushaf.app.data.quran

import java.io.RandomAccessFile
import kotlin.math.pow

/**
 * The tajweed fonts draw their silent letters in light greys (#a5a5a5,
 * #a3a5a5, #9fa5a5 in palette 0), about 2:1 against a light page. This
 * finds the grey that reaches WCAG's 4.5:1 for normal text on the page's
 * ground, and rewrites those palette entries in a copy of the font.
 */
object TajweedPalette {
    /** The lightest of the fonts' silent greys: nothing lighter is ever written. */
    const val ORIGINAL_GREY = 0xa5

    /** The darkest grey written, so silent letters stay lighter than the black ink. */
    private const val FLOOR = 0x20

    /**
     * Relative luminance of an opaque sRGB colour, WCAG 2.2:
     * https://www.w3.org/TR/WCAG22/#dfn-relative-luminance
     */
    fun luminance(argb: Int): Double {
        fun linear(v: Int): Double {
            val c = v / 255.0
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linear(argb shr 16 and 0xff) + 0.7152 * linear(argb shr 8 and 0xff) + 0.0722 * linear(argb and 0xff)
    }

    /**
     * Contrast ratio of two opaque colours, WCAG 2.2:
     * https://www.w3.org/TR/WCAG22/#dfn-contrast-ratio
     */
    fun contrast(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun grey(level: Int) = (0xff shl 24) or (level shl 16) or (level shl 8) or level

    /**
     * The lightest neutral grey (0..255) that reaches [ratio] against the page's
     * [ground], or null when the fonts' own greys already do.
     */
    fun greyFor(ground: Int, ratio: Double = 4.5): Int? {
        if (contrast(grey(ORIGINAL_GREY), ground) >= ratio) return null
        for (level in ORIGINAL_GREY - 1 downTo FLOOR) if (contrast(grey(level), ground) >= ratio) return level
        return FLOOR
    }

    /** The offset of the CPAL table, from the sfnt table directory (OpenType spec, "Organization of an OpenType font"). */
    fun cpalAt(f: RandomAccessFile): Long {
        f.seek(4)
        val tables = f.readUnsignedShort()
        for (i in 0 until tables) {
            val at = 12L + i * 16
            f.seek(at)
            val tag = ByteArray(4).also { f.readFully(it) }
            if (String(tag, Charsets.ISO_8859_1) == "CPAL") {
                f.seek(at + 8)
                return f.readInt().toLong() and 0xffffffffL
            }
        }
        error("no palette")
    }

    /**
     * Sets the silent greys of palette 0 to [level], keeping their alpha. CPAL
     * layout, https://learn.microsoft.com/en-us/typography/opentype/spec/cpal :
     * version, numPaletteEntries, numPalettes, numColorRecords (u16 each),
     * colorRecordsArrayOffset (u32, from the table's start), colorRecordIndices
     * (u16 per palette); a colour record is blue, green, red, alpha (one byte
     * each). A grey is a colour whose three channels lie within 8 of each
     * other and at least 0x80, so the black ink, the white and every colour
     * of a rule stay as they are. Returns how many entries changed.
     */
    fun greyen(f: RandomAccessFile, level: Int): Int {
        val cpal = cpalAt(f)
        f.seek(cpal + 2)
        val entries = f.readUnsignedShort()
        f.readUnsignedShort()
        val records = f.readUnsignedShort()
        val array = f.readInt().toLong() and 0xffffffffL
        val first = f.readUnsignedShort()
        var changed = 0
        for (e in 0 until entries) {
            val record = first + e
            if (record >= records) break
            val at = cpal + array + 4L * record
            f.seek(at)
            val c = ByteArray(4).also { f.readFully(it) }
            val b = c[0].toInt() and 0xff
            val g = c[1].toInt() and 0xff
            val r = c[2].toInt() and 0xff
            if (maxOf(r, g, b) - minOf(r, g, b) <= 8 && minOf(r, g, b) >= 0x80 && maxOf(r, g, b) > level) {
                f.seek(at)
                f.write(byteArrayOf(level.toByte(), level.toByte(), level.toByte()))
                changed++
            }
        }
        return changed
    }
}
