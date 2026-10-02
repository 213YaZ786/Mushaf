package org.mushaf.app.feature.play

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlin.random.Random
import kotlinx.coroutines.launch
import androidx.compose.animation.core.tween
import org.mushaf.app.ui.component.reducedMotion
import org.mushaf.app.ui.component.Glide
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.PI
import kotlinx.coroutines.coroutineScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.runtime.key
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.VectorConverter
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.data.audio.Recitations
import org.mushaf.app.core.quran.Riwayah
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.first
import org.koin.compose.koinInject
import org.mushaf.app.ui.theme.quranFont
import org.mushaf.app.R
import org.mushaf.app.core.play.Games
import org.mushaf.app.core.quran.Arabic
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Word
import org.mushaf.app.core.stt.Heard
import org.mushaf.app.data.play.Star
import org.mushaf.app.data.play.Stars
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.data.stt.Recogniser
import org.mushaf.app.feature.listen.Listen
import org.mushaf.app.feature.recite.Recite
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** An ayah and its words, the end sign left out. */
private class Ayah(val key: AyahKey, val words: List<Word>)

/** Sheikh al-Minshawi with a child repeating after him, and Sheikh al-Husary's slow teaching recitation. */
private const val KIDS_REPEAT = 168
private const val TEACHING = 12

/** Sheikh al-Husary's Warsh, clear and measured: Warsh has no recording with a child. */
private const val WARSH_TEACHING = Recitations.WARSH_BASE + 120

private fun teacher(r: Riwayah) = if (r == Riwayah.WARSH) WARSH_TEACHING else TEACHING

/** What the listening game does in [r]. */
private fun listenDetail(r: Riwayah) =
    if (r == Riwayah.WARSH) "Sheikh al-Husary recites an ayah, then it is your turn." else Game.LISTEN.detail

