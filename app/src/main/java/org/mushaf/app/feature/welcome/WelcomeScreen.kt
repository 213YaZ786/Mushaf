package org.mushaf.app.feature.welcome

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.util.Locale
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.data.quran.Riwayat
import org.mushaf.app.core.update.UpdateMode
import org.mushaf.app.core.update.Updates
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.data.offline.Offline
import org.mushaf.app.data.offline.Pack
import org.mushaf.app.data.quran.Script
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.quran.TranslationInfo
import org.mushaf.app.data.quran.Translations
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.QuietButton
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.glass.LocalGlass
import org.mushaf.app.ui.glass.glassZone
import org.mushaf.app.ui.icon.AppIcons

private enum class Page { WELCOME, PAGES, MEANING, LISTEN, UPDATES }

/**
 * The first launch: a few pages, each with the best choice already made,
 * so "Next" all along gives the best app; Skip leaves at any time and every
 * choice is in Settings. Shown again from Settings.
 */
@Composable
fun WelcomeScreen(onFinish: () -> Unit) {
    val pages = Page.entries
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val riwayat: Riwayat = koinInject()
    val last = pager.currentPage == pages.lastIndex
    val store: SettingsStore = koinInject()
    val offline: Offline = koinInject()
    val translations: Translations = koinInject()
    val settings by store.settings.collectAsState()
    val context = LocalContext.current
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val local = remember { suggestedTranslation(Locale.getDefault().language) }
    // The best choice made already: the phone's language joins English the first time.
    androidx.compose.runtime.LaunchedEffect(local) {
        if (local != null && !store.current.welcomeSeen && local.id !in store.current.translations) {
            store.update { s -> s.copy(translations = s.translations + local.id) }
            runCatching { translations.install(local) }
        }
    }

    fun finish() {
        store.update { it.copy(welcomeSeen = true) }
        // The pages are kept on the phone as chosen; the progress shows in a notification.
        if (settings.riwayah == Riwayah.HAFS && settings.keepPagesOffline && settings.script != Script.HAFS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            offline.start(Pack.Pages(settings.script))
        }
        onFinish()
    }

    Column(Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            QuietButton(onClick = { finish() }) { Text(if (last) "Close" else "Skip") }
        }
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth()) { index ->
            when (pages[index]) {
                Page.WELCOME -> PageContent(
                    mark = true,
                    icon = AppIcons.MenuBook,
                    title = "Welcome to Mushaf",
                    intro = "The Quran as printed in Madinah, with its meaning, its recitation and help to learn it by heart. No account, no tracking, no ads.",
                    points = listOf(
                        "Tap the page to show or hide the controls.",
                        "Hold a word for its meaning, the ayah's translation, its tafsir and its recitation."
                    )
                ) {}
                Page.PAGES -> PageContent(
                    icon = AppIcons.MenuBook,
                    title = "The mushaf",
                    intro = "The riwayah you read in, and how its pages are drawn.",
                    points = listOf("Changeable in Settings.")
                ) {
                    Choices(
                        Riwayah.entries.map { it to "${it.label} · ${it.arabic}" },
                        settings.riwayah
                    ) { r -> scope.launch { riwayat.change(r) } }
                    if (settings.riwayah == Riwayah.HAFS) Choices(
                        Script.entries.filter { it.usable }.map {
                            it to when (it) {
                                Script.PRINT -> "As printed in Madinah"
                                Script.TAJWEED -> "As printed, tajweed in colour"
                                Script.HAFS -> "In the Hafs font, nothing to download"
                            }
                        },
                        settings.script
                    ) { s -> store.update { it.copy(script = s) } }
                    if (settings.riwayah == Riwayah.HAFS && settings.script != Script.HAFS) {
                        Option(
                            label = "Keep all pages on the phone",
                            value = true,
                            chosen = settings.keepPagesOffline,
                            detail = "About 190 MB, on Wi-Fi, for reading without a connection.",
                            onChoose = { store.update { it.copy(keepPagesOffline = !it.keepPagesOffline) } }
                        )
                    }
                }
                Page.MEANING -> PageContent(
                    icon = AppIcons.Translate,
                    title = "The meaning",
                    intro = "Shown under each ayah when reading with the meaning.",
                    points = listOf("More than 500 translations in about 100 languages in Settings.")
                ) {
                    val options = listOfNotNull(local, Translations.BUNDLED)
                    for (t in options) {
                        val on = t.id in settings.translations
                        Option(
                            label = t.name,
                            value = true,
                            chosen = on,
                            detail = t.language,
                            onChoose = {
                                if (on) store.update { s -> s.copy(translations = s.translations - t.id) }
                                else {
                                    store.update { s -> s.copy(translations = s.translations + t.id) }
                                    scope.launch { runCatching { translations.install(t) } }
                                }
                            }
                        )
                    }
                    Option(
                        label = "Word by word",
                        value = true,
                        chosen = settings.wordByWord,
                        detail = "Each word with its own meaning under it.",
                        onChoose = { store.update { it.copy(wordByWord = !it.wordByWord) } }
                    )
                }
                Page.LISTEN -> PageContent(
                    icon = AppIcons.Headphones,
                    title = "The recitation",
                    intro = "The page follows the voice, the word heard lights up.",
                    points = listOf("Nine more reciters in Settings and while listening.")
                ) {
                    Choices(
                        Recitations.of(settings.riwayah).take(4).map { it.id to it.label },
                        store.reciter(settings.riwayah)
                    ) { id -> store.update { if (settings.riwayah == Riwayah.WARSH) it.copy(warshReciter = id) else it.copy(reciter = id) } }
                }
                Page.UPDATES -> PageContent(
                    icon = AppIcons.Update,
                    title = "Updates",
                    intro = "When a new version of Mushaf is out.",
                    points = listOf("Changeable in Settings.")
                ) {
                    Choices(
                        listOf(UpdateMode.OFF to "Off", UpdateMode.NOTIFY to "Notify me", UpdateMode.INSTALL to "Install"),
                        settings.updates
                    ) { mode ->
                        store.update { it.copy(updates = mode) }
                        if (mode == UpdateMode.INSTALL && !Updates.canInstall(context)) Updates.allowInstalls(context)
                    }
                }
            }
        }
        Dots(pages.size, pager.currentPage)
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (pager.currentPage > 0) {
                QuietButton(onClick = { scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }) { Text("Back") }
            }
            Spacer(Modifier.weight(1f))
            BoldButton(filled = true, onClick = {
                if (last) finish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
            }) { Text(if (last) "Start reading" else "Next") }
        }
    }
}

