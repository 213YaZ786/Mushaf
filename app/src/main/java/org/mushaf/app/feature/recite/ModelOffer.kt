package org.mushaf.app.feature.recite

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.mushaf.app.ui.component.ZoneAlertDialog

/** Asks before fetching the speech model, the first time the phone is to listen. */
@Composable
fun ModelOffer(onYes: () -> Unit, onNo: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onNo,
        title = { Text("Recite to the phone?") },
        text = {
            Text(
                "The phone listens while you recite: it shows each word as you say it, or finds the ayah you recited. " +
                    "It needs Tarteel's Quran speech model, about 80 MB, kept on the phone. What you recite never leaves it."
            )
        },
        confirmButton = { TextButton(onClick = onYes) { Text("Download") } },
        dismissButton = { TextButton(onClick = onNo) { Text("Not now") } }
    )
}
