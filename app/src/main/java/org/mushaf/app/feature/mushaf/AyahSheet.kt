package org.mushaf.app.feature.mushaf

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.res.ResourcesCompat
import org.mushaf.app.R
import org.mushaf.app.core.quran.Riwayah
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.ChoiceDialog
import org.mushaf.app.feature.share.CardColors
import org.mushaf.app.feature.share.AyahCard
import org.mushaf.app.feature.common.IconControl
import org.mushaf.app.feature.common.EvenRows
import androidx.compose.foundation.clickable
import org.mushaf.app.data.audio.WordAudio
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import org.mushaf.app.core.quran.AyahKey
import org.mushaf.app.data.marks.Marks
import org.mushaf.app.ui.component.ZoneAlertDialog
import org.mushaf.app.core.quran.Word
import org.mushaf.app.data.quran.Quran
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/**
 * What a long press on a word opens, on glass over the page: the word, its
 * meaning and how it sounds, then its ayah's meaning, and what can be done
 * with the ayah.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AyahSheet(
    word: Word,
    hafs: FontFamily,
    onClose: () -> Unit,
    onOpenMeaning: (AyahKey) -> Unit,
    onOpenTafsir: (AyahKey) -> Unit,
    onPlay: (AyahKey) -> Unit,
    /** Heard again and again, from the first ayah to the second. */
    onRepeat: (AyahKey, AyahKey) -> Unit,
    /** Learn this ayah by heart now: a lesson of it on its page. */
    onMemorise: (AyahKey) -> Unit
) {
    val quran: Quran = koinInject()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val surah by produceState<String?>(null, word.key.surah) { value = quran.surah(word.key.surah).title }
    val meaning by produceState("", word.key) { value = quran.english(word.key) }
    val arabic by produceState("", word.key) {
        value = quran.page(quran.pageOf(word.key)).words.filter { it.key == word.key && !it.end }.joinToString(" ") { it.text }
    }
    val reference = "${surah ?: ""} ${word.key}".trim()
    // An ayah of prostration (۩), as this riwayah's mushaf marks them.
    val sajdah by produceState(false, word.key) { value = word.key.toString() in quran.meta().sajdah }
    val marks: Marks = koinInject()
    val saved by marks.marks.collectAsState()
    val bookmarked = saved.bookmarks.any { it.key == word.key }
    val note = saved.notes.firstOrNull { it.key == word.key }?.text
    var writing by remember { mutableStateOf(false) }
    var sharing by remember { mutableStateOf(false) }
    var repeating by remember { mutableStateOf(false) }
    // The ayat that can close a passage begun here: the rest of the surah, twenty at most.
    val passageEnds by produceState(emptyList<AyahKey>(), word.key) {
        val s = quran.surah(word.key.surah)
        value = (word.key.ayah..minOf(s.ayat, word.key.ayah + 20)).map { AyahKey(word.key.surah, it) }
    }
    val settings by koinInject<SettingsStore>().settings.collectAsState()
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.background.luminance() < 0.5f
    // The ayah with its number, as the card shows it.
    val withNumber by produceState("", word.key) {
        value = quran.page(quran.pageOf(word.key)).words.filter { it.key == word.key }.joinToString(" ") { it.text }
    }
    val words: WordAudio = koinInject()
    val sound by words.sound.collectAsState()
    val heard = sound?.takeIf { it.of(word) }

    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FloatingPane(shape = RoundedCornerShape(28.dp), modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
            Column(
                Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                if (!word.end) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(word.meaning, style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (heard?.failed == true) stringResource(R.string.no_connection_word, word.transliteration) else word.transliteration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (words.available(word)) {
                            FloatingAction(AppIcons.VolumeUp, stringResource(R.string.hear_word), {
                                haptics.tick()
                                words.play(word)
                            })
                            Spacer(Modifier.size(12.dp))
                        }
                        Text(
                            word.text,
                            style = TextStyle(fontFamily = hafs, fontSize = 34.sp),
                            color = if (heard?.playing == true) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                            modifier = if (words.available(word)) Modifier.clickable { haptics.tick(); words.play(word) } else Modifier
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Text(
                    if (sajdah) reference + " · ۩ " + stringResource(R.string.sajdah) else reference,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(4.dp))
                Text(meaning, style = MaterialTheme.typography.bodyLarge)
                if (note != null) {
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.note_n, note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(16.dp))
                // The actions share the whole width, on balanced rows.
                EvenRows(minSlot = 48.dp) {
                    IconControl(if (bookmarked) AppIcons.Bookmark else AppIcons.BookmarkOutline, if (bookmarked) stringResource(R.string.remove_bookmark) else stringResource(R.string.bookmark), {
                        haptics.toggle(marks.toggleBookmark(word.key))
                    })
                    IconControl(AppIcons.Play, stringResource(R.string.listen_from_here), { onPlay(word.key) })
                    IconControl(AppIcons.Repeat, stringResource(R.string.repeat_passage), { repeating = true })
                    IconControl(AppIcons.Translate, stringResource(R.string.read_with_meaning), { onOpenMeaning(word.key) })
                    IconControl(AppIcons.MenuBook, stringResource(R.string.tafsir), { onOpenTafsir(word.key) })
                    IconControl(AppIcons.School, stringResource(R.string.memorise), { onMemorise(word.key) })
                    IconControl(AppIcons.Info, stringResource(R.string.note), { writing = true })
                    IconControl(AppIcons.Copy, stringResource(R.string.copy), {
                        haptics.done()
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText(reference, "$arabic\n$meaning\n($reference)").toClipEntry())
                        }
                    })
                    IconControl(AppIcons.Share, stringResource(R.string.share), { sharing = true })
                    IconControl(AppIcons.Close, stringResource(R.string.close), onClose)
                }
                if (repeating) ChoiceDialog(
                    title = stringResource(R.string.repeat_from_to, word.key.toString()),
                    options = passageEnds.map { it to if (it == word.key) stringResource(R.string.ayah_only, it.toString()) else it.toString() },
                    selected = null,
                    onSelect = { end -> repeating = false; if (end != null) onRepeat(word.key, end) },
                    onDismiss = { repeating = false }
                )
                if (sharing) ChoiceDialog(
                    title = stringResource(R.string.share),
                    options = listOf(0 to stringResource(R.string.share_image_meaning), 1 to stringResource(R.string.share_image), 2 to stringResource(R.string.share_text)),
                    selected = null,
                    onSelect = { how ->
                        sharing = false
                        if (how == 2) {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "$arabic\n\n$meaning\n\n($reference)")
                            context.startActivity(Intent.createChooser(send, null))
                        } else scope.launch {
                            val font = ResourcesCompat.getFont(context, if (settings.riwayah == Riwayah.WARSH) R.font.uthmanic_warsh else R.font.uthmanic_hafs)
                                ?: return@launch
                            val colors = CardColors(
                                background = scheme.surface.toArgb(),
                                glow1 = scheme.primaryContainer.toArgb(),
                                glow2 = scheme.tertiaryContainer.toArgb(),
                                glow3 = scheme.secondaryContainer.toArgb(),
                                glass = (if (dark) scheme.surfaceContainerHigh.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.6f)).toArgb(),
                                rim = Color.White.copy(alpha = if (dark) 0.14f else 0.9f).toArgb(),
                                ink = scheme.onSurface.toArgb(),
                                soft = scheme.onSurfaceVariant.toArgb()
                            )
                            val card = withContext(Dispatchers.Default) {
                                AyahCard.draw(withNumber.ifEmpty { arabic }, font, meaning.takeIf { how == 0 }, reference, colors)
                            }
                            AyahCard.share(context, card, context.getString(R.string.share_caption, reference))
                        }
                    },
                    onDismiss = { sharing = false }
                )
                if (writing) NoteDialog(reference, note.orEmpty(), onDone = { text ->
                    marks.setNote(word.key, text)
                    writing = false
                }, onCancel = { writing = false })
                Text(
                    "Saheeh International",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun NoteDialog(reference: String, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    ZoneAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(stringResource(R.string.note_on, reference)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(if (text.isBlank() && initial.isNotBlank()) stringResource(R.string.remove) else stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) } }
    )
}
