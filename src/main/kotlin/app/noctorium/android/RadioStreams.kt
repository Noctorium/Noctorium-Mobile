package app.noctorium.android

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/*
 * Radio stations over plain http, and nothing else.
 *
 * Everything Noctorium fetches is https, and network_security_config.xml keeps it that way: Android's own http
 * stack, which Media3's requests go through, refuses cleartext to any host but the loopback. A great many radio
 * stations only stream over http, though, and their hosts are any of tens of thousands, so no list of domains in
 * that file could let them through without letting through everything.
 *
 * So the exception lives here instead, as narrow as it can be made: the player's own requests, to a host the
 * listener tuned in to as a station ([RadioHosts]), over http. Those go out on a plain socket of this file's own
 * ([PlainHttpDataSource]); every other request, the player's included, still goes through Android's stack and
 * still cannot be cleartext. What comes back is audio, read by Media3's extractors -- no cookies, no sign-in, no
 * account is ever sent this way.
 */

/** The hosts of the stations tuned in to this run, which alone may be streamed from over plain http. */
internal class RadioHosts {
    private val hosts = ConcurrentHashMap.newKeySet<String>()

    /** Lets [address]'s host stream over http, when it is an http address. */
    fun allow(address: String) {
        val uri = runCatching { Uri.parse(address) }.getOrNull() ?: return
        if (uri.scheme.equals("http", ignoreCase = true)) uri.host?.lowercase()?.let(hosts::add)
    }

    /** Whether [uri] is a station's http address. */
    fun allows(uri: Uri): Boolean =
        uri.scheme.equals("http", ignoreCase = true) && uri.host?.lowercase()?.let(hosts::contains) == true
}

/**
 * The player's requests: a station's http stream over [PlainHttpDataSource], anything else over [usual]. Which one
 * is decided at open, by the address, so one source can play a station and then a song.
 */
@OptIn(UnstableApi::class)
internal class StationRoutingDataSourceFactory(
    private val usual: DataSource.Factory,
    private val stations: RadioHosts,
    private val listener: TransferListener,
) : DataSource.Factory {
    override fun createDataSource(): DataSource = Routing(usual.createDataSource(), stations, listener)

    private class Routing(
        private val usual: DataSource,
        private val stations: RadioHosts,
        private val listener: TransferListener,
    ) : DataSource {
        private val extra = mutableListOf<TransferListener>()
        private var open: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            usual.addTransferListener(transferListener)
            extra += transferListener
        }

        override fun open(dataSpec: DataSpec): Long {
            val source = if (stations.allows(dataSpec.uri)) {
                PlainHttpDataSource().also { plain ->
                    plain.addTransferListener(listener)
                    extra.forEach(plain::addTransferListener)
                }
            } else {
                usual
            }
            open = source
            return source.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            open?.read(buffer, offset, length) ?: throw IOException("Nothing is open")

        override fun getUri(): Uri? = open?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = open?.responseHeaders ?: emptyMap()

        override fun close() {
            val source = open
            open = null
            source?.close()
        }
    }
}

/**
 * A radio stream over plain http: a GET on a socket, the answer's headers read, and the body handed to the player as
 * it arrives. HTTP/1.0, so the server sends the body as it is rather than in chunks, and it answers Shoutcast's
 * `ICY 200 OK` as well as HTTP's. Redirects to another http address are followed; one to https is refused here, since
 * a station that moved to https is played through Android's own stack the next time it is looked up.
 */
@OptIn(UnstableApi::class)
internal class PlainHttpDataSource : BaseDataSource(/* isNetwork = */ true) {
    private var socket: Socket? = null
    private var input: InputStream? = null
    private var uri: Uri? = null
    private var headers: Map<String, List<String>> = emptyMap()
    private var remaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false

    override fun open(dataSpec: DataSpec): Long {
        transferInitializing(dataSpec)
        var address = dataSpec.uri
        repeat(MAX_REDIRECTS + 1) {
            val answer = request(address, dataSpec)
            val location = answer.location
            if (answer.code in 300..399 && location != null) {
                answer.close()
                val next = Uri.parse(java.net.URI(address.toString()).resolve(location).toString())
                if (!next.scheme.equals("http", ignoreCase = true)) {
                    throw IOException("The station moved to $next, which is not a plain http stream")
                }
                address = next
                return@repeat
            }
            if (answer.code !in 200..299) {
                answer.close()
                throw IOException("The station answered ${answer.code}")
            }
            if (answer.headers.entries.any { (k, v) -> k.equals("Transfer-Encoding", true) && v.any { it.contains("chunked", true) } }) {
                answer.close()
                throw IOException("The station sent its stream in chunks, which this player does not read")
            }
            socket = answer.socket
            input = answer.body
            uri = address
            headers = answer.headers
            // A server that ignored the Range asked for starts at the beginning, so the part already had is skipped.
            if (dataSpec.position > 0 && answer.code == 200) skipFully(answer.body, dataSpec.position)
            val length = answer.headers.entries.firstOrNull { it.key.equals("Content-Length", true) }
                ?.value?.firstOrNull()?.trim()?.toLongOrNull()
                ?.let { if (answer.code == 200) it - dataSpec.position else it }
            remaining = when {
                dataSpec.length != C.LENGTH_UNSET.toLong() -> dataSpec.length
                length != null && length >= 0 -> length
                else -> C.LENGTH_UNSET.toLong()
            }
            opened = true
            transferStarted(dataSpec)
            return remaining
        }
        throw IOException("The station redirected too many times")
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (remaining == 0L) return C.RESULT_END_OF_INPUT
        val wanted = if (remaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), remaining).toInt()
        val read = (input ?: throw IOException("Not open")).read(buffer, offset, wanted)
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (remaining != C.LENGTH_UNSET.toLong()) remaining -= read
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = uri

