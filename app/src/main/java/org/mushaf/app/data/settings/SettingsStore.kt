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

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
data class Settings(
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
    val keepScreenOn: Boolean = true
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
        if (!file.exists()) return Settings()
        return runCatching { json.decodeFromString<Settings>(file.readText()) }
            .getOrDefault(Settings())
    }

    fun update(transform: (Settings) -> Settings) {
        val updated = transform(_settings.value)
        if (updated == _settings.value) return
        _settings.value = updated
        runCatching { file.writeTextAtomically(json.encodeToString(updated)) }
    }
}
