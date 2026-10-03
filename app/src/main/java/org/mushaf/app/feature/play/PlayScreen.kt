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
enum class Game(val title: String, val detail: String, val icon: ImageVector, val star: Star?) {
    LISTEN("Listen and repeat", "Sheikh al-Minshawi recites, a child repeats after him.", AppIcons.Headphones, Star.LISTENED),
    BUILD("Build the ayat", "Tap the words in their order.", AppIcons.Puzzle, Star.BUILT),
    MISSING("Find the missing word", "One word is hidden in each ayah.", AppIcons.Visibility, null),
    MATCH("Match the meanings", "Each word with what it means.", AppIcons.Translate, null),
    WHICH("Which ayah?", "Listen, then find the ayah you heard.", AppIcons.VolumeUp, null),
    RECITE("Recite it", "Recite the surah to the phone, from memory.", AppIcons.Mic, Star.RECITED)
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
        top = { FloatingTop("Play", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) }) }
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
                TextControl(if (children.list.any { it.name.isNotEmpty() }) "+ Child" else "+ Add a child's name", { adding = true })
            }
            if (adding) NameDialog("A child's name", "", onDone = { stars.add(it); adding = false }, onCancel = { adding = false })
            editing?.let { c ->
                NameDialog(
                    "${c.name}'s name", c.name,
                    onDone = { stars.rename(c.id, it); editing = null },
                    onCancel = { editing = null },
                    onRemove = { stars.remove(c.id); editing = null }
                )
            }
            val total = won.values.sumOf { it.size }
            Section("Juz 'Amma · $total of ${37 * 3} stars") {
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
        title = { Text(if (removing) "Remove $initial?" else title) },
        text = {
            if (removing) Text("Their stars are forgotten.")
            else OutlinedTextField(value = name, onValueChange = { name = it.take(30) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            if (removing) TextButton(onClick = { onRemove?.invoke() }) { Text("Remove") }
            else TextButton(onClick = { onDone(name) }, enabled = name.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null && !removing) TextButton(onClick = { removing = true }) { Text("Remove") }
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
    )
}
