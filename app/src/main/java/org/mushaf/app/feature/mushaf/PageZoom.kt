package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** How close the page shown is: its scale (1 to 4) and how far it is moved. */
@Stable
class PageZoom {
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    internal var size = IntSize.Zero
    val zoomed: Boolean get() = scale > 1f

    fun reset() {
        scale = 1f
        offset = Offset.Zero
    }

    internal fun change(zoom: Float, pan: Offset) {
        scale = (scale * zoom).coerceIn(1f, MAX)
        // The page's edges never come inside the window.
        val maxX = (scale - 1f) * size.width / 2f
        val maxY = (scale - 1f) * size.height / 2f
        offset = Offset((offset.x + pan.x).coerceIn(-maxX, maxX), (offset.y + pan.y).coerceIn(-maxY, maxY))
    }

    internal fun settle() {
        if (scale < 1.05f) reset()
    }

    companion object {
        const val MAX = 4f
    }
}

/**
 * Two fingers pinch the page closer or back; once closer, one finger moves
 * it. At 1× a single finger is left to the pages (turning, tapping).
 */
fun Modifier.pageZoom(zoom: PageZoom): Modifier = this
    .onSizeChanged { zoom.size = it }
    .pointerInput(zoom) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                val fingers = event.changes.count { it.pressed }
                if (fingers >= 2 || zoom.zoomed) {
                    val z = event.calculateZoom()
                    val pan = event.calculatePan()
                    if (z != 1f || pan != Offset.Zero) {
                        zoom.change(z, pan)
                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
            zoom.settle()
        }
    }
    .graphicsLayer {
        scaleX = zoom.scale
        scaleY = zoom.scale
        translationX = zoom.offset.x
        translationY = zoom.offset.y
    }
