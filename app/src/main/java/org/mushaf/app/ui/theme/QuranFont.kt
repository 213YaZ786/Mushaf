package org.mushaf.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import org.koin.compose.koinInject
import org.mushaf.app.R
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.settings.SettingsStore

/**
 * The King Fahd Complex's font for the riwayah being read: its Hafs font,
 * or its Warsh font, which alone draws the Warsh mushaf's own letters
 * (the alef forms of Arabic Extended-B, the Maghrebi feh and qaf).
 */
@Composable
fun quranFont(): FontFamily {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    return remember(settings.riwayah) { fontFor(settings.riwayah) }
}

fun fontFor(riwayah: Riwayah): FontFamily =
    FontFamily(Font(if (riwayah == Riwayah.WARSH) R.font.uthmanic_warsh else R.font.uthmanic_hafs))

/** The basmala as each mushaf writes it. */
fun basmalaFor(riwayah: Riwayah): String =
    if (riwayah == Riwayah.WARSH) "بِسْمِ ࡴ۬للَّهِ ࡴ۬لرَّحْمَٰنِ ࡴ۬لرَّحِيمِ"
    else "بِسۡمِ ٱللَّهِ ٱلرَّحۡمَٰنِ ٱلرَّحِيمِ"
