package org.mushaf.app.feature.translations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.mushaf.app.data.quran.TranslationInfo
import org.mushaf.app.data.quran.Translations
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.navigation.LocalReadableInset
import org.mushaf.app.ui.component.FloatingAction
import org.mushaf.app.ui.component.FloatingFrame
import org.mushaf.app.ui.component.FloatingTop
import org.mushaf.app.ui.component.LoadingMark
import org.mushaf.app.ui.component.SearchPill
import org.mushaf.app.ui.component.ZoneSurface
import org.mushaf.app.ui.component.rememberHaptics
import org.mushaf.app.ui.icon.AppIcons

/**
 * The translations of the meanings: those shown, in order, then every one
 * the catalogues offer, by language. Picking one fetches it once.
 */
@Composable
fun TranslationsScreen(onBack: () -> Unit) {
    val translations: Translations = koinInject()
    val store: SettingsStore = koinInject()
    val settings by store.settings.collectAsState()
    val installed by translations.installed.collectAsState()
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    var query by rememberSaveable { mutableStateOf("") }
    val busy = remember { mutableStateMapOf<String, Boolean>() }
    val failed = remember { mutableStateMapOf<String, Boolean>() }
    val catalog by produceState<List<TranslationInfo>?>(null) { value = translations.catalog() }

    fun choose(info: TranslationInfo, on: Boolean) {
        haptics.toggle(on)
        if (!on) {
            store.update { s -> s.copy(translations = s.translations - info.id) }
            return
        }
        if (installed.any { it.id == info.id }) {
            store.update { s -> s.copy(translations = (s.translations - info.id) + info.id) }
            return
        }
        busy[info.id] = true
        failed.remove(info.id)
        scope.launch {
            runCatching { translations.install(info) }
                .onSuccess { store.update { s -> s.copy(translations = (s.translations - info.id) + info.id) } }
                .onFailure { failed[info.id] = true; haptics.reject() }
            busy.remove(info.id)
        }
    }

    FloatingFrame(
        bottom = 0.dp,
        top = {
            FloatingTop("Translations", leading = { FloatingAction(AppIcons.ArrowBack, "Back", onBack) })
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                SearchPill(query, { query = it }, "Language or translator", floating = true)
            }
        }
    ) { padding ->
        val inset = LocalReadableInset.current
        val shown = settings.translations.mapNotNull { id -> installed.firstOrNull { it.id == id } }
        val q = query.trim()
        val others = (installed.filter { it.id !in settings.translations } + catalog.orEmpty().filter { c -> installed.none { it.id == c.id } })
            .filter { q.isEmpty() || it.language.contains(q, true) || it.name.contains(q, true) }
            .sortedWith(compareBy({ it.language }, { it.name }))
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, start = inset + 12.dp, end = inset + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            if (q.isEmpty()) {
                item { Heading("Shown") }
                items(shown, key = { "s" + it.id }) { t -> TranslationRow(t, true, busy[t.id] == true, failed[t.id] == true) { on -> choose(t, on) } }
                item { Heading(if (catalog == null) "Looking for translations…" else "${others.size} more") }
            }
            items(others, key = { it.id }) { t ->
                TranslationRow(t, false, busy[t.id] == true, failed[t.id] == true) { on -> choose(t, on) }
            }
            item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)) }
        }
    }
}

@Composable
private fun Heading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
    )
}

@Composable
private fun TranslationRow(t: TranslationInfo, on: Boolean, busy: Boolean, failed: Boolean, onChange: (Boolean) -> Unit) {
    val haptics = rememberHaptics()
    ZoneSurface(shape = RoundedCornerShape(22.dp), accent = on, onClick = { if (!busy) { haptics.toggle(!on); onChange(!on) } }, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    if (failed) "${t.language} · could not be fetched, try again" else t.language,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            when {
                busy -> LoadingMark(size = 28.dp)
                else -> Switch(checked = on, onCheckedChange = { onChange(it) })
            }
        }
    }
}

