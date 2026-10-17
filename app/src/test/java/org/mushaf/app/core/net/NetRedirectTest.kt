package org.mushaf.app.core.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.URL

/**
 * A redirect is followed only when its target is HTTPS to a server on the
 * list, at most three times. Same protocol as PrivacyTest beside it: the
 * rule is checked as code, hostile samples included. The manual follow-up is
 * the one https://developer.android.com/reference/java/net/HttpURLConnection#setInstanceFollowRedirects(boolean)
 * leaves to the caller.
 */
class NetRedirectTest {

    private val start = URL("https://github.com/213YaZ786/Mushaf/releases/download/stt-1/model.bin")
    private val github = setOf("api.github.com", "github.com", "objects.githubusercontent.com", "release-assets.githubusercontent.com")
    private val allow: (String) -> Boolean = { it in github }

    private fun follow(location: String?, hops: Int = 0, list: (String) -> Boolean = allow) = Net.redirect(start, location, hops, list)

    @Test
    fun anAllowedHttpsServerIsFollowed() {
        assertEquals(
            URL("https://release-assets.githubusercontent.com/x?sig=1"),
            follow("https://release-assets.githubusercontent.com/x?sig=1")
        )
        assertEquals(URL("https://github.com/other/path"), follow("/other/path"))
        assertEquals(URL("https://github.com/213YaZ786/Mushaf/releases/download/stt-1/b"), follow("b"))
        assertEquals("objects.githubusercontent.com", follow("HTTPS://OBJECTS.GITHUBUSERCONTENT.COM/x").host.lowercase())
    }

    @Test
    fun anotherServerIsRefused() {
        for (location in listOf(
            "https://evil.example/x",
            "https://github.com.evil.example/x",
            "https://api.github.com.evil.example/",
            "https://evilgithub.com/",
            "https://githubusercontent.com/",
            "https://x.objects.githubusercontent.com/",
            "//evil.example/x",
            "https://[::1]/",
            "https://127.0.0.1/"
        )) {
            assertThrows(location, Net.Refused::class.java) { follow(location) }
        }
    }

    @Test
    fun theServerBehindAUserNameIsTheOneReached() {
        // java.net.URL reads the host after the @: the check and the connection see the same one.
        assertThrows(Net.Refused::class.java) { follow("https://github.com@evil.example/x") }
        assertEquals("github.com", follow("https://evil.example@github.com/x").host)
    }

    @Test
    fun plainHttpAndOtherSchemesAreRefused() {
        assertThrows(Net.Refused::class.java) { follow("http://github.com/x") }
        assertThrows(Net.Refused::class.java) { follow("HTTP://release-assets.githubusercontent.com/x") }
        assertThrows(Net.Refused::class.java) { follow("ftp://github.com/x") }
        assertThrows(Net.Refused::class.java) { follow("file:///data/data/org.mushaf.app/x") }
        assertThrows(IOException::class.java) { follow("javascript:alert(1)") }
        assertThrows(IOException::class.java) { follow("https://") }
    }

    @Test
    fun aRedirectWithNoPlaceIsRefused() {
        assertThrows(IOException::class.java) { follow(null) }
        assertThrows(IOException::class.java) { follow("") }
        assertThrows(IOException::class.java) { follow("   ") }
    }

    @Test
    fun atMostThreeRedirectsAreFollowed() {
        for (hops in 0..2) assertEquals(URL("https://github.com/x"), follow("https://github.com/x", hops))
        assertThrows(Net.Refused::class.java) { follow("https://github.com/x", Net.MAX_REDIRECTS) }
        assertThrows(Net.Refused::class.java) { follow("https://github.com/x", 4) }
    }

    @Test
    fun aVeryLongLocationDoesNotCrashTheCheck() {
        val long = "https://github.com/" + "a".repeat(100_000)
        assertEquals("github.com", follow(long).host)
        assertThrows(Net.Refused::class.java) { follow("https://" + "a".repeat(100_000) + ".example/") }
    }

    @Test
    fun theFirstRequestIsJudgedLikeEveryHop() {
        // Refused before any connection is made.
        assertThrows(Net.Refused::class.java) { Net.open("http://api.quran.com/api/v4/chapters") }
        assertThrows(Net.Refused::class.java) { Net.open("https://evil.example/x") }
        assertThrows(Net.Refused::class.java) { Net.open("http://api.github.com/x", allow) }
    }

    // A source scan like PrivacyTest.nothingIsSent; the default it guards against is documented at
    // https://developer.android.com/reference/java/net/HttpURLConnection#setInstanceFollowRedirects(boolean)
    @Test
    fun noConnectionIsOpenedOutsideNet() {
        val others = java.io.File("src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Net.kt" && "openConnection(" in it.readText() }
            .map { it.name }.toList()
        assertEquals(emptyList<String>(), others)
        assertFalse("instanceFollowRedirects = true" in java.io.File("src/main/java/org/mushaf/app/core/net/Net.kt").readText())
    }

    @Test
    fun theAppsOwnListHoldsNumberedMp3quranServersOnly() {
        assertTrue(Net.allowed("server6.mp3quran.net"))
        assertTrue(Net.allowed("api.quran.com"))
        assertFalse(Net.allowed("evilmp3quran.net"))
        assertFalse(Net.allowed("mp3quran.net.evil.example"))
        assertFalse(Net.allowed(""))
    }
}
