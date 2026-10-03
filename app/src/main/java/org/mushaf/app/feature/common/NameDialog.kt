package org.mushaf.app.feature.common

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.mushaf.app.R
import org.mushaf.app.ui.component.ZoneAlertDialog

/** A name to add or change (a child, a collection); [removeNote] says what removing does. */
@Composable
fun NameDialog(title: String, initial: String, onDone: (String) -> Unit, onCancel: () -> Unit, onRemove: (() -> Unit)? = null, removeNote: String = "") {
    // The cursor after the name, ready to change its end.
    var name by remember { mutableStateOf(TextFieldValue(initial, TextRange(initial.length))) }
    var removing by remember { mutableStateOf(false) }
    ZoneAlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (removing) stringResource(R.string.remove_q, initial) else title) },
        text = {
            if (removing) Text(removeNote)
            else OutlinedTextField(value = name, onValueChange = { name = if (it.text.length <= 30) it else it.copy(text = it.text.take(30)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        },
        confirmButton = {
            if (removing) TextButton(onClick = { onRemove?.invoke() }) { Text(stringResource(R.string.remove)) }
            else TextButton(onClick = { onDone(name.text) }, enabled = name.text.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            Row {
                if (onRemove != null && !removing) TextButton(onClick = { removing = true }) { Text(stringResource(R.string.remove)) }
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        }
    )
}
