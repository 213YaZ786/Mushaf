package org.mushaf.app.data.sources

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.mushaf.app.core.common.writeTextAtomically
import org.mushaf.app.core.net.Net

/**
 * Where each resource is fetched from, first choice first, the others as
 * mirrors. The list comes with the app; a newer one is taken, once a week,
 * from the app's own repository, so that a server that moves or closes is
 * replaced without waiting for a new version. Files whose fingerprint the
 * app knows (the page fonts) are checked whatever server gives them.
 */
@Serializable
data class SourceList(
    val version: Int,
    val hosts: List<String> = emptyList(),
    val printFont: List<String>,
    val tajweedFont: List<String>,
    val quranCom: List<String>,
    val recitations: List<String>,
    val fawaz: List<String>,
    /** mp3quran's API (the Warsh recitations and their timing). */
    val mp3quran: List<String> = listOf("https://www.mp3quran.net/api/v3"),
    /** The speech recogniser's files, "{file}" in the place. */
    val stt: List<String> = emptyList(),
    /** Fingerprints that replace the bundled ones, "print 12" to SHA-256, when a font is reissued. */
    val fontHashes: Map<String, String> = emptyMap()
) {
    /** Every place is HTTPS and every server name well formed, or the list is refused. */
    fun valid(): Boolean {
        val urls = printFont + tajweedFont + quranCom + recitations + fawaz + stt + mp3quran
        return urls.isNotEmpty() && urls.all { it.startsWith("https://") } &&
            printFont.isNotEmpty() && quranCom.isNotEmpty() && recitations.isNotEmpty() && fawaz.isNotEmpty() &&
            fontHashes.values.all { it.matches(Regex("^[0-9a-f]{64}$")) } &&
            hosts.all { it.matches(Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")) }
    }
}

class Sources(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val file = File(context.filesDir, "sources.json")
    private val bundled: SourceList by lazy {
        json.decodeFromString<SourceList>(context.assets.open("sources.json").bufferedReader().use { it.readText() })
    }

    @Volatile private var current: SourceList = load()

    val list: SourceList get() = current

    private fun load(): SourceList {
        val fetched = runCatching { json.decodeFromString<SourceList>(file.readText()) }.getOrNull()
        val chosen = if (fetched != null && fetched.valid() && fetched.version > bundled.version) fetched else bundled
        Net.allow(chosen.hosts + hostsOf(chosen))
        return chosen
    }

    private fun hostsOf(s: SourceList) = (s.printFont + s.tajweedFont + s.quranCom + s.recitations + s.fawaz + s.stt)
        .mapNotNull { runCatching { java.net.URI(it.replace("{page}", "1")).host?.lowercase() }.getOrNull() }

    /** Asks the repository for a newer list, at most once a week; any failure keeps the one in use. */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        if (file.exists() && System.currentTimeMillis() - file.lastModified() < 7 * 24 * 3600_000L) return@withContext
        runCatching {
            val text = Net.text(REMOTE, maxBytes = 64 * 1024)
            val signature = Net.text("$REMOTE.sig", maxBytes = 1024).trim()
            // Taken only when signed by the app's own key: a list changed by anyone else is ignored.
            val list = if (signed(text.toByteArray(Charsets.UTF_8), signature)) json.decodeFromString<SourceList>(text) else null
            if (list != null && list.valid()) {
                file.writeTextAtomically(text)
                current = load()
            } else {
                file.setLastModified(System.currentTimeMillis())
            }
        }
    }

    companion object {
        /** The public half of the key sources.json is signed with (tools/sign_sources.py), ECDSA P-256. */
        const val PUBLIC_KEY = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEuQhXPaMj+sm5VXv6aIZZ6VUDHbXJoipigtnXnFRemwxMqm0RdBoZbynEIpzAPslLaEmETlWNEqGx2wvmyjVrSg=="

        /** True when [signature] (base64, DER) is the key's signature of [data]. */
        fun signed(data: ByteArray, signature: String): Boolean = runCatching {
            val key = java.security.KeyFactory.getInstance("EC")
                .generatePublic(java.security.spec.X509EncodedKeySpec(java.util.Base64.getDecoder().decode(PUBLIC_KEY)))
            java.security.Signature.getInstance("SHA256withECDSA").run {
                initVerify(key)
                update(data)
                verify(java.util.Base64.getDecoder().decode(signature))
            }
        }.getOrDefault(false)

        /** The same file as in the app, from the app's repository. */
        const val REMOTE = "https://raw.githubusercontent.com/213YaZ786/Mushaf/main/app/src/main/assets/sources.json"
    }
}
