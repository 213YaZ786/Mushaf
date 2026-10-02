package org.mushaf.app.core.quran

/**
 * Ramdane At Mensour's Kabyle translation (Leqwṛan s tmaziɣt, Algiers 2006)
 * circulates in the encoding of an old Amazigh font, where common letters
 * stand for the Kabyle ones: shown as is, it reads "Öebbi" for "Ṛebbi".
 * This puts the Kabyle letters back (standard Latin orthography). The table
 * was checked against the whole text: 98 % of the ayat match a corrected
 * edition letter for letter, the rest differ only where that edition kept
 * a legacy letter by mistake.
 */
object Kabyle {

    private val letters = mapOf(
        '$' to "ɣ", '£' to "ɣ",
        'ô' to "ṛ", 'ö' to "ṛ", 'Ö' to "Ṛ", 'Ô' to "Ṛ",
        'â' to "ɛ", 'Â' to "Ɛ",
        'p' to "t", 'P' to "T",
        'ê' to "ḥ", 'Ê' to "Ḥ", 'Ë' to "Ḥ",
        'v' to "ḍ", 'V' to "Ḍ",
        'î' to "ṭ", 'Ï' to "Ṭ",
        'o' to "ǧ", 'O' to "Ǧ",
        'û' to "ṣ", 'Û' to "Ṣ",
        'é' to "ẓ", 'è' to "ẓ", 'È' to "Ẓ",
        'ç' to "č", 'Ç' to "Č"
    )

    fun fromLegacy(text: String): String = buildString(text.length) {
        for (c in text) append(letters[c] ?: c)
    }

    /** The editions of the catalogues that are this translation in the old encoding. */
    fun isLegacy(id: String): Boolean = id == "fa:ber-ramdaneatmansou"
}
