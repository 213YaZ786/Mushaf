package org.mushaf.app.feature.hifz

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.mushaf.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mushaf.app.core.hifz.Likeness
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.hifz.Hifz
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.EmptyZone
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.icon.AppIcons
import org.mushaf.app.ui.theme.quranFont

/** Two ayat that read alike, each with its words. */
private class Pair2(val a: AyahKey, val aWords: List<String>, val b: AyahKey, val bWords: List<String>)

/**
 * The ayat that read alike (mutashabihat) among what is known by heart, or
 * across the Quran when nothing is marked known: each pair face to face,
 * the words that set them apart in the accent, so they are told apart.
 */
@Composable
fun SimilarScreen(onBack: () -> Unit, onOpen: (AyahKey) -> Unit) {
    val hifz: Hifz = koinInject()
    val quran: Quran = koinInject()
    val font = quranFont()
    val pairs by produceState<List<Pair2>?>(null) {
        if (quran.riwayah != Riwayah.HAFS) { value = emptyList(); return@produceState }
        val known = hifz.knownSet()
        val keys = (if (known.isEmpty()) quran.ayat().map { it.key } else known.sorted())
        val out = ArrayList<Pair2>()
        val seen = HashSet<String>()
        suspend fun words(k: AyahKey) = quran.page(quran.pageOf(k)).words.filter { it.key == k && !it.end }.map { it.text }
        for (k in keys) {
            for (s in quran.similar(k)) {
                val other = AyahKey.parse(s.substringBefore('+')) ?: continue
                if (other == k) continue
                val id = listOf(k.toString(), other.toString()).sorted().joinToString("|")
                if (!seen.add(id)) continue
                out += Pair2(k, words(k), other, words(other))
                if (out.size >= 60) break
            }
            if (out.size >= 60) break
        }
        value = out
    }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(stringResource(R.string.similar_ayat), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        val list = pairs
        when {
            list == null -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 40.dp),
                contentAlignment = androidx.compose.ui.Alignment.TopCenter
            ) { LoadingMark(size = 56.dp) }
            list.isEmpty() -> Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding() + 16.dp)) {
                EmptyZone(
                    stringResource(R.string.no_similar),
                    if (quran.riwayah == Riwayah.HAFS) stringResource(R.string.no_similar_known) else stringResource(R.string.similar_hafs_only),
                    icon = AppIcons.MenuBook
                )
            }
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding() + 8.dp,
                    start = LocalReadableInset.current + 12.dp,
                    end = LocalReadableInset.current + 12.dp,
                    bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        stringResource(R.string.similar_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    )
                }
                items(list, key = { it.a.toString() + "|" + it.b.toString() }) { p ->
                    val (inA, inB) = Likeness.shared(p.aWords, p.bWords)
                    ZoneSurface(shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            AyahLine(p.a, p.aWords, inA, font, onOpen)
                            HorizontalDivider(Modifier.padding(vertical = 10.dp))
                            AyahLine(p.b, p.bWords, inB, font, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AyahLine(key: AyahKey, words: List<String>, shared: BooleanArray, font: androidx.compose.ui.text.font.FontFamily, onOpen: (AyahKey) -> Unit) {
    val quran: Quran = koinInject()
    val surah by produceState("", key.surah) { value = quran.surah(key.surah).name }
    Text(
        "$surah $key",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        // A tap opens it in the mushaf.
        modifier = Modifier.clickable { onOpen(key) }.padding(bottom = 4.dp)
    )
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
            // Only where the two part ways: around the passage they share, not the rest of a longer ayah.
            val first = shared.indexOfFirst { it }
            val last = shared.indexOfLast { it }
            val zone = if (first < 0) words.indices else maxOf(0, first - 2)..minOf(words.lastIndex, last + 2)
            words.forEachIndexed { i, w ->
                val apart = !shared.getOrElse(i) { true } && i in zone
                Text(
                    w,
                    style = TextStyle(fontFamily = font, fontSize = 26.sp),
                    color = if (apart) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                    modifier = if (apart) Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp))
                        .padding(horizontal = 4.dp) else Modifier
                )
            }
        }
    }
}
