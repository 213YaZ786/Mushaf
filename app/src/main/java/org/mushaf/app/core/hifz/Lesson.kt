package org.mushaf.app.core.hifz

import java.util.BitSet
import kotlinx.serialization.Serializable
import org.mushaf.app.core.quran.AyahKey

/** The order new ayat are learnt in. */
@Serializable
enum class Order {
    /** Juz 'Amma first: from An-Nas back towards Al-Baqarah, each surah from its first ayah. */
    FROM_AN_NAS,
    /** From Al-Fatihah on, in the mushaf's order. */
    FROM_AL_FATIHAH
}

object Lesson {

    /** Every ayah in the order of [order], given the Quran's ayat in order. */
    fun ordered(ayat: List<AyahKey>, order: Order): List<AyahKey> = when (order) {
        Order.FROM_AL_FATIHAH -> ayat
        Order.FROM_AN_NAS -> ayat.groupBy { it.surah }.toSortedMap(reverseOrder()).values.flatten()
    }

    /**
     * The next lesson: the first ayat not known yet in [ordered], about
     * [lines] lines of the mushaf long ([linesOf] tells an ayah's lines),
     * never fewer than one ayah, and never past the end of a surah.
     */
    fun next(ordered: List<AyahKey>, known: (AyahKey) -> Boolean, linesOf: (AyahKey) -> Int, lines: Int): List<AyahKey> {
        val start = ordered.indexOfFirst { !known(it) }
        if (start < 0) return emptyList()
        val out = mutableListOf<AyahKey>()
        var total = 0
        for (i in start until ordered.size) {
            val k = ordered[i]
            if (out.isNotEmpty() && (k.surah != out.first().surah || known(k) || total >= lines)) break
            out += k
            total += linesOf(k).coerceAtLeast(1)
        }
        return out
    }
}

/** A set of ayat as runs of their place in the Quran (0 to 6235): "0-6,9,11-20". */
object Runs {

    fun encode(bits: BitSet): String = buildList {
        var i = bits.nextSetBit(0)
        while (i >= 0) {
            val end = bits.nextClearBit(i) - 1
            add(if (end == i) "$i" else "$i-$end")
            i = bits.nextSetBit(end + 1)
        }
    }.joinToString(",")

    fun decode(text: String): BitSet = BitSet().apply {
        for (part in text.split(',')) {
            val p = part.trim()
            if (p.isEmpty()) continue
            val dash = p.indexOf('-')
            if (dash < 0) p.toIntOrNull()?.let { set(it) }
            else {
                val a = p.substring(0, dash).toIntOrNull() ?: continue
                val b = p.substring(dash + 1).toIntOrNull() ?: continue
                if (b >= a) set(a, b + 1)
            }
        }
    }
}
