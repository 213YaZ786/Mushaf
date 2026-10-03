package org.mushaf.app.feature.listen

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import org.mushaf.app.R
import org.mushaf.app.data.audio.Reciter

/** The reciter's name, with the kind of recitation when it is not the usual murattal, in the reader's language. */
@Composable
fun Reciter.label(): String {
    val kind = when (style) {
        "Teaching, slow" -> stringResource(R.string.style_teaching)
        "With a child repeating" -> stringResource(R.string.style_child)
        "Mujawwad" -> stringResource(R.string.style_mujawwad)
        "Warsh" -> stringResource(R.string.style_warsh)
        else -> ""
    }
    return if (kind.isEmpty()) name else "$name · $kind"
}
