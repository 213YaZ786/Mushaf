package org.mushaf.app.core.quran

import kotlinx.serialization.Serializable

/** The transmission the mushaf is read in. */
@Serializable
enum class Riwayah(val folder: String, val label: String, val arabic: String) {
    /** Ḥafṣ ʿan ʿĀṣim, the Kufan count (6236 ayat). */
    HAFS("quran", "Hafs ʿan ʿAsim", "حفص عن عاصم"),
    /** Warsh ʿan Nāfiʿ, the last Madani count (6214 ayat), read across the Maghreb. */
    WARSH("warsh", "Warsh ʿan Nafiʿ", "ورش عن نافع");

    /** The reader's own file [name] for this riwayah: Hafs keeps the first name the app used. */
    fun file(name: String): String = if (this == HAFS) "$name.json" else "$name-$folder.json"

    companion object {
        /** Where Warsh is the mushaf people grow up with: the Maghreb and West Africa. */
        private val WARSH_LANDS = setOf("DZ", "MA", "MR", "EH", "SN", "ML", "NE", "GM", "GN")

        fun forCountry(country: String?): Riwayah = if (country?.uppercase() in WARSH_LANDS) WARSH else HAFS
    }
}
