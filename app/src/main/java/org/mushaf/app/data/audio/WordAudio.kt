package org.mushaf.app.data.audio

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mushaf.app.core.audio.PlaybackService
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.settings.SettingsStore

/** The word being heard, or the one that could not be. */
data class WordSound(val key: AyahKey, val position: Int, val playing: Boolean, val failed: Boolean = false) {
    fun of(w: Word) = key == w.key && position == w.position
}

/**
 * One word said on its own (Quran.com's word recordings, Hafs): each word's
 * file is named by its place, which tools/check_word_audio.py checks for the
 * whole Quran before a release; the few words without one are listed in
 * assets/quran/word_audio_missing.txt and offer no sound. A word heard once
 * is kept with the recitations, so it plays again offline.
 */
@OptIn(UnstableApi::class)
class WordAudio(private val context: Context, private val settings: SettingsStore) {

    private val missing: Set<String> by lazy {
        runCatching { context.assets.open("quran/word_audio_missing.txt").bufferedReader().readLines() }
            .getOrDefault(emptyList()).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
    }

    private val _sound = MutableStateFlow<WordSound?>(null)
    val sound: StateFlow<WordSound?> = _sound.asStateFlow()

    private var player: ExoPlayer? = null

    /** True when [w] can be heard on its own: a word of Hafs, not an ayah's number, with its recording. */
    fun available(w: Word): Boolean =
        settings.current.riwayah == Riwayah.HAFS && !w.end && w.key.ayah > 0 &&
            "${w.key.surah}:${w.key.ayah}:${w.position}" !in missing

    fun play(w: Word) {
        if (!available(w)) return
        val p = player ?: build().also { player = it }
        _sound.value = WordSound(w.key, w.position, playing = true)
        p.setMediaItem(MediaItem.fromUri("$BASE%03d_%03d_%03d.mp3".format(w.key.surah, w.key.ayah, w.position)))
        p.prepare()
        p.play()
    }

    private fun build(): ExoPlayer {
        val upstream = DefaultHttpDataSource.Factory().setUserAgent("Mushaf").setAllowCrossProtocolRedirects(false)
        val source = CacheDataSource.Factory().setCache(PlaybackService.cache(context)).setUpstreamDataSourceFactory(upstream)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(context).setDataSourceFactory(source))
            // A word pauses the recitation, as any other sound would.
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(),
                true
            )
            .build()
            .apply {
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        if (state == Player.STATE_ENDED) _sound.value = _sound.value?.copy(playing = false)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        _sound.value = _sound.value?.copy(playing = false, failed = true)
                    }
                })
            }
    }

    private companion object {
        /** On the allowed list of core/net/Net.kt; HTTPS only. */
        const val BASE = "https://audio.qurancdn.com/wbw/"
    }
}