/** A surah's games: its menu, then the game chosen, on the same screen. */
@Composable
fun SurahGames(surah: Int, onBack: () -> Unit) {
    val quran: Quran = koinInject()
    val stars: Stars = koinInject()
    val won by stars.stars.collectAsState()
    val haptics = rememberHaptics()
    var game by rememberSaveable { mutableStateOf<Game?>(null) }
    val names = remember { FontFamily(Font(R.font.surah_names)) }
    val riwayah = koinInject<SettingsStore>().settings.collectAsState().value.riwayah
    val name by produceState("", surah) { value = quran.surah(surah).name }
    val ayat by produceState<List<Ayah>?>(null, surah) {
        val s = quran.surah(surah)
        val words = (s.pages.first()..s.pages.last()).flatMap { quran.page(it).words }.filter { it.key.surah == surah && it.key.ayah > 0 && !it.end }
        value = words.groupBy { it.key }.map { (k, w) -> Ayah(k, w.sortedBy { it.position }) }
    }
    BackHandler(enabled = game != null) { game = null }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop(
                title = game?.title ?: name,
                leading = { FloatingAction(AppIcons.ArrowBack, "Back", { if (game != null) game = null else onBack() }) }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LocalReadableInset.current + 16.dp)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding() + 8.dp))
            val list = ayat
            if (list == null) {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { LoadingMark(size = 64.dp) }
            } else when (game) {
                null -> {
                    Text(
                        "%03d".format(surah),
                        style = TextStyle(fontFamily = names, fontSize = 64.sp, textAlign = TextAlign.Center),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val got = won[surah].orEmpty()
                    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
                        for (star in Star.entries) {
                            Icon(
                                if (star in got) AppIcons.Star else AppIcons.StarOutline,
                                contentDescription = null,
                                tint = if (star in got) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                    for (g in Game.entries) {
                        ZoneSurface(
                            shape = RoundedCornerShape(24.dp),
                            onClick = { haptics.tick(); game = g },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                        ) {
                            Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(g.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                    Text(g.title, style = MaterialTheme.typography.titleMedium)
                                    Text(if (g == Game.LISTEN) listenDetail(riwayah) else g.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                g.star?.let { star ->
                                    Icon(
                                        if (star in got) AppIcons.Star else AppIcons.StarOutline,
                                        contentDescription = null,
                                        tint = if (star in got) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                    }
                }
                Game.LISTEN -> if (riwayah == Riwayah.WARSH) {
                    YourTurnGame(list, WARSH_TEACHING) { stars.win(surah, Star.LISTENED) }
                } else {
                    ListenGame(surah, list) { stars.win(surah, Star.LISTENED) }
                }
                Game.BUILD -> BuildGame(list) { stars.win(surah, Star.BUILT) }
                Game.MISSING -> MissingGame(list)
                Game.WHICH -> WhichGame(list, teacher(riwayah))
                Game.RECITE -> ReciteGame(list) { stars.win(surah, Star.RECITED) }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun hafs() = quranFont()

/** Right to left, as the ayah is written. */
@Composable
private fun Rtl(content: @Composable () -> Unit) =
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)

@Composable
private fun Done(text: String, onAgain: () -> Unit) {
    val haptics = rememberHaptics()
    val reduce = reducedMotion()
    // Three stars of eight points open one after the other, each with a burst.
    val pops = remember { List(3) { Animatable(0f) } }
    val bursts = remember { List(3) { Animatable(0f) } }
    LaunchedEffect(Unit) {
        haptics.done()
        if (reduce) { pops.forEach { it.snapTo(1f) }; bursts.forEach { it.snapTo(1f) }; return@LaunchedEffect }
        coroutineScope {
            for (i in 0 until 3) {
                launch { pops[i].animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
                launch { bursts[i].animateTo(1f, tween(650, easing = Glide)) }
                delay(330)
            }
        }
    }
    val starColor = MaterialTheme.colorScheme.tertiary
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            for (i in 0 until 3) EightStar(pops[i].value, bursts[i].value, starColor, Modifier.size(if (i == 1) 84.dp else 64.dp))
        }
        Text(text, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(16.dp))
        BoldButton(filled = true, onClick = onAgain) { Text("Again") }
    }
}

/**
 * A star of eight points, two squares over each other as the hizb sign is
 * drawn, popping in as [pop] goes from 0 to 1; small diamonds fly out of
 * it as [burst] does.
 */
@Composable
private fun EightStar(pop: Float, burst: Float, color: Color, modifier: Modifier) {
    Canvas(modifier) {
        val c = center
        val r = size.minDimension / 2f
        if (burst in 0.01f..0.99f) {
            for (k in 0 until 8) {
                val a = k / 8f * 2f * PI.toFloat()
                val d = r * (0.6f + 0.9f * burst)
                val s = r * 0.12f * (1f - burst * 0.6f)
                rotate(45f, Offset(c.x + cos(a) * d, c.y + sin(a) * d)) {
                    drawRect(color, Offset(c.x + cos(a) * d - s / 2, c.y + sin(a) * d - s / 2), Size(s, s), alpha = 1f - burst)
                }
            }
        }
        if (pop <= 0f) return@Canvas
        val side = r * 1.25f * pop
        rotate(-40f * (1f - pop), c) {
            for (turn in listOf(0f, 45f)) rotate(turn, c) {
                drawRoundRect(color, Offset(c.x - side / 2, c.y - side / 2), Size(side, side), CornerRadius(side * 0.08f))
            }
            drawCircle(Color.White.copy(alpha = 0.35f), radius = side * 0.18f, center = c)
        }
    }
}

/** The whole surah, recited with a child repeating; the ayah heard is lit. */
@Composable
private fun ListenGame(surah: Int, ayat: List<Ayah>, onWon: () -> Unit) {
    val listen: Listen = koinInject()
    val state by listen.state.collectAsState()
    val font = hafs()
    var started by remember { mutableStateOf(false) }
    DisposableEffect(Unit) { onDispose { listen.stop() } }
    LaunchedEffect(state.key) {
        if (started && state.key == ayat.last().key) onWon()
    }
    FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        BoldButton(filled = true, onClick = {
            started = true
            if (state.playing) listen.toggle() else listen.playUntil(ayat.first().key, ayat.last().key, KIDS_REPEAT)
        }) { Text(if (state.playing) "Pause" else "Listen") }
    }
    for (a in ayat) {
        val lit = state.key == a.key
        val color by animateColorAsState(if (lit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, label = "lit")
        ZoneSurface(shape = RoundedCornerShape(20.dp), accent = lit, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Text(
                a.words.joinToString(" ") { it.text } + " " + Arabic.digits(a.key.ayah),
                style = TextStyle(fontFamily = font, fontSize = 30.sp, lineHeight = 56.sp, textAlign = TextAlign.Center),
                color = color,
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        }
    }
}

/**
 * Repeat after the sheikh, ayah by ayah: he recites one, then the child's
 * turn lasts as long as his did, the ayah lit; then the next.
 */
@Composable
private fun YourTurnGame(ayat: List<Ayah>, reciter: Int, onWon: () -> Unit) {
    val listen: Listen = koinInject()
    val state by listen.state.collectAsState()
    val font = hafs()
    var running by remember { mutableStateOf(false) }
    var turn by remember { mutableStateOf<AyahKey?>(null) }
    DisposableEffect(Unit) { onDispose { listen.stop() } }
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        for (a in ayat) {
            turn = null
            listen.playOnce(a.key, reciter)
            // His recitation: from the moment it sounds until it stops.
            withTimeoutOrNull(20_000) { listen.state.first { it.playing } } ?: break
            val start = System.currentTimeMillis()
            listen.state.first { !it.playing }
            val took = System.currentTimeMillis() - start
            turn = a.key
            delay((took * 1.15).toLong().coerceIn(1_500, 25_000))
        }
        if (turn == ayat.last().key) onWon()
        turn = null
        running = false
    }
    FlowRow(horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        BoldButton(filled = true, onClick = {
            if (running) { listen.stop(); running = false; turn = null } else running = true
        }) { Text(if (running) "Stop" else "Start") }
    }
    for (a in ayat) {
        val heard = running && turn == null && state.key == a.key
        val mine = turn == a.key
        val color by animateColorAsState(
            when {
                mine -> MaterialTheme.colorScheme.tertiary
                heard -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.onSurface
            },
            label = "turn"
        )
        ZoneSurface(shape = RoundedCornerShape(20.dp), accent = heard || mine, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Text(
                    a.words.joinToString(" ") { it.text } + " " + Arabic.digits(a.key.ayah),
                    style = TextStyle(fontFamily = font, fontSize = 30.sp, lineHeight = 56.sp, textAlign = TextAlign.Center),
                    color = color,
                    modifier = Modifier.fillMaxWidth()
                )
                if (mine) Text("Your turn", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
            }
        }
    }
}

/** Each ayah's words shuffled: tapped back in their order, a wrong word shakes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BuildGame(ayat: List<Ayah>, onWon: () -> Unit) {
    val haptics = rememberHaptics()
    val font = hafs()
    var index by remember { mutableIntStateOf(0) }
    var round by remember { mutableIntStateOf(0) }
    if (index >= ayat.size) {
        LaunchedEffect(Unit) { onWon() }
        Done("The whole surah, built!") { index = 0; round++ }
        return
    }
    val a = ayat[index]
    val puzzle = remember(a.key, round) { Games.puzzle(a.key, a.words.map { it.text }, Random(System.nanoTime())) }
    val placed = remember(a.key, round) { mutableStateListOf<Int>() }
    var wrong by remember(a.key, round) { mutableStateOf(-1) }
    val reduce = reducedMotion()
    // Where each tile was when tapped, for the word to fly from there to its place.
    val tileAt = remember(a.key, round) { HashMap<Int, Offset>() }
    val flyFrom = remember(a.key, round) { HashMap<Int, Offset>() }
    Text("Ayah ${index + 1} of ${ayat.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp))
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp)) {
        Rtl {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                for (i in placed) key(i) {
                    val fly = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                    var ready by remember { mutableStateOf(reduce) }
                    val scope = rememberCoroutineScope()
                    Text(
                        puzzle.answer[i].text,
                        style = TextStyle(fontFamily = font, fontSize = 32.sp),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .onGloballyPositioned { c ->
                                if (ready) return@onGloballyPositioned
                                val start = flyFrom[i]?.minus(c.boundsInRoot().center)
                                scope.launch {
                                    if (start != null) fly.snapTo(start)
                                    ready = true
                                    fly.animateTo(Offset.Zero, spring(dampingRatio = 0.55f, stiffness = 420f))
                                }
                            }
                            .graphicsLayer {
                                alpha = if (ready) 1f else 0f
                                translationX = fly.value.x
                                translationY = fly.value.y
                            }
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Rtl {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            puzzle.shuffled.forEachIndexed { slot, tile ->
                if (tile.position !in placed) {
                    val shake = remember(slot, wrong) { Animatable(0f) }
                    LaunchedEffect(wrong) { if (wrong == slot) { shake.animateTo(1f, spring(dampingRatio = 0.2f, stiffness = 2000f)); shake.snapTo(0f) } }
                    FloatingPane(
                        shape = RoundedCornerShape(18.dp),
                        onClick = {
                            // The next word, or the same word written alike further on.
                            val next = puzzle.answer[placed.size]
                            if (tile.position == next.position || Arabic.normalize(tile.text) == Arabic.normalize(next.text) && next.position !in placed) {
                                haptics.tick()
                                val at = if (tile.position == next.position) tile.position else next.position
                                tileAt[slot]?.let { flyFrom[at] = it }
                                placed += at
                                if (placed.size == puzzle.answer.size) { haptics.done(); index++ }
                            } else {
                                haptics.reject(); wrong = slot
                            }
                        },
                        modifier = Modifier
                            .onGloballyPositioned { tileAt[slot] = it.boundsInRoot().center }
                            .graphicsLayer { rotationZ = shake.value * 9f }
                    ) {
                        Text(tile.text, style = TextStyle(fontFamily = font, fontSize = 30.sp), modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                }
            }
        }
    }
}

/** One word hidden in each ayah, to choose among three. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MissingGame(ayat: List<Ayah>) {
    val haptics = rememberHaptics()
    val font = hafs()
    var index by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember(index) { mutableStateOf<String?>(null) }
    if (index >= ayat.size) {
        Done("$score of ${ayat.size} found") { index = 0; score = 0 }
        return
    }
    val a = ayat[index]
    val pool = remember(ayat) { ayat.flatMap { it.words.map { w -> w.text } } }
    val q = remember(a.key) { Games.missing(a.key, a.words.map { it.text }, pool, Arabic::normalize, Random(System.nanoTime())) }
    val answer = q.words[q.hole]
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Rtl {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                q.words.forEachIndexed { i, w ->
                    Text(
                        if (i == q.hole && picked == null) "⟨ ? ⟩" else w,
                        style = TextStyle(fontFamily = font, fontSize = 30.sp),
                        color = if (i == q.hole) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Rtl {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally), modifier = Modifier.fillMaxWidth()) {
            for (c in q.choices) {
                val right = Arabic.normalize(c) == Arabic.normalize(answer)
                FloatingPane(
                    shape = RoundedCornerShape(18.dp),
                    accent = picked != null && right,
                    onClick = {
                        if (picked != null) return@FloatingPane
                        picked = c
                        if (right) { haptics.done(); score++ } else haptics.reject()
                    }
                ) {
                    Text(
                        c,
                        style = TextStyle(fontFamily = font, fontSize = 30.sp),
                        color = if (picked == c && !right) MaterialTheme.colorScheme.error else Color.Unspecified,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
    if (picked != null) {
        Row(Modifier.fillMaxWidth().padding(top = 20.dp), horizontalArrangement = Arrangement.Center) {
            BoldButton(filled = true, onClick = { index++ }) { Text("Next") }
        }
    }
}

/** An ayah heard, to find among three written ones. */
@Composable
private fun WhichGame(ayat: List<Ayah>, reciter: Int) {
    val listen: Listen = koinInject()
    val haptics = rememberHaptics()
    val font = hafs()
    var round by remember { mutableIntStateOf(0) }
    var score by remember { mutableIntStateOf(0) }
    var picked by remember(round) { mutableStateOf<AyahKey?>(null) }
    DisposableEffect(Unit) { onDispose { listen.stop() } }
    val rounds = minOf(5, ayat.size)
    if (round >= rounds) {
        Done("$score of $rounds heard right") { round = 0; score = 0 }
        return
    }
    val q = remember(round) { Games.which(ayat.map { it.key }, Random(System.nanoTime())) }
    LaunchedEffect(round) { listen.playOnce(q.answer, reciter) }
    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
        FloatingAction(AppIcons.VolumeUp, "Hear it again", { listen.playOnce(q.answer, reciter) })
    }
    for (k in q.choices) {
        val a = ayat.first { it.key == k }
        val right = k == q.answer
        ZoneSurface(
            shape = RoundedCornerShape(20.dp),
            accent = picked != null && right,
            onClick = {
                if (picked != null) return@ZoneSurface
                picked = k
                if (right) { haptics.done(); score++ } else haptics.reject()
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Text(
                a.words.joinToString(" ") { it.text },
                style = TextStyle(fontFamily = font, fontSize = 26.sp, lineHeight = 48.sp, textAlign = TextAlign.Center),
                color = if (picked == k && !right) MaterialTheme.colorScheme.error else Color.Unspecified,
                modifier = Modifier.fillMaxWidth().padding(14.dp)
            )
        }
    }
    if (picked != null) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
            BoldButton(filled = true, onClick = { round++ }) { Text("Next") }
        }
    }
}

/** The surah recited to the phone from memory: each word shows as it is said. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReciteGame(ayat: List<Ayah>, onWon: () -> Unit) {
    val recite: Recite = koinInject()
    val recogniser: Recogniser = koinInject()
    val state by recite.state.collectAsState()
    val ready by recogniser.ready.collectAsState()
    val haptics = rememberHaptics()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val font = hafs()
    val words = remember(ayat) { ayat.flatMap { it.words } }
    var fetching by remember { mutableStateOf<String?>(null) }
    val start = { recite.start(words.map { it.text }) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) start() }
    DisposableEffect(Unit) { onDispose { recite.reset() } }
    val done = state.next >= words.size && words.isNotEmpty()
    val skipped = state.marks.count { it.value == Heard.SKIPPED }
    LaunchedEffect(done) { if (done && skipped <= 1) { haptics.done(); onWon() } }

    Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.Center) {
        when {
            fetching != null -> Text(fetching!!, style = MaterialTheme.typography.bodyMedium)
            !ready -> BoldButton(filled = true, onClick = {
                scope.launch {
                    fetching = "Downloading the speech model…"
                    runCatching { recogniser.install { fetching = "Downloading the speech model · ${it shr 20} of 80 MB" } }
                        .onFailure { haptics.reject() }
                    fetching = null
                }
            }) { Text("Get the speech model, 80 MB") }
            state.listening -> BoldButton(onClick = { recite.stop() }) { Text("Stop") }
            else -> BoldButton(filled = true, onClick = {
                recite.reset()
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                else askMic.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text(if (done) "Again" else "Start reciting") }
        }
    }
    if (done) Text(
        if (skipped <= 1) "Well done, the whole surah!" else "$skipped words passed over. Again?",
        style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
    )
    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Rtl {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(16.dp)) {
                words.forEachIndexed { i, w ->
                    val mark = state.marks[i]
                    Text(
                        w.text,
                        style = TextStyle(fontFamily = font, fontSize = 30.sp),
                        color = when (mark) {
                            Heard.RIGHT -> MaterialTheme.colorScheme.primary
                            Heard.CLOSE -> MaterialTheme.colorScheme.tertiary
                            Heard.SKIPPED -> MaterialTheme.colorScheme.error
                            null -> Color.Transparent
                        },
                        modifier = Modifier.padding(2.dp)
                    )
                }
            }
        }
    }
}
