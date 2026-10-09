package org.mushaf.app.feature.settings

import org.mushaf.app.R
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import org.mushaf.app.ui.component.ZoneAlertDialog
import java.time.format.FormatStyle
import java.time.format.DateTimeFormatter
import java.time.LocalTime
import androidx.core.content.ContextCompat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.TimePicker
import android.app.LocaleManager
import android.content.Context
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.compose.material3.TextButton
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.text.format.DateFormat
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
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

private enum class OpenDialog { NONE, RIWAYAH, SCRIPT, THEME, TEXT_SIZE, UPDATES, LANGUAGE, SPACING }

/** The app's languages, each named in itself; "" follows the phone. */
private val APP_LANGUAGES = listOf(
    "en" to "English", "ar" to "العربية", "az" to "Azərbaycanca", "bn" to "বাংলা", "bs" to "Bosanski",
    "de" to "Deutsch", "es" to "Español", "fa" to "فارسی", "fr" to "Français", "hi" to "हिन्दी",
    "id" to "Bahasa Indonesia", "it" to "Italiano", "kab" to "Taqbaylit", "ms" to "Bahasa Melayu",
    "nl" to "Nederlands", "pt" to "Português", "ru" to "Русский", "sq" to "Shqip", "sw" to "Kiswahili",
    "tr" to "Türkçe", "ur" to "اردو", "uz" to "Oʻzbekcha"
)

