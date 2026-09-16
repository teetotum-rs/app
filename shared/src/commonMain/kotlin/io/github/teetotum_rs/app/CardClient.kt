package io.github.teetotum_rs.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext

/** The address the Knob serves its card on while the dialog is open. */
const val KNOB_URL = "http://192.168.4.1"

class CardException(message: String) : Exception(message)

class CardClient(private val http: HttpClient, private val base: String = KNOB_URL) {
    /** The folder at [path], `/` or `/NAME/SUB/`. */
    suspend fun list(path: String): Listing {
        val response = http.get(url(path, folder = true)) { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
        if (!response.status.isSuccess()) {
            throw CardException("The Knob answered ${response.status.value} for $path.")
        }
        if (response.contentType()?.match(ContentType.Application.Json) != true) {
            throw CardException("The Knob needs firmware $OLDEST_FIRMWARE or later.")
        }
        val listing = Listing.decode(response.bodyAsText())
        val version = FirmwareVersion.parse(listing.version)
        if (version != null && version < OLDEST_FIRMWARE) {
            throw CardException("The Knob needs firmware $OLDEST_FIRMWARE or later.")
        }
        return listing
    }

    /** Copies the file at [path] into [sink], reporting bytes done and the total the Knob announced. */
    suspend fun download(path: String, sink: FileSink, progress: (done: Long, total: Long?) -> Unit): String =
        http.prepareGet(url(path, folder = false)).execute { response ->
            if (!response.status.isSuccess()) {
                throw CardException("The Knob answered ${response.status.value} for $path.")
            }
            val total = response.contentLength()
            val channel = response.bodyAsChannel()
            val buffer = ByteArray(CHUNK)
            var done = 0L
            try {
                while (true) {
                    val count = channel.readAvailable(buffer)
                    if (count < 0) break
                    withContext(Dispatchers.IO) { sink.write(buffer, count) }
                    done += count
                    progress(done, total)
                }
                if (total != null && done != total) {
                    throw CardException("The download of $path stopped after $done of $total bytes.")
                }
                withContext(Dispatchers.IO) { sink.commit() }
            } catch (e: Throwable) {
                withContext(Dispatchers.IO) { sink.abort() }
                throw e
            }
        }

    private fun url(path: String, folder: Boolean): Url = URLBuilder(base).apply {
        val segments = path.split('/').filter { it.isNotEmpty() }
        appendPathSegments(if (folder) segments + "" else segments)
    }.build()

    private companion object {
        const val CHUNK = 16 * 1024
    }
}
