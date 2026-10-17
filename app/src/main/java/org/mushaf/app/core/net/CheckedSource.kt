package org.mushaf.app.core.net

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * The audio's network source, held to the servers [Net] allows, as Net's own requests are:
 * the address asked and the one finally read after Media3's own redirects must both be
 * HTTPS to an allowed server, else nothing is read. Built as Media3's ResolvingDataSource
 * wraps its upstream (https://github.com/androidx/media, libraries/datasource/src/main/java/
 * androidx/media3/datasource/ResolvingDataSource.java); the address read comes from
 * DataSource.getUri, which "will be identical to the one passed open(DataSpec) in the
 * DataSpec unless redirection has occurred"
 * (https://developer.android.com/reference/androidx/media3/datasource/DataSource#getUri()).
 */
@UnstableApi
class CheckedSource(private val upstream: DataSource) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) = upstream.addTransferListener(transferListener)

    override fun open(dataSpec: DataSpec): Long {
        check(dataSpec.uri)
        val length = upstream.open(dataSpec)
        val read = upstream.uri ?: dataSpec.uri
        try {
            check(read)
        } catch (e: Net.Refused) {
            upstream.close()
            throw e
        }
        return length
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = upstream.read(buffer, offset, length)

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() = upstream.close()

    /** HTTPS to an allowed server (Net.allowed), the rule of Net.checked. */
    private fun check(uri: Uri) {
        val host = uri.host?.lowercase() ?: throw Net.Refused("no server")
        if (uri.scheme != "https") throw Net.Refused("not https")
        if (!Net.allowed(host)) throw Net.Refused("unknown server $host")
    }

    /** The factory Media3 asks for, around [upstream]'s. */
    class Factory(private val upstream: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource = CheckedSource(upstream.createDataSource())
    }
}
