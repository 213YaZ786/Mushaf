package org.mushaf.app.core.hifz

import org.junit.Assert.assertEquals
import org.junit.Test

class LikenessTest {

    @Test
    fun theWordsThatDifferStandOut() {
        // 2:5 and 31:5 differ by nothing; 2:48 and 2:123 by the order of two phrases.
        val a = "وَٱتَّقُواْ يَوۡمٗا لَّا تَجۡزِي نَفۡسٌ عَن نَّفۡسٖ شَيۡـٔٗا وَلَا يُقۡبَلُ مِنۡهَا شَفَٰعَةٞ وَلَا يُؤۡخَذُ مِنۡهَا عَدۡلٞ".split(" ")
        val b = "وَٱتَّقُواْ يَوۡمٗا لَّا تَجۡزِي نَفۡسٌ عَن نَّفۡسٖ شَيۡـٔٗا وَلَا يُقۡبَلُ مِنۡهَا عَدۡلٞ وَلَا تَنفَعُهَا شَفَٰعَةٞ".split(" ")
        val (inA, inB) = Likeness.shared(a, b)
        // The opening is shared word for word.
        assertEquals(true, (0..10).all { inA[it] && inB[it] })
        // What follows sets them apart.
        assertEquals(true, inA.count { !it } >= 2)
        assertEquals(true, inB.count { !it } >= 2)
    }

    @Test
    fun theSameAyahIsAllShared() {
        val a = "قُلۡ هُوَ ٱللَّهُ أَحَدٌ".split(" ")
        val (inA, inB) = Likeness.shared(a, a)
        assertEquals(true, inA.all { it } && inB.all { it })
    }
}
