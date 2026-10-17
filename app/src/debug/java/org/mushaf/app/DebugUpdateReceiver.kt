package org.mushaf.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.mushaf.app.core.update.Updates
import java.io.File
import kotlin.concurrent.thread

/**
 * Debug builds only: feeds an APK of this app (a debug build of a higher
 * versionCode, copied into files/ with run-as) into the same path a download
 * takes: Updates.stageFile checks it, it waits, and Updates.commitIfLeft
 * installs it when the app is left and not busy. A real release cannot
 * update a debug build (another signature).
 * Ported from Calculator's DebugUpdateReceiver (APK/YAZ/Calculator/app/src/debug), after
 * https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync().
 *
 * adb shell am broadcast -n org.mushaf.app.debug/org.mushaf.app.DebugUpdateReceiver --es file next.apk [--ez busy true]
 * `--ez busy true` makes the app count as busy (the update then waits for the next stop);
 * `--ez play true` makes it busy while a real recitation plays, as in the release build
 * (Listen.state.playing); a later broadcast with `--ez busy false` and no file lifts it.
 */
class DebugUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val busy = intent.getBooleanExtra("busy", false)
        val play = intent.getBooleanExtra("play", false)
        Updates.arm(app) {
            busy || (play && org.koin.core.context.GlobalContext.get().get<org.mushaf.app.feature.listen.Listen>().state.value.playing)
        }
        val name = intent.getStringExtra("file") ?: return
        val source = File(app.filesDir, name).takeIf { it.isFile && it.parentFile == app.filesDir } ?: return
        val pending = goAsync()
        thread {
            try {
                val kept = File(app.cacheDir, "update.apk")
                source.copyTo(kept, overwrite = true)
                // Past the version name: "9.9.9" only labels the notification.
                if (Updates.stageFile(app, kept, "9.9.9", "https://github.com/213YaZ786/Mushaf/releases/latest")) {
                    Updates.commitIfLeft(app)
                } else {
                    kept.delete()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
