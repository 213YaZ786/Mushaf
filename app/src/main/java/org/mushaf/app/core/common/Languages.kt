package org.mushaf.app.core.common

import java.util.Locale

/**
 * A language named in English by a catalogue ("French", "Kabyle (Taqbaylit)")
 * as the phone names it in its own language; the English name when no
 * language of the phone answers to it.
 */
object Languages {

    private val byEnglish: Map<String, String> by lazy {
        Locale.getAvailableLocales()
            .filter { it.language.isNotEmpty() }
            .associate { it.getDisplayLanguage(Locale.ENGLISH).lowercase() to it.language }
    }

    /** The language's code ("fr", "kab") from its English name, or null. */
    fun code(english: String): String? {
        val plain = english.substringBefore(" (").trim().lowercase()
        return byEnglish[plain] ?: (if (plain == "kabyle") "kab" else null)
    }

    fun local(english: String): String {
        val code = code(english) ?: return english
        val here = Locale.getDefault()
        return Locale.forLanguageTag(code).getDisplayLanguage(here).replaceFirstChar { it.titlecase(here) }.ifEmpty { english }
    }
}
