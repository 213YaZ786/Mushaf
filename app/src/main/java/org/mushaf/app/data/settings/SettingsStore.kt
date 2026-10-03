package org.mushaf.app.data.settings

import android.content.Context
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.update.UpdateMode
import org.mushaf.app.data.quran.Script
import org.mushaf.app.core.quran.Riwayah
import java.util.Locale

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
    /** The riwayah the mushaf is read in; at the first launch, the one of the phone's country. */
    val riwayah: Riwayah = Riwayah.HAFS,
    /** The reciter heard in Warsh (mp3quran's id, past 10000), Yassin al-Jazairi first. */
    val warshReciter: Int = 10014,
    /** What happens when a newer version is out, checked once when the app opens. */
    val updates: UpdateMode = UpdateMode.INSTALL,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** True black instead of dark grey in dark mode. */
    val pureBlack: Boolean = false,
    /** Zones and floating controls in liquid glass, over a soft light in the wallpaper's colours. */
    val glass: Boolean = true,
    /** Multiplier on every text style of the app (not the Quran's), one of ui.theme.TEXT_SCALES. */
    val textScale: Float = 1f,
    /** The first launch page was closed. */
    val welcomeSeen: Boolean = false,
    /** The page being read, so the app opens where the reader stopped. */
    val page: Int = 1,
    /** How the pages are drawn. */
    val script: Script = Script.PRINT,
    /** Two pages side by side when the window is wide enough, as an open book. */
    val twoPages: Boolean = true,
    /** The screen stays on while a page is shown. */
    val keepScreenOn: Boolean = true,
    /** The translations shown with the meaning, in order; Saheeh International comes with the app. */
    val translations: List<String> = listOf("qc:20"),
    /** Each word with its own meaning, under it, when reading with the meaning. */
    val wordByWord: Boolean = true,
    /** The book of tafsir opened last. */
    val tafsir: Int = 169,
    /** The reciter heard (Quran.com's id), Mishari al-Afasy first. */
    val reciter: Int = 7,
    /** Times each ayah is heard; 0 for again and again. */
    val repeat: Int = 1,
    val speed: Float = 1f,
    /** At the end of a surah the next one follows. */
    val continuePlaying: Boolean = true,
    /** The page turns with the voice, and the word heard lights up. */
    val followVoice: Boolean = true,
    /** All the pages are kept on the phone once chosen in the welcome guide. */
    val keepPagesOffline: Boolean = true,
    /** Large downloads wait for Wi-Fi. */
    val wifiOnly: Boolean = true,
    /** Packs already offered for offline use, so each is offered once. */
    val offered: Set<String> = emptySet(),
    /** A reminder of the wird each day, unless the mushaf was opened that day. */
    val reminder: Boolean = true,
    /** Its time, in minutes after midnight. */
    val reminderAt: Int = 20 * 60,
    /** The last day the mushaf was opened (epoch day). */
    val readDay: Long = -1,
    /** The ayah heard last when the recitation was paused or stopped, to go on from there. */
    val lastHeard: String? = null,
    /** A reminder of Surah Al-Kahf on Friday mornings. */
    val kahf: Boolean = true,
    /** The last day a page of Al-Kahf was shown (epoch day). */
    val kahfDay: Long = -1
)

/**
 * Small preference file, plain JSON written atomically, like the other apps.
 * Nothing here is a secret, and none of it leaves the device.
 */
class SettingsStore(context: Context) {

    private val file = File(context.filesDir, "settings.json")
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    val current: Settings get() = _settings.value

    private fun load(): Settings {
        if (!file.exists()) return Settings(riwayah = Riwayah.forCountry(Locale.getDefault().country))
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    /** The reciter heard in [r]. */
    fun reciter(r: Riwayah = current.riwayah): Int = if (r == Riwayah.WARSH) current.warshReciter else current.reciter

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }
}
