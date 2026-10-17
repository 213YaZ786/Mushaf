package org.mushaf.app.core.net

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Every connection the app makes goes through here, and only here:
 * - HTTPS only, to the servers the app knows ([allowed]); a redirect is
 *   followed by hand, only to another known server, three at most;
 * - every answer has a ceiling on its size, so a server cannot fill the
 *   phone or the memory;
 * - a file is written beside its place and moved there only once whole,
 *   and, when its fingerprint is known, only if its SHA-256 matches.
 * No cookies, no account, no identifier: a plain User-Agent.
 */
object Net {

    /** The servers known at build time; the sources list may add mirrors (see Sources). */
    private val builtIn = setOf(
        "api.quran.com",
        "static.qurancdn.com",
        "verses.quran.foundation",
        "audio.qurancdn.com",
        "download.quranicaudio.com",
        "cdn.jsdelivr.net",
        "raw.githubusercontent.com",
        "everyayah.com",
        // The app's own releases: the speech model, and later its updates.
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com",
        "huggingface.co"
    )

    @Volatile private var extra: Set<String> = emptySet()

    /** Hosts added by the sources list fetched from the app's own repository. */
    fun allow(hosts: Collection<String>) {
        extra = hosts.filter { HOST.matches(it) }.toSet()
    }

    /** mp3quran.net serves its recordings from numbered servers (server6, server11...). */
    fun allowed(host: String): Boolean =
        host in builtIn || host in extra || host == "mp3quran.net" || host.endsWith(".mp3quran.net")

    private val HOST = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

    class Refused(message: String) : IOException(message)

    /** The body of [url] as text, at most [maxBytes]. */
    fun text(url: String, maxBytes: Long = 8L shl 20): String = String(bytes(url, maxBytes), Charsets.UTF_8)

    fun bytes(url: String, maxBytes: Long): ByteArray {
        open(url).let { conn ->
            try {
                val length = conn.contentLengthLong
                if (length > maxBytes) throw Refused("too large")
                val out = java.io.ByteArrayOutputStream(if (length in 1..maxBytes) length.toInt() else 64 * 1024)
                copy(conn, out, maxBytes) { }
                return out.toByteArray()
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * Fetches [url] into [target]: whole, at most [maxBytes], with the
     * SHA-256 [sha256] when given. [progress] hears the bytes as they come.
     */
    fun download(url: String, target: File, maxBytes: Long, sha256: String? = null, progress: (Long) -> Unit = {}) {
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val conn = open(url)
        try {
            val length = conn.contentLengthLong
            if (length > maxBytes) throw Refused("too large")
            val digest = MessageDigest.getInstance("SHA-256")
            part.outputStream().use { out ->
                copy(conn, object : java.io.OutputStream() {
                    override fun write(b: Int) { out.write(b); digest.update(b.toByte()) }
                    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); digest.update(b, off, len) }
                }, maxBytes, progress)
            }
            if (sha256 != null) {
                val got = digest.digest().joinToString("") { "%02x".format(it) }
                if (!got.equals(sha256, ignoreCase = true)) throw Refused("fingerprint differs")
            }
            if (!part.renameTo(target)) throw IOException("could not keep the file")
        } finally {
            conn.disconnect()
            part.delete()
        }
    }

    /** Redirects followed by hand: the first request and three more. */
    internal const val MAX_REDIRECTS = 3

    /**
     * Opens [start] and answers once it says 200. [allow] tells which servers
     * may be reached (the app's list by default); [accept] sets an Accept header.
     * Redirects are never left to HttpURLConnection
     * (instanceFollowRedirects = false, the way developer.android.com documents:
     * https://developer.android.com/reference/java/net/HttpURLConnection#setInstanceFollowRedirects(boolean));
     * each hop is read here and goes through [checked] before it is followed.
     */
    internal fun open(start: String, allow: (String) -> Boolean = ::allowed, accept: String? = null): HttpURLConnection {
        var url = checked(URL(start), allow)
        var hops = 0
        while (true) {
            val conn = url.openConnection() as HttpURLConnection
            // The app only ever reads: no request carries anything from the phone.
            conn.requestMethod = "GET"
            conn.doOutput = false
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.useCaches = false
            conn.setRequestProperty("User-Agent", "Mushaf")
            conn.setRequestProperty("Accept-Encoding", "identity")
            if (accept != null) conn.setRequestProperty("Accept", accept)
            when (val code = conn.responseCode) {
                200 -> return conn
                301, 302, 303, 307, 308 -> {
                    val location = conn.getHeaderField("Location")
                    conn.disconnect()
                    url = redirect(url, location, hops++, allow)
                }
                else -> {
                    conn.disconnect()
                    throw IOException("HTTP $code")
                }
            }
        }
    }

    /**
     * [url] when it is HTTPS to a server [allow] names, else Refused. Same
     * check as before the first request, applied to each hop of a redirect.
     * Source: the manual follow-up that
     * https://developer.android.com/reference/java/net/HttpURLConnection#setInstanceFollowRedirects(boolean)
     * leaves to the caller (read Location, judge it, open it); HTTPS only as in
     * https://developer.android.com/privacy-and-security/risks/cleartext.
     */
    internal fun checked(url: URL, allow: (String) -> Boolean): URL {
        if (url.protocol != "https") throw Refused("not https")
        if (!allow(url.host.lowercase())) throw Refused("unknown server ${url.host}")
        return url
    }

    /**
     * The place [location] names from [from], checked; [hops] is how many redirects were followed already.
     * Source: as [checked] (https://developer.android.com/reference/java/net/HttpURLConnection#setInstanceFollowRedirects(boolean)).
     */
    internal fun redirect(from: URL, location: String?, hops: Int, allow: (String) -> Boolean): URL {
        if (hops >= MAX_REDIRECTS) throw Refused("too many redirects")
        if (location.isNullOrBlank()) throw IOException("redirect without a place")
        return checked(URL(from, location), allow)
    }

    private fun copy(conn: HttpURLConnection, out: java.io.OutputStream, maxBytes: Long, progress: (Long) -> Unit) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        conn.inputStream.use { input ->
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > maxBytes) throw Refused("too large")
                out.write(buffer, 0, n)
                progress(total)
            }
        }
    }
}
