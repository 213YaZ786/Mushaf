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
import org.mushaf.app.Shown
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
/** When the recitation stops by itself: at a time (millis), or at the end of the surah. */
sealed interface Sleep {
    data class At(val at: Long, val minutes: Int) : Sleep
    data object SurahEnd : Sleep
}

data class ListenState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val failed: Boolean = false,
    /** The failure came from the network, for a surah not kept on the phone. */
    val notKept: Boolean = false,
    val heard: Heard? = null,
    /** How many more times the ayah being heard comes again. */
    val repeatsLeft: Int = 0,
    /** Silent after an ayah, for the reader to say it. */
    val yourTurn: Boolean = false,
    /** The meaning of the ayah just heard, being read aloud. */
    val meaning: Boolean = false
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
    private val translations: org.mushaf.app.data.quran.Translations,
    private val speaker: org.mushaf.app.core.speech.Speaker,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(ListenState())
    val state: StateFlow<ListenState> = _state.asStateFlow()

    private var controller: MediaController? = null
    private var audio: SurahAudio? = null
    private var ticker: Job? = null
    /** True while the recitation is quiet for the reader's turn: the pause is ours, the loop goes on. */
    private var turnPause = false
    /** The ayah whose repeats are being counted. */
    private var counting: AyahKey? = null
    /** A range heard again and again, until stopped. */
    private var range: ClosedRange<AyahKey>? = null
    /** Pauses once past this ayah: one ayah heard, for a game. */
    private var stopAfter: AyahKey? = null
    /** Where the recording in the player is cut: the end of [stopAfter], null when it runs whole. */
    private var cut: Long? = null

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
                    if (isPlaying) tick() else if (!turnPause) ticker?.cancel()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    _state.update { it.copy(loading = playbackState == Player.STATE_BUFFERING) }
                    if (playbackState == Player.STATE_ENDED) scope.launch { surahEnded() }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    // The 2000s are the network's and the files' errors; a kept file is read from the phone.
                    val network = error.errorCode in 2000..2999 && c.currentMediaItem?.localConfiguration?.uri?.scheme != "file"
                    _state.update { it.copy(failed = true, notKept = network, loading = false) }
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
            _state.update { it.copy(active = true, loading = true, failed = false, notKept = false) }
            val there = runCatching { load(key.surah, key.ayah) }.getOrElse { e ->
                val offline = e is java.io.IOException && recitations.local(reciterFor ?: settings.reciter(), key.surah) == null
                _state.update { it.copy(loading = false, failed = true, notKept = offline) }
                return@launch
            }
            val c = controller()
            val a = audio ?: return@launch
            val start = a.ayat.firstOrNull { it.ayah == key.ayah } ?: a.ayat.first()
            startRepeats(start)
            if (there) c.seekTo(start.from)
            c.setPlaybackSpeed(settings.current.speed)
            c.play()
        }
    }

    /**
     * The surah's recording in the player, ready at [from]. True when it was
     * there already: then it is moved with a seek. A new recording gets its
     * place with it, as the player drops a seek asked before it can seek.
     */
    private suspend fun load(surah: Int, from: Int): Boolean {
        val reciter = reciterFor ?: settings.reciter()
        val a = audio?.takeIf { it.surah == surah && it.reciter == reciter } ?: recitations.surah(reciter, surah)
        // One ayah or a passage heard once: the recording is cut at its end, so the
        // voice stops there to the sample, before the next ayah begins. A pause
        // sent when the end is seen arrives tens of milliseconds late, and the
        // next ayah's first sound got through.
        val end = stopAfter?.takeIf { it.surah == surah }?.let { stop -> a.ayat.firstOrNull { it.key == stop }?.to }
        if (audio === a && cut == end) return true
        // The recording's place comes from the API: played only from a known server, encrypted.
        val uri = Uri.parse(a.url)
        if (recitations.local(reciter, surah) == null && (uri.scheme != "https" || !Net.allowed(uri.host.orEmpty().lowercase()))) {
            error("recording not on a known server")
        }
        val s = quran.surah(surah)
        val item = MediaItem.Builder()
            .setUri(recitations.local(reciter, surah)?.let { Uri.fromFile(it) } ?: Uri.parse(a.url))
            .apply { if (end != null) setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(end).build()) }
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle("${s.n}. ${s.title}")
                    .setArtist(Recitations.reciter(reciter).name)
                    .build()
            )
            .build()
        val c = controller()
        val start = a.ayat.firstOrNull { it.ayah == from } ?: a.ayat.first()
        c.setMediaItem(item, start.from)
        c.prepare()
        audio = a
        cut = end
        return false
    }

    /** Where the recitation stands, kept to go on from there another time. */
    private fun keepPlace() {
        val key = _state.value.key ?: return
        settings.update { it.copy(lastHeard = key.toString()) }
    }

    fun toggle() {
        val c = controller ?: return
        // A tap during the reader's turn ends it: the recitation goes on at once.
        if (turnPause) { turnPause = false; speaker.stop(); _state.update { it.copy(yourTurn = false, meaning = false) }; c.play(); return }
        if (c.isPlaying) { c.pause(); keepPlace() } else if (c.playbackState == Player.STATE_ENDED && cut != null) {
            // Play again after an ayah heard once: on from that ayah, the recording whole.
            _state.value.key?.let { play(it) }
        } else {
            c.setPlaybackSpeed(settings.current.speed)
            c.play()
        }
    }

    fun stop() {
        turnPause = false
        speaker.release()
        keepPlace()
        setSleep(null)
        ticker?.cancel()
        controller?.stop()
        controller?.clearMediaItems()
        audio = null
        cut = null
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

    /** Turns the meaning read aloud after each ayah on or off. */
    fun setSpeakMeaning(on: Boolean) {
        settings.update { it.copy(speakMeaning = on) }
        if (!on) speaker.stop()
    }

    /**
     * The meaning of [key] to read aloud: the translation chosen in the
     * phone's language when there is one, else the first chosen; Hafs's
     * numbering, so a Warsh ayah takes the Hafs ayat it holds.
     */
    private suspend fun meaningOf(key: AyahKey): Pair<String, String?>? {
        val chosen = settings.current.translations
        val here = java.util.Locale.getDefault().language
        val language = { id: String -> translations.info(id)?.language?.let { org.mushaf.app.core.common.Languages.code(it) } }
        val id = chosen.firstOrNull { language(it) == here } ?: chosen.firstOrNull() ?: return null
        val parts = mutableListOf<String>()
        for (k in quran.hafsKeys(key)) parts += translations.text(id, k)
        val text = parts.joinToString(" ").trim()
        return if (text.isEmpty()) null else text to language(id)
    }

    /** Turns the reader's turn after each ayah on or off. */
    fun setYourTurn(on: Boolean) {
        settings.update { it.copy(yourTurn = on) }
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
                // An ayah ends where the next one begins. Just before that end the
                // next one counts as heard: a pause, a repeat or the reader's turn
                // then comes in the silence after the last word, before a sound
                // of the next ayah gets through.
                val now = Timing.at(a, ms)
                val heard = now?.takeIf { ms < it.ayah.to - END_LEAD || it.ayah == a.ayat.last() }
                    ?: now?.let { Timing.at(a, it.ayah.to) }
                if (heard != null) {
                    val stop = stopAfter
                    if (stop != null && heard.ayah.key > stop) {
                        // A cut recording ends by itself, right at the ayah's end.
                        if (cut == null) c.pause()
                        stopAfter = null
                        break
                    }
                    if (heard.ayah.key != counting) {
                        val prev = a.ayat.firstOrNull { it.key == counting }
                        // After an ayah: its meaning read aloud, then the reader's turn, as asked; then on.
                        val s = settings.current
                        if ((s.speakMeaning || s.yourTurn) && prev != null && heard.ayah.from >= prev.to) {
                            turnPause = true
                            c.pause()
                            if (s.speakMeaning) {
                                _state.update { it.copy(meaning = true) }
                                meaningOf(prev.key)?.let { (text, language) -> speaker.say(text, language) }
                                _state.update { it.copy(meaning = false) }
                                if (!turnPause) break
                            }
                            if (s.yourTurn) {
                                _state.update { it.copy(yourTurn = true) }
                                delay(((prev.to - prev.from) / c.playbackParameters.speed).toLong())
                                _state.update { it.copy(yourTurn = false) }
                                if (!turnPause) break
                            }
                            turnPause = false
                            c.play()
                        }
                        // Past the end of the ayah being repeated: back to its start.
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
                // On screen the word heard is lit, so the voice is followed closely; out of sight
                // (screen off, another app) only the end of the ayah matters: one wake-up per ayah.
                // Either way the loop wakes in time for the end of the ayah, never after it.
                val untilEnd = heard?.let { ((it.ayah.to - END_LEAD - c.currentPosition) / c.playbackParameters.speed).toLong() }
                delay(
                    when {
                        untilEnd == null -> 50L
                        Shown.now -> untilEnd.coerceIn(5L, 50L)
                        else -> untilEnd.coerceIn(5L, 2_000L)
                    }
                )
            }
        }
    }

    private val _sleep = MutableStateFlow<Sleep?>(null)

    /** When the recitation stops by itself, if it does. */
    val sleep: StateFlow<Sleep?> = _sleep.asStateFlow()
    private var sleepJob: Job? = null

    fun setSleep(s: Sleep?) {
        sleepJob?.cancel()
        _sleep.value = s
        if (s is Sleep.At) sleepJob = scope.launch {
            delay((s.at - System.currentTimeMillis()).coerceAtLeast(0))
            fadeAndPause()
        }
    }

    /** The voice lowered over ten seconds, then paused, the volume as it was for the next time. */
    private suspend fun fadeAndPause() {
        val c = controller ?: return
        val volume = c.volume
        for (i in 1..20) {
            c.volume = volume * (1f - i / 20f)
            delay(500)
        }
        c.pause()
        c.volume = volume
        _sleep.value = null
    }

    /** At the end of a surah: its last ayah repeated if asked, else on to the next surah. */
    private suspend fun surahEnded() {
        val a = audio ?: return
        if (cut != null) {
            // The end of the ayah asked for, not of the surah.
            stopAfter = null
            _state.update { it.copy(playing = false) }
            return
        }
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
        if (_sleep.value == Sleep.SurahEnd) {
            // Asked to stop at the end of the surah.
            _sleep.value = null
            _state.update { it.copy(playing = false) }
            return
        }
        if (a.surah < 114 && settings.current.continuePlaying) play(AyahKey(a.surah + 1, 1)) else _state.update { it.copy(playing = false) }
    }
}

/**
 * How long before an ayah's end the next one counts as heard, in the
 * recording's milliseconds: a pause or a seek takes tens of milliseconds to
 * reach the player, and the last word ends 100 to 200 ms before the next
 * ayah in the timed recordings.
 */
private const val END_LEAD = 100L
