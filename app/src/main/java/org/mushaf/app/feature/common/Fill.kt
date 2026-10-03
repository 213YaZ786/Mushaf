package org.mushaf.app.feature.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mushaf.app.ui.component.FloatingPane
import org.mushaf.app.ui.component.rememberHaptics
import kotlin.math.ceil

/**
 * Items that fill the whole width: as many on a row as [minSlot] allows,
 * the rows balanced (seven make four and three, not six and one), and each
 * item stretched to its share, so no row ends on empty space.
 */
@Composable
fun EvenRows(modifier: Modifier = Modifier, minSlot: Dp = 52.dp, gap: Dp = 8.dp, content: @Composable () -> Unit) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val n = measurables.size
        if (n == 0) return@Layout layout(constraints.maxWidth, 0) {}
        val width = constraints.maxWidth
        val g = gap.roundToPx()
        val fit = ((width + g) / (minSlot.roundToPx() + g)).coerceIn(1, n)
        val rows = ceil(n / fit.toFloat()).toInt()
        val perRow = ceil(n / rows.toFloat()).toInt()
        val placeables = measurables.chunked(perRow).map { row ->
            val slot = (width - g * (row.size - 1)) / row.size
            row.map { it.measure(Constraints.fixedWidth(slot).copy(minHeight = 0, maxHeight = constraints.maxHeight)) }
        }
        val heights = placeables.map { row -> row.maxOf { it.height } }
        val height = heights.sum() + g * (heights.size - 1)
        layout(width, height) {
            var y = 0
            placeables.forEachIndexed { r, row ->
                var x = 0
                for (p in row) {
                    p.placeRelative(x, y + (heights[r] - p.height) / 2)
                    x += p.width + g
                }
                y += heights[r] + g
            }
        }
    }
}

/** A control of an [EvenRows]: a pill of glass as wide as its share, an icon in it. */
@Composable
fun IconControl(icon: ImageVector, label: String, onClick: () -> Unit, accent: Boolean = false, tint: Color = Color.Unspecified, overlay: @Composable () -> Unit = {}) {
    val haptics = rememberHaptics()
    FloatingPane(
        shape = CircleShape,
        accent = accent,
        onClick = { haptics.tick(); onClick() },
        modifier = Modifier.fillMaxWidth().height(ControlHeight).semantics { contentDescription = label }
    ) {
        Box(Modifier.fillMaxWidth().height(ControlHeight), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint.takeOrElse { MaterialTheme.colorScheme.primary }, modifier = Modifier.size(22.dp))
            overlay()
        }
    }
}

/** A control of an [EvenRows] with a word in it. */
@Composable
fun TextControl(text: String, onClick: (() -> Unit)?, accent: Boolean = false) {
    FloatingPane(shape = CircleShape, accent = accent, onClick = onClick, modifier = Modifier.fillMaxWidth().height(ControlHeight)) {
        Box(Modifier.fillMaxWidth().height(ControlHeight).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        }
    }
}

val ControlHeight = 48.dp
