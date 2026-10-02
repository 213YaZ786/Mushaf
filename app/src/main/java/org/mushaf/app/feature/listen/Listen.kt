package org.mushaf.app.feature.listen

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.mushaf.app.core.audio.AyahTime
import org.mushaf.app.core.audio.Heard
import org.mushaf.app.core.audio.PlaybackService
import org.mushaf.app.core.audio.SurahAudio
import org.mushaf.app.core.audio.Timing
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.core.net.Net
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.settings.SettingsStore

/** What the recitation is doing, for the screens. */
data class ListenState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    val heard: Heard? = null,
    /** How many more times the ayah being heard comes again. */
    val repeatsLeft: Int = 0
) {
    val key: AyahKey? get() = heard?.ayah?.key
}

/**
 * The recitation, as the app drives it: which ayah and word are heard,
 * each ayah repeated as many times as asked, a range repeated as a whole,
 * and on to the next surah at the end of one. The player itself lives in
 * PlaybackService, so it goes on in the background.
 */
class Listen(
    private val context: Context,
    private val recitations: Recitations,
    private val quran: Quran,
    private val settings: SettingsStore,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var audio: SurahAudio? = null
    private var ticker: Job? = null
    /** The ayah whose repeats are being counted. */
    private var counting: AyahKey? = null
    /** A range heard again and again, until stopped. */
    private var range: ClosedRange<AyahKey>? = null
    /** Pauses once past this ayah: one ayah heard, for a game. */
    private var stopAfter: AyahKey? = null

    /** For a game: another reciter than the reader's own, without changing it. */
    private var reciterFor: Int? = null

    /** Plays [key] once, then pauses; [reciter] for this time only. */
    fun playOnce(key: AyahKey, reciter: Int? = null) = playUntil(key, key, reciter)

    /** Plays from [from] to [to] once, then pauses; [reciter] for this time only. */
    fun playUntil(from: AyahKey, to: AyahKey, reciter: Int? = null) {
        if (reciterFor != reciter) audio = null
        reciterFor = reciter
        stopAfter = to
        play(from)
    }

    private suspend fun controller(): MediaController = controller ?: suspendCancellableCoroutine { cont ->
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = future.get()
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                    if (isPlaying) tick() else ticker?.cancel()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update { it.copy(loading = playbackState == Player.STATE_BUFFERING) }
                    if (playbackState == Player.STATE_ENDED) scope.launch { surahEnded() }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    _state.update { it.copy(failed = true, loading = false) }
                }
            })
            controller = c
            cont.resume(c)
        }, ContextCompat.getMainExecutor(context))
    }

    /** Plays from [key], in the reciter chosen. */
    fun play(key: AyahKey, until: AyahKey? = null) {
        range = until?.let { key..it }
        if (stopAfter == null || key > stopAfter!!) {
            stopAfter = null
            if (reciterFor != null) { reciterFor = null; audio = null }
        }
        scope.launch {
            _state.update { it.copy(active = true, loading = true, failed = false) }
            val ok = runCatching { load(key.surah) }.isSuccess
            if (!ok) {
                _state.update { it.copy(loading = false, failed = true) }
                return@launch
            }
            val c = controller()
            val a = audio ?: return@launch
            val start = a.ayat.firstOrNull { it.ayah == key.ayah } ?: a.ayat.first()
            startRepeats(start)
            c.seekTo(start.from)
            c.setPlaybackSpeed(settings.current.speed)
            c.play()
        }
    }

    private suspend fun load(surah: Int) {
        val reciter = reciterFor ?: settings.reciter()
        if (audio?.surah == surah && audio?.reciter == reciter) return
        val a = recitations.surah(reciter, surah)
        // The recording's place comes from the API: played only from a known server, encrypted.
        val uri = Uri.parse(a.url)
        if (recitations.local(reciter, surah) == null && (uri.scheme != "https" || !Net.allowed(uri.host.orEmpty().lowercase()))) {
            error("recording not on a known server")
        }
        val s = quran.surah(surah)
        val item = MediaItem.Builder()
            .setUri(recitations.local(reciter, surah)?.let { Uri.fromFile(it) } ?: Uri.parse(a.url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${s.n}. ${s.name}")
                    .setArtist(Recitations.reciter(reciter).name)
                    .build()
            )
            .build()
        val c = controller()
        c.setMediaItem(item)
        c.prepare()
        audio = a
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else {
            c.setPlaybackSpeed(settings.current.speed)
            c.play()
        }
    }

    fun stop() {
        ticker?.cancel()
        controller?.stop()
        controller?.clearMediaItems()
        audio = null
        range = null
        _state.value = ListenState()
    }

    /** The next ayah ([step] 1) or the one before ([step] -1). */
    fun skip(step: Int) {
        val now = _state.value.key ?: return
        scope.launch {
            val ayat = quran.ayat()
            val i = ayat.indexOfFirst { it.key == now } + step
            ayat.getOrNull(i)?.let { play(it.key) }
        }
    }

    fun setSpeed(speed: Float) {
        settings.update { it.copy(speed = speed) }
        controller?.setPlaybackSpeed(speed)
    }

    fun setRepeat(times: Int) {
        settings.update { it.copy(repeat = times) }
        _state.value.heard?.ayah?.let { startRepeats(it) }
    }

    /** A new reciter takes over at the ayah being heard. */
    fun setReciter(id: Int) {
        settings.update { if (id > Recitations.WARSH_BASE) it.copy(warshReciter = id) else it.copy(reciter = id) }
        val key = _state.value.key ?: return
        audio = null
        play(key, range?.endInclusive)
    }

    private fun startRepeats(ayah: AyahTime) {
        counting = ayah.key
        val times = if (stopAfter != null) 1 else settings.current.repeat
        _state.update { it.copy(repeatsLeft = if (times <= 0) Int.MAX_VALUE else times - 1) }
    }

    /** Follows the voice: the ayah and word heard, the repeats. */
    private fun tick() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                val c = controller ?: break
                val a = audio ?: break
                val ms = c.currentPosition
                val heard = Timing.at(a, ms)
                if (heard != null) {
                    val stop = stopAfter
                    if (stop != null && heard.ayah.key > stop) {
                        c.pause()
                        stopAfter = null
                        break
                    }
                    if (heard.ayah.key != counting) {
                        // Past the end of the ayah being repeated: back to its start.
                        val prev = a.ayat.firstOrNull { it.key == counting }
                        val left = _state.value.repeatsLeft
                        val r = range
                        when {
                            prev != null && left > 0 && heard.ayah.from >= prev.to -> {
                                _state.update { it.copy(repeatsLeft = if (left == Int.MAX_VALUE) left else left - 1) }
                                c.seekTo(prev.from)
                                delay(40)
                                continue
                            }
                            r != null && prev != null && prev.key == r.endInclusive && heard.ayah.key > r.endInclusive -> {
                                a.ayat.firstOrNull { it.key == r.start }?.let { c.seekTo(it.from); startRepeats(it) }
                                delay(40)
                                continue
                            }
                            else -> startRepeats(heard.ayah)
                        }
                    }
                    _state.update { it.copy(heard = heard) }
                }
                delay(50)
            }
        }
    }

    /** At the end of a surah: its last ayah repeated if asked, else on to the next surah. */
    private suspend fun surahEnded() {
        val a = audio ?: return
        val last = a.ayat.lastOrNull() ?: return
        val c = controller ?: return
        if (_state.value.repeatsLeft > 0) {
            _state.update { it.copy(repeatsLeft = it.repeatsLeft - 1) }
            c.seekTo(last.from)
            c.play()
            return
        }
        range?.let { r ->
            if (r.start.surah == a.surah) {
                a.ayat.firstOrNull { it.key == r.start }?.let { c.seekTo(it.from); startRepeats(it); c.play(); return }
            }
        }
        if (a.surah < 114 && settings.current.continuePlaying) play(AyahKey(a.surah + 1, 1)) else _state.update { it.copy(playing = false) }
    }
}
