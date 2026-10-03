package org.mushaf.app.feature.mushaf

import org.mushaf.app.core.reading.Hijri
import java.text.NumberFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.compose.koinInject
import org.mushaf.app.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.core.reading.Days
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.core.quran.PAGES
import org.mushaf.app.data.khatmah.Khatmah
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.ui.component.BoldButton
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.QuietButton
import org.mushaf.app.ui.component.ZoneAlertDialog
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** The lengths offered for a khatmah, in days. */
private val LENGTHS = listOf(7, 10, 15, 30, 60)

/**
 * The khatmah, on glass over the page: started in a tap (the whole Quran in
 * so many days, from page 1 or from the page shown), then the day's pages,
 * how far the reading is, and a way to today's first page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KhatmahSheet(page: Int, onGo: (Int) -> Unit, onClose: () -> Unit) {
    val khatmah: Khatmah = koinInject()
    val quran: Quran = koinInject()
    val haptics = rememberHaptics()
    val plan by khatmah.plan.collectAsState()
    var fromHere by remember { mutableStateOf(false) }
    var ending by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), contentAlignment = Alignment.BottomCenter) {
        FloatingPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.khatmah), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FloatingAction(AppIcons.Close, stringResource(R.string.close), onClose)
                }
                Spacer(Modifier.size(8.dp))
                DaysRead()
                Spacer(Modifier.size(14.dp))
                val p = plan
                when {
                    p == null -> {
                        Text(stringResource(R.string.read_whole_in), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.size(12.dp))
                        val from = if (fromHere) page else 1
                        // The lengths share the whole width.
                        // In Ramadan, the reading that ends with the month comes first.
                        val ramadan = remember { Hijri.ramadanDaysLeft(khatmah.today()) }
                        val lengths = listOfNotNull(ramadan) + LENGTHS.filter { it != ramadan }
                        EvenRows(minSlot = 60.dp) {
                            for (d in lengths) {
                                val perDay = (PAGES - from + 1 + d - 1) / d
                                FloatingPane(shape = RoundedCornerShape(20.dp), accent = d == ramadan, onClick = { haptics.done(); khatmah.start(d, from) }, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(if (d == ramadan) stringResource(R.string.until_eid) else pluralStringResource(R.plurals.n_days, d, d), style = MaterialTheme.typography.labelLarge)
                                        Text(stringResource(R.string.pages_per_day, perDay), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (page > 1) {
                            Spacer(Modifier.size(12.dp))
                            EvenRows(minSlot = 120.dp) {
                                TextControl(stringResource(R.string.from_page_1), { fromHere = false }, accent = !fromHere)
                                TextControl(stringResource(R.string.from_page, page), { fromHere = true }, accent = fromHere)
                            }
                        }
                    }
                    p.finished -> {
                        val took = ((p.ended ?: khatmah.today()) - p.start + 1).toInt()
                        Text(
                            pluralStringResource(R.plurals.quran_read_in, took, took),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(Modifier.size(12.dp))
                        TextControl(stringResource(R.string.start_another), { khatmah.end() }, accent = true)
                    }
                    else -> {
                        val today = remember(p) { p.portion(khatmah.today()) }
                        val surah by produceState("", today.fromPage) {
                            value = runCatching { quran.surah(quran.firstAyah(today.fromPage).surah).title }.getOrDefault("")
                        }
                        LinearProgressIndicator(
                            progress = { p.read / p.total.toFloat() },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)
                        )
                        Text(
                            stringResource(R.string.khatmah_progress, minOf(today.day, p.days), p.days, p.read, p.total),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            when {
                                today.done -> stringResource(R.string.today_done_next, today.fromPage, surah)
                                today.fromPage == today.toPage -> stringResource(R.string.today_page, today.fromPage, surah)
                                else -> stringResource(R.string.today_pages, today.fromPage, today.toPage, surah)
                            },
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            stringResource(R.string.pages_count_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.size(12.dp))
                        EvenRows(minSlot = 120.dp) {
                            if (today.fromPage != page) TextControl(stringResource(R.string.go_to_page, today.fromPage), { onGo(today.fromPage) }, accent = true)
                            TextControl(stringResource(R.string.end_khatmah), { ending = true })
                        }
                    }
                }
            }
        }
    }
    if (ending) ZoneAlertDialog(
        onDismissRequest = { ending = false },
        title = { Text(stringResource(R.string.end_khatmah_q)) },
        text = { Text(stringResource(R.string.khatmah_forgotten)) },
        confirmButton = { TextButton(onClick = { khatmah.end(); ending = false }) { Text(stringResource(R.string.end)) } },
        dismissButton = { TextButton(onClick = { ending = false }) { Text(stringResource(R.string.keep_it)) } }
    )
}

/** The days a page was turned: the streak, and the last five weeks. */
@Composable
private fun DaysRead() {
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val today = remember { java.time.LocalDate.now().toEpochDay() }
    val locale = java.util.Locale.getDefault()
    val firstDay = remember(locale) { java.time.temporal.WeekFields.of(locale).firstDayOfWeek.value }
    val weeks = remember(today, firstDay) { Days.calendar(today, 5, firstDay) }
    val read = settings.readDays.toHashSet()
    val streak = Days.streak(settings.readDays, today)
    val scheme = MaterialTheme.colorScheme
    Text(
        when (streak) {
            0 -> stringResource(R.string.streak_none)
            else -> pluralStringResource(R.plurals.days_read_in_row, streak, streak)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = scheme.onSurfaceVariant
    )
    // The reader's own count: this week's pages, the best run, the khatmahs read whole.
    val week = Days.week(settings.pagesByDay, today)
    val best = Days.best(settings.readDays)
    val stats = listOfNotNull(
        pluralStringResource(R.plurals.week_pages, week, week),
        best.takeIf { it > 1 }?.let { pluralStringResource(R.plurals.best_streak, it, it) },
        settings.khatmahsDone.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.khatmahs_done, it, it) }
    )
    Text(stats.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    // The Hijri months these weeks fall in.
    Text(
        remember(weeks, locale) { Hijri.months(weeks.first().first(), weeks.last().last(), locale) },
        style = MaterialTheme.typography.labelLarge,
        color = scheme.primary,
        modifier = Modifier.padding(top = 6.dp)
    )
    Spacer(Modifier.size(6.dp))
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in weeks.first()) {
                Text(
                    java.time.LocalDate.ofEpochDay(d).dayOfWeek.getDisplayName(java.time.format.TextStyle.NARROW, locale),
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        for (week in weeks) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (d in week) {
                val on = d in read
                Box(
                    Modifier
                        .weight(1f)
                        .height(34.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (on) scheme.primary else scheme.surfaceVariant.copy(alpha = if (d > today) 0.25f else 0.7f))
                        .then(if (d == today) Modifier.border(1.5.dp, scheme.primary, RoundedCornerShape(6.dp)) else Modifier),
                    contentAlignment = Alignment.Center
                ) {
                    // The Hijri day, the Gregorian one small under it.
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            remember(d, locale) { NumberFormat.getInstance(locale).format(Hijri.day(d, locale)) },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (on) scheme.onPrimary else scheme.onSurface
                        )
                        Text(
                            remember(d, locale) { NumberFormat.getInstance(locale).format(java.time.LocalDate.ofEpochDay(d).dayOfMonth) },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                            color = (if (on) scheme.onPrimary else scheme.onSurfaceVariant).copy(alpha = 0.75f)
                        )
                    }
                }
            }
        }
    }
}
