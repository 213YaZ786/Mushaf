package org.mushaf.app.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import org.mushaf.app.data.quran.Riwayat
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.BuildConfig
import org.mushaf.app.core.update.UpdateMode
import org.mushaf.app.core.update.Updates
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Script
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.data.settings.ThemeMode
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.ChoiceDialog
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.Section
import org.mushaf.app.ui.component.SettingRow
import org.mushaf.app.ui.component.SwitchRow
import org.mushaf.app.ui.icon.AppIcons
import org.mushaf.app.ui.theme.TEXT_SCALES
import org.mushaf.app.ui.theme.textScaleLabel

private enum class OpenDialog { NONE, RIWAYAH, SCRIPT, THEME, TEXT_SIZE, UPDATES }

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit, onOpenTranslations: () -> Unit, onOpenOffline: () -> Unit, onOpenGuide: () -> Unit) {
    val store: SettingsStore = koinInject()
    val fonts: PageFonts = koinInject()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    val riwayat: Riwayat = koinInject()
    val scope = rememberCoroutineScope()

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop("Settings", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))

            Section("Mushaf") {
                val kept = remember(settings.script) { fonts.count(settings.script) }
                SettingRow(
                    title = "Riwayah",
                    summary = "${settings.riwayah.label} · ${settings.riwayah.arabic}",
                    onClick = { dialog = OpenDialog.RIWAYAH }
                )
                // Warsh has one script, the King Fahd Complex's; the printed pages are Hafs's.
                if (settings.riwayah == Riwayah.HAFS) SettingRow(
                    title = "Pages",
                    summary = scriptLabel(settings.script) + when (settings.script) {
                        Script.HAFS -> ""
                        else -> " · $kept of 604 pages kept on the phone"
                    },
                    onClick = { dialog = OpenDialog.SCRIPT }
                )
                SwitchRow(
                    title = "Two pages side by side",
                    summary = "On a wide screen, as an open book.",
                    checked = settings.twoPages,
                    onChange = { on -> store.update { it.copy(twoPages = on) } }
                )
                SwitchRow(
                    title = "Keep the screen on",
                    summary = "While a page is shown.",
                    checked = settings.keepScreenOn,
                    onChange = { on -> store.update { it.copy(keepScreenOn = on) } }
                )
            }

            Section("Offline") {
                SettingRow(
                    title = "Keep offline",
                    summary = "Pages, recitations and tafsir on the phone, one by one or all at once.",
                    onClick = onOpenOffline
                )
            }

            Section("Meaning") {
                SettingRow(
                    title = "Translations",
                    summary = settings.translations.size.let { if (it == 1) "1 shown" else "$it shown" } + " · more than 600 in about 100 languages",
                    onClick = onOpenTranslations
                )
                SwitchRow(
                    title = "Word by word",
                    summary = "Each word with its own meaning, when reading with the meaning.",
                    checked = settings.wordByWord,
                    onChange = { on -> store.update { it.copy(wordByWord = on) } }
                )
            }

            Section("Look") {
                SettingRow("Theme", themeLabel(settings.themeMode), onClick = { dialog = OpenDialog.THEME })
                SwitchRow(
                    title = "Pure black",
                    summary = "In the dark theme.",
                    checked = settings.pureBlack,
                    onChange = { on -> store.update { it.copy(pureBlack = on) } }
                )
                SwitchRow(
                    title = "Liquid glass",
                    summary = "Panes of glass over a soft light in your wallpaper's colours.",
                    checked = settings.glass,
                    onChange = { on -> store.update { it.copy(glass = on) } }
                )
                SettingRow("Text size", textScaleLabel(settings.textScale) + " · menus and translations", onClick = { dialog = OpenDialog.TEXT_SIZE })
            }

            Section("App") {
                SettingRow("Updates", updatesLabel(settings.updates), onClick = { dialog = OpenDialog.UPDATES })
                SettingRow("Guide", "The first pages again, with their choices", onClick = onOpenGuide)
                SettingRow("About", "Version ${BuildConfig.VERSION_NAME} · sources and credits", onClick = onOpenAbout)
            }

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    when (dialog) {
        OpenDialog.RIWAYAH -> ChoiceDialog(
            title = "Riwayah",
            options = Riwayah.entries.map { it to "${it.label} · ${it.arabic}" },
            selected = settings.riwayah,
            onSelect = { r -> scope.launch { riwayat.change(r) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.SCRIPT -> ChoiceDialog(
            title = "Pages",
            options = Script.entries.filter { it.usable }.map { it to scriptLabel(it) },
            selected = settings.script,
            onSelect = { s -> store.update { it.copy(script = s) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.THEME -> ChoiceDialog(
            title = "Theme",
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = { m -> store.update { it.copy(themeMode = m) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = "Text size",
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onSelect = { s -> store.update { it.copy(textScale = s) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = "Updates",
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = settings.updates,
            onSelect = { mode ->
                store.update { it.copy(updates = mode) }
                // Installing needs Android's leave, asked when chosen.
                if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
            },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.NONE -> Unit
    }
}

private fun scriptLabel(script: Script): String = when (script) {
    Script.PRINT -> "As printed (Madinah mushaf)"
    Script.TAJWEED -> "As printed, tajweed in colour"
    Script.HAFS -> "Hafs font, no download"
}

private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> "Same as the system"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> "Off"
    UpdateMode.NOTIFY -> "Notify me"
    UpdateMode.INSTALL -> "Install automatically"
}