/** The app's own language, kept by Android (13 and later). */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private object AppLanguage {
    fun current(context: Context): String =
        context.getSystemService(LocaleManager::class.java).applicationLocales.get(0)?.toLanguageTag()?.substringBefore('-').orEmpty()

    fun set(context: Context, tag: String) {
        context.getSystemService(LocaleManager::class.java).applicationLocales =
            if (tag.isEmpty()) LocaleList.getEmptyLocaleList() else LocaleList.forLanguageTags(tag)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenAbout: () -> Unit, onOpenTranslations: () -> Unit, onOpenOffline: () -> Unit, onOpenGuide: () -> Unit) {
    val store: SettingsStore = koinInject()
    val fonts: PageFonts = koinInject()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    val riwayat: Riwayat = koinInject()
    val scope = rememberCoroutineScope()
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // A backup goes where the reader saves it; one read back replaces only once agreed.
    val backup: org.mushaf.app.data.backup.Backup = koinInject()
    var restoring by remember { mutableStateOf<android.net.Uri?>(null) }
    var told by remember { mutableStateOf<Int?>(null) }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            told = runCatching { context.contentResolver.openOutputStream(uri)!!.let { backup.write(it) } }
                .fold({ R.string.backup_done }, { R.string.backup_failed })
        }
    }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }
    restoring?.let { uri ->
        ZoneAlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text(stringResource(R.string.restore_q)) },
            text = { Text(stringResource(R.string.restore_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        val ok = runCatching { context.contentResolver.openInputStream(uri)!!.let { backup.read(it) } }.getOrDefault(false)
                        told = if (ok) R.string.restore_done else R.string.restore_failed
                    }
                }) { Text(stringResource(R.string.restore)) }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text(stringResource(R.string.cancel)) } }
        )
    }
    told?.let { message ->
        ZoneAlertDialog(
            onDismissRequest = { told = null },
            text = { Text(stringResource(message)) },
            confirmButton = { TextButton(onClick = { told = null }) { Text(stringResource(R.string.ok)) } }
        )
    }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(stringResource(R.string.settings), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))

            Section(stringResource(R.string.mushaf)) {
                val kept = remember(settings.script) { fonts.count(settings.script) }
                SettingRow(
                    title = stringResource(R.string.riwayah),
                    summary = "${settings.riwayah.label} · ${settings.riwayah.arabic}",
                    onClick = { dialog = OpenDialog.RIWAYAH }
                )
                // Warsh has one script, the King Fahd Complex's; the printed pages are Hafs's.
                if (settings.riwayah == Riwayah.HAFS) SettingRow(
                    title = stringResource(R.string.pages),
                    summary = scriptLabel(settings.script) + when (settings.script) {
                        Script.HAFS -> ""
                        else -> " · " + stringResource(R.string.pages_kept, kept)
                    },
                    onClick = { dialog = OpenDialog.SCRIPT }
                )
                SwitchRow(
                    title = stringResource(R.string.two_pages),
                    summary = stringResource(R.string.two_pages_detail),
                    checked = settings.twoPages,
                    onChange = { on -> store.update { it.copy(twoPages = on) } }
                )
                SwitchRow(
                    title = stringResource(R.string.keep_screen_on),
                    summary = stringResource(R.string.keep_screen_on_detail),
                    checked = settings.keepScreenOn,
                    onChange = { on -> store.update { it.copy(keepScreenOn = on) } }
                )
            }

            Section(stringResource(R.string.offline)) {
                SettingRow(
                    title = stringResource(R.string.keep_offline),
                    summary = stringResource(R.string.keep_offline_detail),
                    onClick = onOpenOffline
                )
            }

            Section(stringResource(R.string.meaning)) {
                SettingRow(
                    title = stringResource(R.string.translations),
                    summary = settings.translations.size.let { pluralStringResource(R.plurals.translations_shown, it, it) } + " · " + stringResource(R.string.translations_more),
                    onClick = onOpenTranslations
                )
                SwitchRow(
                    title = stringResource(R.string.word_by_word),
                    summary = stringResource(R.string.word_by_word_reading),
                    checked = settings.wordByWord,
                    onChange = { on -> store.update { it.copy(wordByWord = on) } }
                )
            }

            Section(stringResource(R.string.look)) {
                SettingRow(stringResource(R.string.theme), themeLabel(settings.themeMode), onClick = { dialog = OpenDialog.THEME })
                SwitchRow(
                    title = stringResource(R.string.pure_black),
                    summary = stringResource(R.string.pure_black_detail),
                    checked = settings.pureBlack,
                    onChange = { on -> store.update { it.copy(pureBlack = on) } }
                )
                SwitchRow(
                    title = stringResource(R.string.cream_page),
                    summary = stringResource(R.string.cream_page_detail),
                    checked = settings.cream,
                    onChange = { on -> store.update { it.copy(cream = on) } }
                )
                SwitchRow(
                    title = stringResource(R.string.liquid_glass),
                    summary = stringResource(R.string.liquid_glass_detail),
                    checked = settings.glass,
                    onChange = { on -> store.update { it.copy(glass = on) } }
                )
                SettingRow(stringResource(R.string.text_size), textScaleLabel(settings.textScale) + " · " + stringResource(R.string.text_size_detail), onClick = { dialog = OpenDialog.TEXT_SIZE })
                SettingRow(
                    stringResource(R.string.spacing),
                    stringResource(if (settings.airy) R.string.spacing_airy else R.string.spacing_compact) + " · " + stringResource(R.string.spacing_detail),
                    onClick = { dialog = OpenDialog.SPACING }
                )
            }

            Section(stringResource(R.string.your_data)) {
                SettingRow(stringResource(R.string.backup), stringResource(R.string.backup_detail), onClick = {
                    saveBackup.launch("mushaf-" + java.time.LocalDate.now() + ".json")
                })
                SettingRow(stringResource(R.string.restore), stringResource(R.string.restore_detail), onClick = {
                    openBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                })
            }

            Section(stringResource(R.string.app)) {
                // Android 13 and later keep a language per app; before, the phone's applies.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val tag = remember(dialog) { AppLanguage.current(context) }
                    SettingRow(stringResource(R.string.language), APP_LANGUAGES.firstOrNull { it.first == tag }?.second ?: stringResource(R.string.language_system), onClick = { dialog = OpenDialog.LANGUAGE })
                }
                SettingRow(stringResource(R.string.updates), updatesLabel(settings.updates), onClick = { dialog = OpenDialog.UPDATES })
                SettingRow(stringResource(R.string.guide), stringResource(R.string.guide_detail), onClick = onOpenGuide)
                SettingRow(stringResource(R.string.about), stringResource(R.string.about_summary, BuildConfig.VERSION_NAME), onClick = onOpenAbout)
            }

            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }

    when (dialog) {
        OpenDialog.RIWAYAH -> ChoiceDialog(
            title = stringResource(R.string.riwayah),
            options = Riwayah.entries.map { it to "${it.label} · ${it.arabic}" },
            selected = settings.riwayah,
            onSelect = { r -> scope.launch { riwayat.change(r) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.SCRIPT -> ChoiceDialog(
            title = stringResource(R.string.pages),
            options = Script.entries.filter { it.usable }.map { it to scriptLabel(it) },
            selected = settings.script,
            onSelect = { s -> store.update { it.copy(script = s) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.THEME -> ChoiceDialog(
            title = stringResource(R.string.theme),
            options = ThemeMode.entries.map { it to themeLabel(it) },
            selected = settings.themeMode,
            onSelect = { m -> store.update { it.copy(themeMode = m) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TEXT_SIZE -> ChoiceDialog(
            title = stringResource(R.string.text_size),
            options = TEXT_SCALES.map { it to textScaleLabel(it) },
            selected = settings.textScale,
            onSelect = { s -> store.update { it.copy(textScale = s) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.UPDATES -> ChoiceDialog(
            title = stringResource(R.string.updates),
            options = UpdateMode.entries.map { it to updatesLabel(it) },
            selected = settings.updates,
            onSelect = { mode ->
                store.update { it.copy(updates = mode) }
                // Installing needs Android's leave, asked when chosen.
                if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
            },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.LANGUAGE -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ChoiceDialog(
            title = stringResource(R.string.language),
            options = listOf("" to stringResource(R.string.language_system)) + APP_LANGUAGES,
            selected = AppLanguage.current(context),
            onSelect = { tag -> dialog = OpenDialog.NONE; AppLanguage.set(context, tag) },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.SPACING -> ChoiceDialog(
            title = stringResource(R.string.spacing),
            options = listOf(true to stringResource(R.string.spacing_airy), false to stringResource(R.string.spacing_compact)),
            selected = settings.airy,
            onSelect = { a -> store.update { it.copy(airy = a) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.NONE -> Unit
    }
}

@Composable
private fun scriptLabel(script: Script): String = when (script) {
    Script.PRINT -> stringResource(R.string.script_print_short)
    Script.TAJWEED -> stringResource(R.string.script_tajweed)
    Script.HAFS -> stringResource(R.string.script_hafs_short)
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

@Composable
private fun updatesLabel(mode: UpdateMode): String = when (mode) {
    UpdateMode.OFF -> stringResource(R.string.updates_off)
    UpdateMode.NOTIFY -> stringResource(R.string.updates_notify)
    UpdateMode.INSTALL -> stringResource(R.string.updates_install_auto)
}
