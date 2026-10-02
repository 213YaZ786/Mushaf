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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.mushaf.app.R

/**
 * Mushaf's loading mark, the launcher icon in motion: the medal still, a
 * gleam of light crossing its relief from the upper left, as on metal
 * turned to the light. The medal is the icon's: its grey relief multiplied
 * by the theme's accent, its white sheen over it; the gleam lights it
 * broadly and catches brightly on what is raised.
 *
 * [progress] from 0 to 1 carries the gleam as far as a gesture has gone.
 * While [running] it crosses on its own.
 */
@Composable
fun LoadingMark(
    modifier: Modifier = Modifier,
    size: Dp = 32.dp,
    running: Boolean = true,
    progress: Float = 0f
) {
    val medal = ImageBitmap.imageResource(R.drawable.mushaf_medal)
    val sheen = ImageBitmap.imageResource(R.drawable.mushaf_sheen)
    val accent = MaterialTheme.colorScheme.primary
    val tint = remember(accent) { ColorFilter.tint(accent, BlendMode.Modulate) }

    val transition = rememberInfiniteTransition(label = "gleam")
    val clock by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(LOOP_MILLIS, easing = LinearEasing), RepeatMode.Restart),
        label = "pass"
    )
    // The gleam crosses in the first part of each loop, then the medal rests.
    val place = if (running) START + (END - START) * (clock / CROSSING).coerceAtMost(1f) else START + (END - START) * progress.coerceIn(0f, 1f)

    Canvas(modifier.size(size).graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        val src = medalBounds(medal)
        val dst = IntSize(this.size.width.roundToInt(), this.size.height.roundToInt())
        drawImage(medal, src.first, src.second, dstSize = dst, colorFilter = tint, filterQuality = FilterQuality.High)
        drawImage(sheen, src.first, src.second, dstSize = dst, filterQuality = FilterQuality.High)
        // Broad and soft, on the medal only.
        drawRect(gleam(place, 0.30f, 0.16f), blendMode = BlendMode.SrcAtop)
        // Bright where the relief catches it: the sheen, kept where the gleam is, added.
        drawIntoCanvas { it.saveLayer(Rect(Offset.Zero, this.size), Paint().apply { blendMode = BlendMode.Plus }) }
        drawImage(sheen, src.first, src.second, dstSize = dst, filterQuality = FilterQuality.High)
        drawRect(gleam(place, 1f, 0.09f), blendMode = BlendMode.DstIn)
        drawIntoCanvas { it.restore() }
    }
}

/** The part of the icon's 108 dp canvas the medal covers (a radius of 34 dp, and its shadow's margin). */
private fun medalBounds(image: ImageBitmap): Pair<IntOffset, IntSize> {
    val margin = image.width * (54f - 35f) / 108f
    val side = image.width - 2 * margin
    return IntOffset(margin.roundToInt(), margin.roundToInt()) to IntSize(side.roundToInt(), side.roundToInt())
}

/** A band of light across the diagonal, centred at [place] (0 at the upper left corner, 1 at the lower right). */
private fun DrawScope.gleam(place: Float, strength: Float, width: Float): Brush {
    val stops = arrayOf(
        (place - width) to Color.Transparent,
        place to Color.White.copy(alpha = strength),
        (place + width) to Color.Transparent
    ).map { (at, color) -> at.coerceIn(0f, 1f) to color }.toTypedArray()
    return Brush.linearGradient(*stops, start = Offset.Zero, end = Offset(size.width, size.height))
}

private const val LOOP_MILLIS = 2200
private const val CROSSING = 0.7f
private const val START = -0.25f
private const val END = 1.25f
