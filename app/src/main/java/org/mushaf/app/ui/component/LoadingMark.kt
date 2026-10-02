package org.mushaf.app.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * The app's loading mark. For now three dots that breathe in turn; it is
 * drawn from the launcher icon once the icon is chosen.
 *
 * [progress] from 0 to 1 lights the dots as far as a gesture has gone.
 * While [running] they breathe on their own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) {
    val accent = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "breath")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "clock"
    )
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 9f
        for (i in 0..2) {
            val phase = if (running) ((sin((clock - i / 6f) * 2 * PI) + 1) / 2).toFloat() else if (progress * 3 > i) 1f else 0.25f
            val x = this.size.width / 2 + (i - 1) * r * 3f
            drawCircle(accent.copy(alpha = 0.3f + 0.7f * phase), radius = r * (0.8f + 0.3f * phase), center = Offset(x, this.size.height / 2))
        }
    }
}
