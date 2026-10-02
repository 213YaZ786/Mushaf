package org.mushaf.app.core.quran

/**
 * Arabic reduced to its letters, so that what is typed or heard matches
 * what is printed: no vowels, no Quranic signs, one form of each letter.
 */
object Arabic {

    fun normalize(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            when {
                isMark(c) -> Unit
                c == 'ـ' -> Unit // tatweel
                c in "آأإٱٲٳ" -> out.append('ا') // alef forms
                c in 'ࡰ'..'ࢆ' -> out.append('ا') // the Warsh mushaf's alef forms (Arabic Extended-B)
                c == 'ى' || c == 'ی' || c == 'ے' -> out.append('ي') // alef maqsura, Farsi yeh, yeh barree
                c == 'ڢ' -> out.append('ف') // the Maghrebi feh, its dot below
                c == 'ڧ' || c == 'ٯ' -> out.append('ق') // the Maghrebi qaf, one dot above or none
                c == 'ۨ' -> out.append('ن') // a small nun written as a sign (12:110 in Warsh)
                c in "ۑࢇۥۦࣉ" -> Unit // seats and small letters that are signs, not letters
                c == 'ة' -> out.append('ه') // teh marbuta
                c == 'ؤ' -> out.append('و') // waw with hamza
                c == 'ئ' -> out.append('ي') // yeh with hamza
                c == 'ک' -> out.append('ك') // keheh
                c == 'ء' -> Unit // a lone hamza is often left out when typed or heard
                c.isWhitespace() -> if (out.isNotEmpty() && out.last() != ' ') out.append(' ')
                else -> out.append(c)
            }
        }
        return out.toString().trim()
    }

    /** The words of [text], normalised, empty ones dropped. */
    fun words(text: String): List<String> = normalize(text).split(' ').filter { it.isNotEmpty() }

    /** True when [text] holds an Arabic letter. */
    fun isArabic(text: String): Boolean = text.any { it in '؀'..'ۿ' || it in 'ݐ'..'ݿ' || it in 'ﭐ'..'﻿' }

    private fun isMark(c: Char): Boolean =
        c in 'ً'..'ٟ' || c == 'ٰ' || c in 'ۖ'..'ۭ' || c in 'ؐ'..'ؚ' ||
            c == 'ࣰ' || c == 'ࣱ' || c == 'ࣲ' || c in '࣓'..'ࣿ'

    private val digits = "٠١٢٣٤٥٦٧٨٩"

    /** A number in Arabic-Indic digits, as the mushaf prints it. */
    fun digits(n: Int): String = n.toString().map { digits[it - '0'] }.joinToString("")
}
