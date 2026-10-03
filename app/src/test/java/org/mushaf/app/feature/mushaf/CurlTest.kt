package org.mushaf.app.feature.mushaf

import androidx.compose.ui.geometry.Offset
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurlTest {

    private fun area(p: List<Offset>): Float {
        var a = 0f
        for (i in p.indices) { val u = p[i]; val v = p[(i + 1) % p.size]; a += u.x * v.y - v.x * u.y }
        return abs(a) / 2f
    }

    @Test
    fun flatIsNoFold() {
        assertNull(Curl.fold(400f, 640f, 0f, 0.9f))
    }

    @Test
    fun theTwoSidesMakeThePage() {
        for (t in listOf(0.1f, 0.42f, 0.8f)) for (g in listOf(0.1f, 0.5f, 0.9f)) {
            val f = Curl.fold(400f, 640f, t, g)!!
            assertEquals(400f * 640f, area(f.front) + area(f.lifted), 1f)
        }
    }

    @Test
    fun theBackIsTheLiftedPartTurnedOver() {
        val f = Curl.fold(400f, 640f, 0.42f, 0.9f)!!
        // Same size, and every point as far from the fold as before.
        assertEquals(area(f.lifted), area(f.back), 0.5f)
        for ((a, b) in f.lifted.zip(f.back)) {
            val da = (a.x - f.at.x) * f.normal.x + (a.y - f.at.y) * f.normal.y
            val db = (b.x - f.at.x) * f.normal.x + (b.y - f.at.y) * f.normal.y
            assertEquals(-da, db, 0.01f)
        }
    }

    @Test
    fun takenLowTheCornerGoesFirst() {
        val f = Curl.fold(400f, 640f, 0.3f, 0.9f)!!
        // The lifted part is the free edge's side, larger at the bottom than at the top.
        val xs = f.lifted.filter { it.y == 640f }.maxOf { it.x }
        val xt = f.lifted.filter { it.y == 0f }.maxOfOrNull { it.x } ?: 0f
        assertTrue(xs > xt)
    }
}
