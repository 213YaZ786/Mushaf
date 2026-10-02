package org.mushaf.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Quran
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
    single { PageFonts(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single { Reader(get()) }
    single { Translations(androidContext(), get()) }
    single { Tafsir(androidContext()) }
    single { Marks(androidContext()) }
    single { Search(get(), get()) }
    single { Recitations(androidContext()) }
    single { Listen(androidContext(), get(), get(), get(), CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)) }
}
