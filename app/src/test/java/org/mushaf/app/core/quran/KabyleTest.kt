package org.mushaf.app.core.quran

import org.junit.Assert.assertEquals
import org.junit.Test

class KabyleTest {

    @Test
    fun alFatihahReadsInKabyle() {
        assertEquals("S yisem n Ṛebbi, Aḥnin Itḥunun", Kabyle.fromLegacy("S yisem n Öebbi, Aênin Ipêunun"))
        assertEquals("Ccekṛan i Ṛebbi, Mass imaḍalen", Kabyle.fromLegacy("Ccekôan i Öebbi, Mass imavalen"))
        assertEquals("D Kečč ay naɛbed", Kabyle.fromLegacy("D Keçç ay naâbed"))
        assertEquals("Awi yaɣ d ubrid iweqmen", Kabyle.fromLegacy("Awi ya\$ d ubrid iweqmen"))
    }
}