/** A translation known to be reviewed, in the phone's language. */
private fun suggestedTranslation(language: String): TranslationInfo? {
    val (id, name, lang) = when (language) {
        "fr" -> Triple("qc:31", "Muhammad Hamidullah", "French")
        "kab", "ber", "tzm", "zgh" -> Triple("fa:ber-ramdaneatmansou", "Ramdane At Mansour", "Kabyle (Taqbaylit)")
        "de" -> Triple("qc:27", "Frank Bubenheim and Nadeem Elyas", "German")
        "es" -> Triple("qc:83", "Sheikh Isa Garcia", "Spanish")
        "it" -> Triple("qc:153", "Hamza Roberto Piccardo", "Italian")
        "pt" -> Triple("qc:43", "Samir El-Hayek", "Portuguese")
        "nl" -> Triple("qc:235", "Malak Faris Abdalsalaam", "Dutch")
        "tr" -> Triple("qc:77", "Diyanet Isleri", "Turkish")
        "ur" -> Triple("qc:54", "Maulana Muhammad Junagarhi", "Urdu")
        "id", "in" -> Triple("qc:33", "Indonesian Islamic Affairs Ministry", "Indonesian")
        "ms" -> Triple("qc:39", "Abdullah Muhammad Basmeih", "Malay")
        "ru" -> Triple("qc:45", "Elmir Kuliev", "Russian")
        "bn" -> Triple("qc:161", "Taisirul Quran", "Bengali")
        "fa" -> Triple("qc:135", "IslamHouse.com", "Persian")
        "sq" -> Triple("qc:89", "Sherif Ahmeti", "Albanian")
        "bs", "hr", "sr" -> Triple("qc:126", "Besim Korkut", "Bosnian")
        "sw" -> Triple("qc:49", "Ali Muhsin Al-Barwani", "Swahili")
        "ha" -> Triple("qc:32", "Abubakar Gumi", "Hausa")
        "so" -> Triple("qc:46", "Mahmud Muhammad Abduh", "Somali")
        "ta" -> Triple("qc:50", "Jan Trust Foundation", "Tamil")
        "hi" -> Triple("qc:122", "Maulana Azizul Haque al-Umari", "Hindi")
        "zh" -> Triple("qc:56", "Ma Jian", "Chinese")
        "ja" -> Triple("qc:35", "Ryoichi Mita", "Japanese")
        "ko" -> Triple("qc:219", "Hamed Choi", "Korean")
        "th" -> Triple("qc:51", "King Fahad Quran Complex", "Thai")
        "uz" -> Triple("qc:55", "Muhammad Sodiq Muhammad Yusuf", "Uzbek")
        "az" -> Triple("qc:75", "Alikhan Musayev", "Azeri")
        "ku" -> Triple("qc:81", "Burhan Muhammad-Amin", "Kurdish")
        "ps" -> Triple("qc:118", "Zakaria Abulsalam", "Pashto")
        "am" -> Triple("qc:87", "Sadiq and Sani", "Amharic")
        "yo" -> Triple("qc:125", "Abu Rahimah Mikael Aykyuni", "Yoruba")
        "sv" -> Triple("qc:48", "Knut Bernström", "Swedish")
        "nb", "no" -> Triple("qc:41", "Einar Berg", "Norwegian")
        "pl" -> Triple("qc:42", "Józef Bielawski", "Polish")
        "ro" -> Triple("qc:782", "Islamic and Cultural League", "Romanian")
        else -> return null
    }
    return TranslationInfo(id, name, lang)
}

