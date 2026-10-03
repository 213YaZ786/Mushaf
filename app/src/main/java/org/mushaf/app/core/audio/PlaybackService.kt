package org.mushaf.app.core.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import java.io.File

/**
 * Plays the recitation, on with the screen off or the app closed, with
 * its controls in a notification and on the lock screen. What has been
 * heard is kept (up to 1 GB, the oldest goes first), so a surah heard
 * once plays again offline.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val upstream = DefaultHttpDataSource.Factory().setUserAgent("Mushaf").setAllowCrossProtocolRedirects(false)
        val cached = CacheDataSource.Factory().setCache(cache(this)).setUpstreamDataSourceFactory(upstream)
        // A surah kept on the phone is read from its file, the others from the cache or the server.
        val source = DataSource.Factory { KeptOrStreamed(FileDataSource(), cached.createDataSource()) }
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(source))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            // Unplugging the headphones pauses, as everywhere on Android.
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        session = MediaSession.Builder(this, player).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: android.content.Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) stopSelf()
    }

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        super.onDestroy()
    }

    companion object {
        @Volatile private var shared: SimpleCache? = null

        fun cache(context: Context): SimpleCache = shared ?: synchronized(this) {
            shared ?: SimpleCache(
                File(context.cacheDir, "recitations"),
                LeastRecentlyUsedCacheEvictor(1L shl 30),
                StandaloneDatabaseProvider(context)
            ).also { shared = it }
        }
    }
}

/** Reads a file kept in the app's own folder directly, anything else through [streamed]. */
@OptIn(UnstableApi::class)
private class KeptOrStreamed(private val kept: DataSource, private val streamed: DataSource) : DataSource {

    private var current: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        kept.addTransferListener(transferListener)
        streamed.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val source = if (dataSpec.uri.scheme == "file") kept else streamed
        current = source
        return source.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = current!!.read(buffer, offset, length)

    override fun getUri(): android.net.Uri? = current?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = current?.responseHeaders ?: emptyMap()

    override fun close() {
        current?.close()
        current = null
    }
}
