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

    fun local(english: String): String {
        val plain = english.substringBefore(" (").trim().lowercase()
        val code = byEnglish[plain] ?: (if (plain == "kabyle") "kab" else null) ?: return english
        val here = Locale.getDefault()
        return Locale.forLanguageTag(code).getDisplayLanguage(here).replaceFirstChar { it.titlecase(here) }.ifEmpty { english }
    }
}
