package org.mushaf.app.core.common

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * File names, addresses and the surah-name glyph codes ("017") are written
 * with Latin digits whatever the phone's language: in Bengali or Persian a
 * plain format writes ০১৭ or ۰۱۷, and the page, the recording or the glyph
 * is not found.
 */
class LatinDigitsTest {

    @Test
    fun paddedNumbersAreFormattedWithLatinDigits() {
        val plain = Regex("""%0\d+d[^"]*"\.format\((?!Locale\.ROOT)""")
        val found = File("src/main").walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().withIndex().filter { plain.containsMatchIn(it.value) }.map { "${f.name}:${it.index + 1}" }
        }.toList()
        assertTrue("Format with Locale.ROOT: $found", found.isEmpty())
    }
}
