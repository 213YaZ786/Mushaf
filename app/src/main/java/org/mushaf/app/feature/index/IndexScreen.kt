package org.mushaf.app.feature.index

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import org.mushaf.app.R
import org.mushaf.app.core.quran.QuranMeta
import org.mushaf.app.core.quran.Surah
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.feature.mushaf.Reader
import org.mushaf.app.feature.mushaf.hizbLabel
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

private enum class Part(val label: String) { SURAHS("Surahs"), JUZ("Juz"), HIZB("Hizb") }

/**
 * Where to go in the mushaf: the surahs, the 30 juz, the 60 hizb and their
 * quarters. The place being read is marked; a tap opens its page.
 */
@Composable
fun IndexScreen(onBack: () -> Unit) {
    val quran: Quran = koinInject()
    val reader: Reader = koinInject()
    val haptics = rememberHaptics()
    val meta by produceState(quran.metaNow, quran) { value = quran.meta() }
    val page by reader.page.collectAsState()
    var part by rememberSaveable { mutableStateOf(Part.SURAHS) }
    val hafs = remember { FontFamily(Font(R.font.uthmanic_hafs)) }

    val open = { target: Int ->
        haptics.tick()
        reader.go(target)
        onBack()
    }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop("Index", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) })
            Row(
                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                for (p in Part.entries) {
                    FloatingPane(shape = CircleShape, accent = p == part, onClick = { haptics.tick(); part = p }) {
                        Text(p.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp))
                    }
                }
            }
        }
    ) { padding ->
        val m = meta ?: return@FloatingFrame
        val inset = LocalReadableInset.current
        val list = rememberLazyListState()
        // Opens on the place being read.
        LaunchedEffect(part) {
            val index = when (part) {
                Part.SURAHS -> m.surahs.indexOfLast { it.firstPage <= page }
                Part.JUZ -> m.juz.indexOfLast { it.page <= page }
                Part.HIZB -> (m.quarters.indexOfLast { it.page <= page } / 4)
            }.coerceAtLeast(0)
            list.scrollToItem((index - 2).coerceAtLeast(0))
        }
        LazyColumn(
            state = list,
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, start = inset + 12.dp, end = inset + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            when (part) {
                Part.SURAHS -> items(m.surahs, key = { it.n }) { s ->
                    val here = page in s.pages.first()..s.pages.last()
                    SurahRow(s, hafs, here) { open(s.firstPage) }
                }
                Part.JUZ -> items(m.juz, key = { it.n }) { j ->
                    val next = m.juz.getOrNull(j.n)?.page ?: 605
                    val surah = m.surahs[j.ayah.surah - 1]
                    PlaceRow(
                        number = j.n,
                        title = "Juz ${j.n}",
                        detail = "${surah.name} ${j.ayah.surah}:${j.ayah.ayah} · page ${j.page}",
                        here = page in j.page until next
                    ) { open(j.page) }
                }
                Part.HIZB -> items((1..60).toList(), key = { it }) { h ->
                    HizbRow(h, m, page, open)
                }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun SurahRow(s: Surah, hafs: FontFamily, here: Boolean, onClick: () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(s.n)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(s.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${s.meaning} · ${s.ayat} ayat · ${if (s.meccan) "Meccan" else "Medinan"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(s.arabic, style = TextStyle(fontFamily = hafs, fontSize = 22.sp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun PlaceRow(number: Int, title: String, detail: String, here: Boolean, onClick: () -> Unit) {
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Number(number)
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** A hizb and its four quarters, which wrap under it on a narrow screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HizbRow(hizb: Int, m: QuranMeta, page: Int, open: (Int) -> Unit) {
    val quarters = m.quarters.subList((hizb - 1) * 4, hizb * 4)
    val end = m.quarters.getOrNull(hizb * 4)?.page ?: 605
    val here = page in quarters.first().page until end
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = here, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Number(hizb)
                Text(
                    "Hizb $hizb · Juz ${(hizb + 1) / 2}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 14.dp)
                )
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 10.dp)
            ) {
                for (q in quarters) {
                    val surah = m.surahs[q.ayah.surah - 1]
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { open(q.page) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Column {
                            Text(hizbLabel(q.n), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text("${surah.name} ${q.key} · p. ${q.page}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Number(n: Int) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        ZoneSurface(shape = CircleShape, modifier = Modifier.size(40.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(n.toString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
