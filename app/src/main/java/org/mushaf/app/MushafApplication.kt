package org.mushaf.app

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.mushaf.app.di.appModule

class MushafApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.DEBUG else Level.NONE)
            androidContext(this@MushafApplication)
            modules(appModule)
        }
        // Nothing runs while the app is closed: the reminders of earlier versions go.
        androidx.work.WorkManager.getInstance(this).run {
            cancelUniqueWork("wird")
            cancelUniqueWork("kahf")
        }
        runCatching { getSystemService(android.app.NotificationManager::class.java).deleteNotificationChannel("wird") }
    }
}
