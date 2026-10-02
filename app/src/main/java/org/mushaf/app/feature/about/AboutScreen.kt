package org.mushaf.app.feature.about

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.mushaf.app.BuildConfig
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.Section
import org.mushaf.app.ui.component.SettingRow
import org.mushaf.app.ui.icon.AppIcons

/** Where the text, the fonts, the meanings and the recitations come from. */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop("About", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Section("Mushaf ${BuildConfig.VERSION_NAME}") {
                SettingRow("Source code", "MIT licence, on GitHub", onClick = { uri.openUri("https://github.com/213YaZ786/Mushaf") })
            }
            Section("Sources") {
                for (s in SOURCES) {
                    SettingRow(s.title, s.detail, onClick = { uri.openUri(s.link) })
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private class Credit(val title: String, val detail: String, val link: String)

private val SOURCES = listOf(
    Credit(
        "Quran text and pages",
        "Madinah mushaf of the King Fahd Glorious Quran Printing Complex, its Hafs and page fonts",
        "https://qurancomplex.gov.sa"
    ),
    Credit(
        "Warsh mushaf",
        "The King Fahd Complex's Warsh text, font and layout, through quran-ws/quran-text (CC BY 4.0)",
        "https://github.com/quran-ws/quran-text"
    ),
    Credit(
        "Words, pages and lines",
        "Quran.com: each word's place in the mushaf, word by word meaning and transliteration",
        "https://quran.com"
    ),
    Credit("English meaning", "Saheeh International", "https://quran.com"),
    Credit("Surah introductions", "Sayyid Abul Ala Maududi, Tafhim al-Qur'an, via Quran.com", "https://quran.com"),
    Credit("Text checked against", "The Tanzil Project's Quran text (CC BY 3.0)", "https://tanzil.net"),
    Credit("Translations", "Quran.com, and fawazahmed0/quran-api: QuranEnc, Tanzil and others", "https://github.com/fawazahmed0/quran-api"),
    Credit("Tafsir", "Quran.com", "https://quran.com"),
    Credit("Recitations", "Hafs: Quran.com and quranicaudio.com. Warsh: mp3quran.net", "https://quranicaudio.com"),
    Credit("Similar ayat", "Quran Revision Companion (MIT)", "https://github.com/Waqar144/quran_memorization_helper"),
    Credit("Speech recognition", "Tarteel's Quran model (Apache-2.0), run by whisper.cpp (MIT)", "https://huggingface.co/tarteel-ai/whisper-base-ar-quran")
)
