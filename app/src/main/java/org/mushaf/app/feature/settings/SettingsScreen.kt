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
import org.mushaf.app.data.remind.Reminder
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

private enum class OpenDialog { NONE, RIWAYAH, REMINDER_TIME, SCRIPT, THEME, TEXT_SIZE, UPDATES, LANGUAGE }

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
    // The reminder follows every change made here.
    LaunchedEffect(settings.reminder, settings.reminderAt) { Reminder.schedule(context, store) }
    LaunchedEffect(settings.kahf) { Reminder.scheduleKahf(context, store) }

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

            Section(stringResource(R.string.daily_reminder)) {
                SwitchRow(
                    title = stringResource(R.string.remind_wird),
                    summary = stringResource(R.string.reminder_summary, reminderTime(settings.reminderAt)),
                    checked = settings.reminder,
                    onChange = { on ->
                        store.update { it.copy(reminder = on) }
                        if (on && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
                if (settings.reminder) SettingRow(stringResource(R.string.time), reminderTime(settings.reminderAt), onClick = { dialog = OpenDialog.REMINDER_TIME })
                SwitchRow(
                    title = stringResource(R.string.kahf_fridays),
                    summary = stringResource(R.string.kahf_fridays_detail),
                    checked = settings.kahf,
                    onChange = { on -> store.update { it.copy(kahf = on) } }
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
                    title = stringResource(R.string.liquid_glass),
                    summary = stringResource(R.string.liquid_glass_detail),
                    checked = settings.glass,
                    onChange = { on -> store.update { it.copy(glass = on) } }
                )
                SettingRow(stringResource(R.string.text_size), textScaleLabel(settings.textScale) + " · " + stringResource(R.string.text_size_detail), onClick = { dialog = OpenDialog.TEXT_SIZE })
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
        OpenDialog.REMINDER_TIME -> {
            val state = rememberTimePickerState(
                initialHour = settings.reminderAt / 60,
                initialMinute = settings.reminderAt % 60,
                is24Hour = DateFormat.is24HourFormat(context)
            )
            ZoneAlertDialog(
                onDismissRequest = { dialog = OpenDialog.NONE },
                title = { Text(stringResource(R.string.reminder_time)) },
                text = { TimePicker(state) },
                confirmButton = {
                    TextButton(onClick = {
                        store.update { it.copy(reminderAt = state.hour * 60 + state.minute) }
                        dialog = OpenDialog.NONE
                    }) { Text(stringResource(R.string.set)) }
                },
                dismissButton = { TextButton(onClick = { dialog = OpenDialog.NONE }) { Text(stringResource(R.string.cancel)) } }
            )
        }
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
        OpenDialog.NONE -> Unit
    }
}

/** The reminder's time as the phone writes times. */
private fun reminderTime(minutes: Int): String =
    LocalTime.of(minutes / 60, minutes % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

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
