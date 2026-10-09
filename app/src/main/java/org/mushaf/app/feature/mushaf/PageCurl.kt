package org.mushaf.app.feature.mushaf

import androidx.compose.foundation.pager.PagerState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.sin

/**
 * A page folded as paper is: the point taken on its free edge (the left,
 * the spine being on the right) is brought to the finger, and the page
 * folds along the line half way between them. The part on the finger's
 * side stays flat; the part on the other side is turned over and lies on
 * it, its back showing; the next page shows where it was.
 */
internal object Curl {

    class Fold(
        /** The page still flat. */
        val front: List<Offset>,
        /** Where the turned part was: the next page shows there. */
        val lifted: List<Offset>,
        /** The turned part, lying over the flat one: its back. */
        val back: List<Offset>,
        /** A point of the fold and the fold's normal, toward the flat part. */
        val at: Offset,
        val normal: Offset
    )

    /**
     * The fold of a [width] by [height] page turned [t] of the way (0 flat,
     * 1 turned over the spine), taken at [grab] of its height (0 top, 1
     * bottom). Taken low or high, the corner lifts first, as a hand does.
     */
    fun fold(width: Float, height: Float, t: Float, grab: Float): Fold? {
        if (t <= 0f || width <= 0f || height <= 0f) return null
        val gy = grab.coerceIn(0f, 1f) * height
        val tilt = when {
            grab > 0.6f -> -1f
            grab < 0.4f -> 1f
            else -> 0f
        }
        val c = Offset(0f, gy)
        val p = Offset(2.05f * width * t, gy + tilt * height * 0.18f * sin(PI.toFloat() * t.coerceAtMost(1f)))
        val d = p - c
        val len = hypot(d.x, d.y).takeIf { it > 0f } ?: return null
        val n = Offset(d.x / len, d.y / len)
        val m = Offset((c.x + p.x) / 2f, (c.y + p.y) / 2f)
        val front = cut(width, height, m, n, keep = true)
        val lifted = cut(width, height, m, n, keep = false)
        return Fold(front, lifted, lifted.map { reflect(it, m, n) }, m, n)
    }

    /** The page's rectangle on one side of the fold. */
    fun cut(width: Float, height: Float, m: Offset, n: Offset, keep: Boolean): List<Offset> {
        val corners = listOf(Offset(0f, 0f), Offset(width, 0f), Offset(width, height), Offset(0f, height))
        val sign = if (keep) 1f else -1f
        fun side(p: Offset) = ((p.x - m.x) * n.x + (p.y - m.y) * n.y) * sign
        val out = ArrayList<Offset>(5)
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val sa = side(a)
            val sb = side(b)
            if (sa >= 0f) out += a
            if ((sa >= 0f) != (sb >= 0f)) {
                val k = sa / (sa - sb)
                out += Offset(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
            }
        }
        return out
    }

    fun reflect(p: Offset, m: Offset, n: Offset): Offset {
        val s = (p.x - m.x) * n.x + (p.y - m.y) * n.y
        return Offset(p.x - 2f * s * n.x, p.y - 2f * s * n.y)
    }

    /** The reflection across the fold, as a matrix for drawing. */
    fun mirror(m: Offset, n: Offset): Matrix {
        val k = 2f * (m.x * n.x + m.y * n.y)
        return Matrix().apply {
            this[0, 0] = 1f - 2f * n.x * n.x
            this[1, 0] = -2f * n.x * n.y
            this[3, 0] = k * n.x
            this[0, 1] = -2f * n.x * n.y
            this[1, 1] = 1f - 2f * n.y * n.y
            this[3, 1] = k * n.y
        }
    }

    fun path(points: List<Offset>) = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        close()
    }
}

/**
 * The pager's pages turned as paper: the page held in place folds along
 * the finger and turns over the next, which waits beneath in the shadow
 * of the part lifted. Follows the finger; let go early, it falls back.
 * [grab] is where the finger took the page, as a fraction of its height.
 */
internal fun Modifier.pageCurl(pager: PagerState, item: Int, paper: DrawScope.() -> Unit, grab: () -> Float): Modifier = this
    .zIndex(-item.toFloat())
    .graphicsLayer {
        // 0 at rest, 0 to 1 while this page turns away, -1 to 0 while it waits beneath.
        val off = (pager.currentPage - item) + pager.currentPageOffsetFraction
        // The pager slides its pages (right to left); here they keep still.
        translationX = if (off > -1f && off < 1f) -off * size.width else 0f
    }
    .drawWithContent {
        val off = (pager.currentPage - item) + pager.currentPageOffsetFraction
        when {
            off > 0f && off < 1f -> turning(Curl.fold(size.width, size.height, off, grab()), paper)
            off < 0f && off > -1f -> beneath(Curl.fold(size.width, size.height, 1f + off, grab()))
            else -> drawContent()
        }
    }

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.turning(fold: Curl.Fold?, paper: DrawScope.() -> Unit) {
    if (fold == null) { drawContent(); return }
    val n = fold.normal
    val m = fold.at
    // The flat part, on the same paper as a page at rest, with its ink.
    clipPath(Curl.path(fold.front)) {
        paper()
        this@turning.drawContent()
    }
    if (fold.back.size < 3) return
    val back = Curl.path(fold.back)
    // The turned part: a soft shadow under it on the flat page, then its paper.
    drawIntoCanvas { canvas ->
        val shadow = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.BLACK
            setShadowLayer(18f * density, 4f * density, 0f, 0x55000000)
        }
        canvas.nativeCanvas.drawPath(back.asAndroidPath(), shadow)
    }
    clipPath(back) {
        paper()
        // The ink of the other side, mirrored, barely through the paper.
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { alpha = 0.13f })
            withTransform({ transform(Curl.mirror(m, n)) }) { this@turning.drawContent() }
            canvas.restore()
        }
        // The paper's curve: darker at the fold, a light just after it, plain at the edge.
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.22f),
                0.18f to Color.White.copy(alpha = 0.10f),
                0.6f to Color.Black.copy(alpha = 0.04f),
                1f to Color.Transparent,
                start = m,
                end = m + n * (size.width * 0.45f)
            )
        )
        drawRect(Brush.linearGradient(0f to Color.White.copy(alpha = 0.35f), 1f to Color.Transparent, start = m, end = m + n * (6f * density)))
    }
}

private fun androidx.compose.ui.graphics.drawscope.ContentDrawScope.beneath(fold: Curl.Fold?) {
    drawContent()
    if (fold == null || fold.lifted.size < 3) return
    // The shadow of the lifted part, along the fold, where this page shows.
    clipPath(Curl.path(fold.lifted)) {
        drawRect(
            Brush.linearGradient(
                0f to Color.Black.copy(alpha = 0.32f),
                1f to Color.Transparent,
                start = fold.at,
                end = fold.at - fold.normal * (size.width * 0.25f)
            )
        )
    }
}
