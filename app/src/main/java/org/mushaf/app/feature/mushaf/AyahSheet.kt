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
@Composable
fun AyahSheet(word: Word, hafs: FontFamily, onClose: () -> Unit) {
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
                                word.transliteration,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            word.text,
                            style = TextStyle(fontFamily = hafs, fontSize = 34.sp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.size(12.dp))
                }
                Text(reference, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(4.dp))
                Text(meaning, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
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
                    Spacer(Modifier.weight(1f))
                    FloatingAction(AppIcons.Close, "Close", onClose)
                }
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
