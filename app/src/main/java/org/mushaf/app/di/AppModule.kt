package org.mushaf.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.sources.Sources
import org.mushaf.app.data.offline.Offline
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.feature.hifz.HifzSession
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.data.play.Stars
import org.mushaf.app.feature.recite.Recite
import org.mushaf.app.data.quran.Search
import org.mushaf.app.data.quran.Tafsir
import org.mushaf.app.data.quran.Translations
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.feature.mushaf.Reader

/** Single composition root. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    single { Quran(androidContext()) }
    single { Sources(androidContext()) }
    single { PageFonts(androidContext(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single { Reader(get()) }
    single { Translations(androidContext(), get(), get()) }
    single { Tafsir(androidContext(), get()) }
    single { Marks(androidContext()) }
    single { Search(get(), get()) }
    single { Recitations(androidContext(), get()) }
    single { Offline(androidContext(), get()) }
    single { Hifz(androidContext(), get()) }
    single { HifzSession(get(), get()) }
    single { Recogniser(androidContext(), get()) }
    single { Stars(androidContext()) }
    single { Recite(get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
    single { Listen(androidContext(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
}
