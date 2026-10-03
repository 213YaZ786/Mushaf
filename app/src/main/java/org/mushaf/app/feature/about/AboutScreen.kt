package org.mushaf.app.feature.about

import org.mushaf.app.R
import androidx.compose.ui.res.stringResource
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
        top = { FloatingTop(stringResource(R.string.about), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            Section(stringResource(R.string.about_version, BuildConfig.VERSION_NAME)) {
                SettingRow(stringResource(R.string.source_code), stringResource(R.string.source_code_detail), onClick = { uri.openUri("https://github.com/213YaZ786/Mushaf") })
            }
            Section(stringResource(R.string.sources)) {
                for (s in SOURCES) {
                    SettingRow(stringResource(s.title), stringResource(s.detail), onClick = { uri.openUri(s.link) })
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

private class Credit(val title: Int, val detail: Int, val link: String)

private val SOURCES = listOf(
    Credit(
        R.string.credit_text,
        R.string.credit_text_detail,
        "https://qurancomplex.gov.sa"
    ),
    Credit(
        R.string.credit_warsh,
        R.string.credit_warsh_detail,
        "https://github.com/quran-ws/quran-text"
    ),
    Credit(
        R.string.credit_words,
        R.string.credit_words_detail,
        "https://quran.com"
    ),
    Credit(R.string.credit_english, R.string.credit_english_detail, "https://quran.com"),
    Credit(R.string.credit_intro, R.string.credit_intro_detail, "https://quran.com"),
    Credit(R.string.credit_checked, R.string.credit_checked_detail, "https://tanzil.net"),
    Credit(R.string.translations, R.string.credit_translations_detail, "https://github.com/fawazahmed0/quran-api"),
    Credit(R.string.tafsir, R.string.credit_quran_com, "https://quran.com"),
    Credit(R.string.credit_recitations, R.string.credit_recitations_detail, "https://quranicaudio.com"),
    Credit(R.string.similar_ayat, R.string.credit_similar_detail, "https://github.com/Waqar144/quran_memorization_helper"),
    Credit(R.string.credit_speech, R.string.credit_speech_detail, "https://huggingface.co/tarteel-ai/whisper-base-ar-quran")
)
