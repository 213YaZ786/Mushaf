package org.mushaf.app.core.quran

import java.util.Locale

import kotlinx.serialization.Serializable

/** An ayah's place: surah 1 to 114, ayah from 1. */
@Serializable
data class AyahKey(val surah: Int, val ayah: Int) : Comparable<AyahKey> {
    override fun compareTo(other: AyahKey): Int =
        if (surah != other.surah) surah.compareTo(other.surah) else ayah.compareTo(other.ayah)

    override fun toString() = "$surah:$ayah"

    companion object {
        /** "2:255" or "2:255:3" (the word is dropped); null when it is not a key. */
        fun parse(text: String): AyahKey? {
            val parts = text.trim().split(':')
            if (parts.size < 2) return null
            val s = parts[0].toIntOrNull() ?: return null
            val a = parts[1].toIntOrNull() ?: return null
            return if (s in 1..114 && a >= 1) AyahKey(s, a) else null
        }
    }
}

/**
 * One word of the mushaf as printed: its line on the page, its place in
 * the ayah, and the ways to show it. [end] marks the ayah's number sign,
 * not a word.
 */
data class Word(
    val line: Int,
    val key: AyahKey,
    val position: Int,
    val end: Boolean,
    /** The word in the King Fahd Complex's Hafs script, for the Hafs font. */
    val text: String,
    /** The word's glyph in the page's own print font. */
    val glyph: String,
    /** Without vowels or signs, for search and games. */
    val plain: String,
    val meaning: String,
    val transliteration: String
)

/** A line of a page: a surah's title, the basmala, or words. */
sealed interface PageLine {
    val number: Int

    data class Title(override val number: Int, val surah: Int) : PageLine
    data class Basmala(override val number: Int) : PageLine
    data class Words(override val number: Int, val words: List<Word>) : PageLine
}

/** A page of the Madinah mushaf as printed, 15 lines (8 on the first two). */
data class MushafPage(val number: Int, val lines: List<PageLine>) {
    val words: List<Word> get() = lines.flatMap { (it as? PageLine.Words)?.words.orEmpty() }
    val ayat: List<AyahKey> get() = words.map { it.key }.filter { it.ayah > 0 }.distinct()
}

/** Interface languages written in the Arabic script: they show a surah's Arabic name. */
private val ARABIC_SCRIPT = setOf("ar", "fa", "ur")

@Serializable
data class Surah(
    val n: Int,
    val name: String,
    val arabic: String,
    val meaning: String,
    val ayat: Int,
    val place: String,
    val order: Int,
    val pages: List<Int>,
    val basmala: Boolean
) {
    val firstPage: Int get() = pages.first()
    /** The name as the reader's language says it: in Arabic, its own. */
    val title: String get() = if (Locale.getDefault().language in ARABIC_SCRIPT) arabic else name
    /** The English meaning of the name, shown only when the app speaks English. */
    val meaningHere: String? get() = meaning.takeIf { Locale.getDefault().language == "en" }
    val meccan: Boolean get() = place == "makkah"
}

/** Where a juz or a quarter of a hizb starts. */
@Serializable
data class Start(val n: Int, val key: String, val page: Int) {
    val ayah: AyahKey get() = AyahKey.parse(key)!!
}

@Serializable
data class QuranMeta(
    val surahs: List<Surah>,
    val juz: List<Start>,
    /** 240 quarters: hizb (n - 1) / 4 + 1, quarter (n - 1) % 4 + 1. */
    val quarters: List<Start>,
    val sajdah: List<String>,
    /** The first ayah of each page, page 1 first. */
    val pageStart: List<String>
)

/** One ayah with its place in the divisions, and its plain text for search and games. */
data class Ayah(val key: AyahKey, val page: Int, val juz: Int, val quarter: Int, val plain: String) {
    val hizb: Int get() = (quarter - 1) / 4 + 1
}

const val PAGES = 604
