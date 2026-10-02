package org.mushaf.app.di

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.feature.mushaf.Reader

/** Single composition root. */
val appModule = module {
    single { SettingsStore(androidContext()) }
    single { Quran(androidContext()) }
    single { PageFonts(androidContext(), CoroutineScope(SupervisorJob() + Dispatchers.Default)) }
    single { Reader(get()) }
}
