package org.mushaf.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import androidx.work.ExistingWorkPolicy
import org.koin.core.context.GlobalContext
import org.mushaf.app.data.remind.Reminder
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.di.appModule

class MushafApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@MushafApplication)
            modules(appModule)
        }
        // The next reminder of the wird, set again at each start (and kept by WorkManager through a restart).
        Reminder.schedule(this, GlobalContext.get().get<SettingsStore>(), ExistingWorkPolicy.KEEP)
        Reminder.scheduleKahf(this, GlobalContext.get().get<SettingsStore>(), ExistingWorkPolicy.KEEP)
    }
}
