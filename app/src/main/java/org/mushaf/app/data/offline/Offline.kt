package org.mushaf.app.data.offline

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mushaf.app.R
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.data.quran.PageFonts
import org.mushaf.app.data.quran.Script
import org.mushaf.app.data.quran.Tafsir
import org.mushaf.app.data.settings.SettingsStore

/** Something that can be kept on the phone as a whole. */
sealed class Pack(val id: String, val total: Int) {
    class Pages(val script: Script) : Pack("pages-${script.name.lowercase()}", 604)
    class TafsirBook(val book: Int) : Pack("tafsir-$book", 114)
    class Recitation(val reciter: Int) : Pack("recitation-$reciter", 114)

    companion object {
        fun parse(id: String): Pack? = when {
            id == "pages-print" -> Pages(Script.PRINT)
            id == "pages-tajweed" -> Pages(Script.TAJWEED)
            id.startsWith("tafsir-") -> id.removePrefix("tafsir-").toIntOrNull()?.let { TafsirBook(it) }
            id.startsWith("recitation-") -> id.removePrefix("recitation-").toIntOrNull()?.let { Recitation(it) }
            else -> null
        }
    }
}

/** Where a pack stands while it downloads. */
data class Progress(val running: Boolean, val done: Int, val total: Int, val failed: Boolean)

/**
 * Keeps packs on the phone for offline use. Each pack downloads in the
 * background, survives the app being closed or the phone restarted, shows
 * its progress in a notification, waits for Wi-Fi when asked to, and starts
 * again where it stopped: what is already here is never fetched twice.
 */
class Offline(private val context: Context, private val settings: SettingsStore) {

    private val work get() = WorkManager.getInstance(context)

    fun start(pack: Pack) {
        val wifi = settings.current.wifiOnly
        val request = OneTimeWorkRequestBuilder<PackWorker>()
            .setInputData(workDataOf("pack" to pack.id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (wifi) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .addTag("pack")
            .build()
        work.enqueueUniqueWork(pack.id, ExistingWorkPolicy.KEEP, request)
    }

    fun cancel(pack: Pack) {
        work.cancelUniqueWork(pack.id)
    }

    fun progress(pack: Pack): Flow<Progress?> = work.getWorkInfosForUniqueWorkFlow(pack.id).map { infos ->
        val info = infos.lastOrNull() ?: return@map null
        Progress(
            running = info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED,
            done = info.progress.getInt("done", 0),
            total = pack.total,
            failed = info.state == WorkInfo.State.FAILED
        )
    }
}

class PackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params), KoinComponent {

    private val fonts: PageFonts by inject()
    private val tafsir: Tafsir by inject()
    private val recitations: Recitations by inject()

    override suspend fun doWork(): Result {
        val pack = Pack.parse(inputData.getString("pack") ?: return Result.failure()) ?: return Result.failure()
        val title = title(pack)
        runCatching { setForeground(foreground(title, 0, pack.total)) }
        var last = -1
        val report: suspend (Int) -> Unit = { done ->
            if (done != last) {
                last = done
                setProgress(workDataOf("done" to done))
                runCatching { setForeground(foreground(title, done, pack.total)) }
            }
        }
        return try {
            when (pack) {
                is Pack.Pages -> {
                    var done = 0
                    for (page in 1..604) {
                        fonts.get(pack.script, page)
                        done++
                        if (done % 4 == 0 || done == 604) report(done)
                    }
                }
                is Pack.TafsirBook -> {
                    val book = Tafsir.BOOKS.first { it.id == pack.book }
                    tafsir.download(book) { report(it) }
                }
                is Pack.Recitation -> recitations.download(pack.reciter) { report(it) }
            }
            Result.success()
        } catch (e: Exception) {
            // Started again later by WorkManager; what came is kept.
            if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
    }

    private fun title(pack: Pack) = applicationContext.run {
        when (pack) {
            is Pack.Pages -> getString(if (pack.script == Script.TAJWEED) R.string.mushaf_tajweed_pages else R.string.mushaf_pages_title)
            is Pack.TafsirBook -> getString(R.string.tafsir_named, Tafsir.BOOKS.firstOrNull { it.id == pack.book }?.name ?: "")
            is Pack.Recitation -> getString(R.string.recitation_named, Recitations.reciter(pack.reciter).name)
        }
    }

    private fun foreground(title: String, done: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, applicationContext.getString(R.string.downloads_channel), NotificationManager.IMPORTANCE_LOW))
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_mushaf)
            .setContentTitle(title)
            .setContentText(applicationContext.getString(R.string.n_of_m, done, total))
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(title.hashCode(), notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(title.hashCode(), notification)
        }
    }

    companion object {
        const val CHANNEL = "downloads"
    }
}