    override fun getResponseHeaders(): Map<String, List<String>> = headers

    override fun close() {
        runCatching { input?.close() }
        runCatching { socket?.close() }
        input = null
        socket = null
        uri = null
        if (opened) {
            opened = false
            transferEnded()
        }
    }

    private class Answer(val socket: Socket, val body: InputStream, val code: Int, val headers: Map<String, List<String>>) {
        val location: String? get() = headers.entries.firstOrNull { it.key.equals("Location", true) }?.value?.firstOrNull()
        fun close() { runCatching { socket.close() } }
    }

    private fun request(address: Uri, spec: DataSpec): Answer {
        val host = address.host ?: throw IOException("No host in $address")
        val port = address.port.takeIf { it > 0 } ?: 80
        val path = (address.encodedPath?.takeIf { it.isNotEmpty() } ?: "/") +
            (address.encodedQuery?.let { "?$it" } ?: "")
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            socket.soTimeout = READ_TIMEOUT_MS
            val request = buildString {
                append("GET ").append(path).append(" HTTP/1.0\r\n")
                append("Host: ").append(host).append(if (port != 80) ":$port" else "").append("\r\n")
                append("User-Agent: ").append(USER_AGENT).append("\r\n")
                append("Accept: */*\r\n")
                // Song titles mixed into the audio would be heard as noise by an extractor that was not told.
                append("Icy-MetaData: 0\r\n")
                append("Connection: close\r\n")
                if (spec.position > 0) append("Range: bytes=").append(spec.position).append("-\r\n")
                spec.httpRequestHeaders.forEach { (k, v) ->
                    if (k.none { it == '\r' || it == '\n' } && v.none { it == '\r' || it == '\n' }) append(k).append(": ").append(v).append("\r\n")
                }
                append("\r\n")
            }
            socket.getOutputStream().apply { write(request.toByteArray(Charsets.ISO_8859_1)); flush() }
            val body = BufferedInputStream(socket.getInputStream(), 16 * 1024)
            val status = readLine(body) ?: throw IOException("The station closed the connection without answering")
            val code = STATUS.find(status)?.groupValues?.get(1)?.toIntOrNull()
                ?: throw IOException("The station's answer was not understood: ${status.take(60)}")
            val headers = LinkedHashMap<String, MutableList<String>>()
            while (true) {
                val line = readLine(body) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon <= 0) continue
                headers.getOrPut(line.substring(0, colon).trim()) { mutableListOf() } += line.substring(colon + 1).trim()
                if (headers.size > MAX_HEADERS) throw IOException("The station sent too many headers")
            }
            return Answer(socket, body, code, headers)
        } catch (error: Throwable) {
            runCatching { socket.close() }
            throw if (error is IOException) error else IOException(error)
        }
    }

    companion object {
        private const val MAX_REDIRECTS = 5
        private const val MAX_HEADERS = 100
        private const val MAX_LINE = 8 * 1024
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 20_000
        private const val USER_AGENT = "Noctorium"
        private val STATUS = Regex("^(?:HTTP/\\d(?:\\.\\d)?|ICY)\\s+(\\d{3})")

        /** One header line, without its line ending; null at the end of the stream. */
        internal fun readLine(input: InputStream): String? {
            val line = StringBuilder()
            while (true) {
                val b = input.read()
                if (b == -1) return if (line.isEmpty()) null else line.toString()
                if (b == '\n'.code) return line.toString().trimEnd('\r')
                if (line.length >= MAX_LINE) throw IOException("A header line from the station was too long")
                line.append(b.toChar())
            }
        }

        private fun skipFully(input: InputStream, count: Long) {
            var left = count
            val scratch = ByteArray(8 * 1024)
            while (left > 0) {
                val read = input.read(scratch, 0, minOf(scratch.size.toLong(), left).toInt())
                if (read == -1) throw IOException("The stream ended early")
                left -= read
            }
        }
    }
}
