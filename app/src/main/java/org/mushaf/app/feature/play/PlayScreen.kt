package org.mushaf.app.feature.play

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.koin.compose.koinInject
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import org.mushaf.app.ui.component.ZoneAlertDialog
import org.mushaf.app.feature.common.TextControl
import org.mushaf.app.feature.common.EvenRows
import org.mushaf.app.data.play.Child
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import org.mushaf.app.R
import org.mushaf.app.data.play.Star
import org.mushaf.app.data.play.Stars
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.Section
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/** The games, in the order they are shown for a surah. */
enum class Game(val title: Int, val detail: Int, val icon: ImageVector, val star: Star?) {
    LISTEN(R.string.game_listen, R.string.game_listen_detail, AppIcons.Headphones, Star.LISTENED),
    BUILD(R.string.game_build, R.string.game_build_detail, AppIcons.Puzzle, Star.BUILT),
    MISSING(R.string.game_missing, R.string.game_missing_detail, AppIcons.Visibility, null),
    MATCH(R.string.game_match, R.string.game_match_detail, AppIcons.Translate, null),
    WHICH(R.string.game_which, R.string.game_which_detail, AppIcons.VolumeUp, null),
    RECITE(R.string.game_recite, R.string.game_recite_detail, AppIcons.Mic, Star.RECITED)
}

/**
 * Play: the surahs of Juz 'Amma, from An-Nas, as a path the child walks,
 * each with its three stars; a surah opens its games.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlayScreen(onBack: () -> Unit, onOpenSurah: (Int) -> Unit) {
    val quran: Quran = koinInject()
    val stars: Stars = koinInject()
    val won by stars.stars.collectAsState()
    val haptics = rememberHaptics()
    val meta by produceState(quran.metaNow, quran) { value = quran.meta() }
    val names = remember { FontFamily(Font(R.font.surah_names)) }

    FloatingFrame(
        bottom = 0.dp,
        top = { FloatingTop(stringResource(R.string.play), leading = { FloatingAction(AppIcons.ArrowBack, stringResource(R.string.back), onBack) }) }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = LocalReadableInset.current)
        ) {
            Spacer(Modifier.height(padding.calculateTopPadding()))
            // Who is playing: each child has their own stars.
            val children by stars.children.collectAsState()
            var editing by remember { mutableStateOf<Child?>(null) }
            var adding by remember { mutableStateOf(false) }
            EvenRows(Modifier.padding(horizontal = 12.dp).padding(bottom = 4.dp), minSlot = 96.dp) {
                for (c in children.list.filter { it.name.isNotEmpty() }) {
                    val active = c.id == children.active
                    TextControl(c.name, { if (active) editing = c else stars.select(c.id) }, accent = active)
                }
                TextControl(if (children.list.any { it.name.isNotEmpty() }) stringResource(R.string.add_child) else stringResource(R.string.add_child_name), { adding = true })
            }
            if (adding) NameDialog(stringResource(R.string.a_childs_name), "", onDone = { stars.add(it); adding = false }, onCancel = { adding = false })
            editing?.let { c ->
                NameDialog(
                    stringResource(R.string.childs_name_of, c.name), c.name,
                    onDone = { stars.rename(c.id, it); editing = null },
                    onCancel = { editing = null },
                    onRemove = { stars.remove(c.id); editing = null }
                )
            }
            val total = won.values.sumOf { it.size }
            Section(stringResource(R.string.amma_stars, total, 37 * 3)) {
                // The surahs in as many columns as the width holds, each as wide as its share.
                EvenRows(Modifier.padding(12.dp), minSlot = 100.dp, gap = 10.dp) {
                    for (n in 114 downTo 78) {
                        val s = meta?.surahs?.getOrNull(n - 1)
                        val got = won[n].orEmpty()
                        ZoneSurface(
                            shape = RoundedCornerShape(22.dp),
                            accent = got.size == 3,
                            onClick = { haptics.tick(); onOpenSurah(n) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("%03d".format(n), style = TextStyle(fontFamily = names, fontSize = 30.sp, textAlign = TextAlign.Center), maxLines = 1)
                                Text(s?.name.orEmpty(), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                Row(Modifier.padding(top = 4.dp)) {
                                    for (star in Star.entries) {
                                        Icon(
                                            if (star in got) AppIcons.Star else AppIcons.StarOutline,
                                            contentDescription = null,
                                            tint = if (star in got) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

/** A child's name, to add or change; removing forgets their stars. */
@Composable
private fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit, onRemove: (() -> Unit)? = null) {
    var name by remember { mutableStateOf(initial) }
    var removing by remember { mutableStateOf(false) }
    ZoneAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (removing) stringResource(R.string.remove_q, initial) else title) },
        text = {
            if (removing) Text(stringResource(R.string.stars_forgotten))
            else OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            if (removing) TextButton(onClick = { onRemove?.invoke() }) { Text(stringResource(R.string.remove)) }
            else TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null && !removing) TextButton(onClick = { removing = true }) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
