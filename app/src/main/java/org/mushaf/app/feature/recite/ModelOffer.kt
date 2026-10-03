package org.mushaf.app.feature.recite

import org.mushaf.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import org.mushaf.app.ui.component.ZoneAlertDialog

/** Asks before fetching the speech model, the first time the phone is to listen. */
@Composable
fun ModelOffer(onYes: () -> Unit, onNo: () -> Unit) {
    ZoneAlertDialog(
        onDismissRequest = onNo,
        title = { Text(stringResource(R.string.recite_q)) },
        text = {
            Text(stringResource(R.string.model_offer_text))
        },
        confirmButton = { TextButton(onClick = onYes) { Text(stringResource(R.string.download)) } },
        dismissButton = { TextButton(onClick = onNo) { Text(stringResource(R.string.not_now)) } }
    )
}