@Composable
private fun PageContent(
    icon: ImageVector,
    title: String,
    intro: String,
    points: List<String>,
    mark: Boolean = false,
    choice: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        if (mark) {
            LoadingMark(size = 160.dp)
        } else {
            Box(Modifier.size(80.dp).accentDisc(), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
            }
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        Text(intro, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) { choice() }
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { points.forEach { Point(it) } }
    }
}

@Composable
private fun <T> Choices(options: List<Pair<T, String>>, chosen: T?, onChoose: (T) -> Unit) {
    options.forEach { (value, label) -> Option(label, value, chosen, onChoose) }
}

/** A filled primary colour when picked, so the answer is unmistakable. */
@Composable
private fun <T> Option(label: String, value: T, chosen: T?, onChoose: (T) -> Unit, detail: String? = null) {
    val picked = chosen == value
    val haptics = rememberHaptics()
    ZoneSurface(
        onClick = { haptics.tick(); onChoose(value) },
        shape = RoundedCornerShape(16.dp),
        color = if (picked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        accent = picked,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (picked) Icon(AppIcons.Check, contentDescription = "Chosen", modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
            }
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun Point(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.padding(top = 8.dp).size(6.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Dots(count: Int, current: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .size(if (index == current) 10.dp else 8.dp)
                    .background(if (index == current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    }
}

/** The page's icon on a disc of the accent: a drop of glass in the accent when glass is on. */
@Composable
private fun Modifier.accentDisc(): Modifier {
    val glass = LocalGlass.current
    return if (glass == null) background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
    else glassZone(CircleShape, glass, lens = 1f).background(glass.accentTint, CircleShape)
}
