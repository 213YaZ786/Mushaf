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
    onPlay: (AyahKey) -> Unit
) {
    val quran: Quran = koinInject()
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val surah by produceState<String?>(null, word.key.surah) { value = quran.surah(word.key.surah).name }
    val meaning by produceState("", word.key) { value = quran.english(word.key) }
    val arabic by produceState("", word.key) {
        value = quran.page(quran.pageOf(word.key)).words.filter { it.key == word.key && !it.end }.joinToString(" ") { it.text }
    }
    val reference = "${surah ?: ""} ${word.key}".trim()
    val marks: Marks = koinInject()
    val saved by marks.marks.collectAsState()
    val bookmarked = saved.bookmarks.any { it.key == word.key }
    val note = saved.notes.firstOrNull { it.key == word.key }?.text
    var writing by remember { mutableStateOf(false) }
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
                                if (heard?.failed == true) "${word.transliteration} · no connection to hear it" else word.transliteration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (words.available(word)) {
                            FloatingAction(AppIcons.VolumeUp, "Hear the word", {
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
                Text(reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(4.dp))
                Text(meaning, style = MaterialTheme.typography.bodyLarge)
                if (note != null) {
                    Spacer(Modifier.size(8.dp))
                    Text("Note · $note", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.size(16.dp))
                // The actions wrap onto a second line on a narrow screen.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    FloatingAction(if (bookmarked) AppIcons.Bookmark else AppIcons.BookmarkOutline, if (bookmarked) "Remove bookmark" else "Bookmark", {
                        haptics.toggle(marks.toggleBookmark(word.key))
                    })
                    FloatingAction(AppIcons.Play, "Listen from here", { onPlay(word.key) })
                    FloatingAction(AppIcons.Translate, "Read with meaning", { onOpenMeaning(word.key) })
                    FloatingAction(AppIcons.MenuBook, "Tafsir", { onOpenTafsir(word.key) })
                    FloatingAction(AppIcons.Info, "Note", { writing = true })
                    FloatingAction(AppIcons.Copy, "Copy", {
                        haptics.done()
                        scope.launch {
                            clipboard.setClipEntry(ClipData.newPlainText(reference, "$arabic\n$meaning\n($reference)").toClipEntry())
                        }
                    })
                    FloatingAction(AppIcons.Share, "Share", {
                        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                            .putExtra(Intent.EXTRA_TEXT, "$arabic\n\n$meaning\n\n($reference)")
                        context.startActivity(Intent.createChooser(send, null))
                    })
                    FloatingAction(AppIcons.Close, "Close", onClose)
                }
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
        title = { Text("Note on $reference") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                minLines = 3,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = { TextButton(onClick = { onDone(text) }) { Text(if (text.isBlank() && initial.isNotBlank()) "Remove" else "Save") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } }
    )
}
