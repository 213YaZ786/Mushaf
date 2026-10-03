package org.mushaf.app.feature.offline

import org.mushaf.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import org.koin.compose.koinInject
import org.mushaf.app.data.offline.Offline
import org.mushaf.app.data.offline.Pack
import org.mushaf.app.data.settings.SettingsStore
import org.mushaf.app.ui.component.ZoneAlertDialog

/**
 * The first time a pack is used while it is not kept, offers once to keep
 * it offline: the whole of it, or [one] thing only (this surah), or not now.
 * Shown only when [wanted] and the pack was never offered.
 */
@Composable
fun OfferDownload(
    pack: Pack,
    wanted: Boolean,
    title: String,
    text: String,
    one: Pair<String, () -> Unit>? = null
) {
    val store: SettingsStore = koinInject()
    val offline: Offline = koinInject()
    val settings by store.settings.collectAsState()
    if (!wanted || pack.id in settings.offered) return
    val close = { store.update { it.copy(offered = it.offered + pack.id) } }
    ZoneAlertDialog(
        onDismissRequest = close,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = { offline.start(pack); close() }) { Text(stringResource(R.string.keep_all_offline)) }
        },
        dismissButton = {
            if (one != null) {
                TextButton(onClick = { one.second(); close() }) { Text(one.first) }
            }
            TextButton(onClick = close) { Text(stringResource(R.string.not_now)) }
        }
    )
}
