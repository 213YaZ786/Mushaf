package org.mushaf.app.data.remind

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mushaf.app.MainActivity
import org.mushaf.app.R
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.settings.SettingsStore

/**
 * The daily reminder of the wird: once a day at the time chosen, unless the
 * mushaf was opened that day already; it says where reading stopped, and the
 * day's lesson when a hifz plan is set. WorkManager keeps it through a
 * restart of the phone; nothing is asked of any server.
 */
object Reminder {

    private const val WORK = "wird"
    const val CHANNEL = "wird"

    /**
     * Sets the next reminder from the settings, or none when it is off.
     * At the app's start [policy] is KEEP: the reminder itself starts the
     * app, and replacing it then would cancel it before it shows. The
     * reminder sets the next one after itself (APPEND_OR_REPLACE).
     */
    fun schedule(context: Context, settings: SettingsStore, policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE) {
        val work = WorkManager.getInstance(context)
        val s = settings.current
        if (!s.reminder) {
            work.cancelUniqueWork(WORK)
            return
        }
        val now = LocalDateTime.now()
        var next = LocalDate.now().atTime(LocalTime.of(s.reminderAt / 60, s.reminderAt % 60))
        // Never twice in a day: the next one is at least an hour away.
        while (next.isBefore(now.plusHours(1))) next = next.plusDays(1)
        work.enqueueUniqueWork(
            WORK,
            policy,
            OneTimeWorkRequestBuilder<ReminderWorker>().setInitialDelay(Duration.between(now, next)).build()
        )
    }

    fun today(): Long = LocalDate.now().toEpochDay()
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val settings: SettingsStore by inject()
    private val quran: Quran by inject()
    private val hifz: Hifz by inject()

    override suspend fun doWork(): Result {
        val s = settings.current
        try {
            if (s.reminder && s.readDay != Reminder.today()) notifyWird()
        } finally {
            Reminder.schedule(applicationContext, settings, ExistingWorkPolicy.APPEND_OR_REPLACE)
        }
        return Result.success()
    }

    private suspend fun notifyWird() {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val page = settings.current.page
        val surah = runCatching { quran.surah(quran.firstAyah(page).surah).name }.getOrNull()
        val where = if (surah != null) "Continue at $surah, page $page." else "Continue at page $page."
        val lesson = runCatching { hifz.lesson() }.getOrDefault(emptyList())
        val revise = runCatching { hifz.revision().let { it.recent.size + it.due.size } }.getOrDefault(0)
        val hifzLine = buildList {
            lesson.firstOrNull()?.let { first ->
                val name = runCatching { quran.surah(first.surah).name }.getOrDefault("")
                add("Lesson: $name $first" + if (lesson.size > 1) "–${lesson.last().ayah}" else "")
            }
            if (revise > 0) add(if (revise == 1) "1 page to revise" else "$revise pages to revise")
        }.joinToString(" · ")
        val text = if (hifzLine.isEmpty()) where else "$where\n$hifzLine"
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(Reminder.CHANNEL, "Daily reminder", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val note = NotificationCompat.Builder(context, Reminder.CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mushaf)
            .setContentTitle("Your daily wird")
            .setContentText(where)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTE, note)
    }

    private companion object {
        const val NOTE = 7
    }
}
