package io.github.teetotum_rs.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.client.request.put
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.appendPathSegments
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentLength
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.readAvailable
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource

/** The address the Knob serves its card on while the dialog is open. */
const val KNOB_URL = "http://192.168.4.1"

class CardException(override val shown: Message) :
    Exception(shown.toString()),
    Shown {
    constructor(resource: StringResource, vararg args: Any) : this(messageOf(resource, *args))
}

class CardClient(private val http: HttpClient, private val base: String = KNOB_URL) {
    /** The folder at [path], `/` or `/NAME/SUB/`. */
    suspend fun list(path: String): Listing {
        val response = http.get(url(path, folder = true)) {
            header(HttpHeaders.Accept, ContentType.Application.Json.toString())
        }
        if (!response.status.isSuccess()) {
            throw CardException(Res.string.card_error_answered, response.status.value, path)
        }
        if (response.contentType()?.match(ContentType.Application.Json) != true) {
            throw CardException(Res.string.card_error_firmware, OLDEST_FIRMWARE.toString())
        }
        val listing = Listing.decode(response.bodyAsText())
        val version = FirmwareVersion.parse(listing.version)
        if (version != null && version < OLDEST_FIRMWARE) {
            throw CardException(Res.string.card_error_firmware, OLDEST_FIRMWARE.toString())
        }
        return listing
    }

    /** Copies the file at [path] into [sink], reporting bytes done and the total the Knob announced. */
    @Suppress("TooGenericExceptionCaught") // Aborts the sink on any failure, then rethrows.
    suspend fun download(path: String, sink: FileSink, progress: (done: Long, total: Long?) -> Unit): String =
        http.prepareGet(url(path, folder = false)).execute { response ->
            if (!response.status.isSuccess()) {
                throw CardException(Res.string.card_error_answered, response.status.value, path)
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
                    throw CardException(Res.string.card_error_stopped, path, done, total)
                }
                withContext(Dispatchers.IO) { sink.commit() }
            } catch (e: Throwable) {
                withContext(Dispatchers.IO) { sink.abort() }
                throw e
            }
        }

    /** Sends [file] into the folder [path], replacing a file of that name; progress as in [download]. */
    suspend fun upload(path: String, file: Pick, progress: (done: Long, total: Long?) -> Unit) {
        val now = nowMillis()
        val target = url(
            path + file.name,
            folder = false,
            "created" to cardStamp(now),
            "modified" to cardStamp(file.modified ?: now),
        )
        val body = object : OutgoingContent.WriteChannelContent() {
            override val contentLength = file.size
            override val contentType = ContentType.Application.OctetStream

            override suspend fun writeTo(channel: ByteWriteChannel) {
                val buffer = ByteArray(CHUNK)
                var done = 0L
                withContext(Dispatchers.IO) { file.open() }.use { source ->
                    while (done < file.size) {
                        val want = minOf(buffer.size.toLong(), file.size - done).toInt()
                        val count = withContext(Dispatchers.IO) { source.read(buffer, want) }
                        if (count < 0) break
                        channel.writeFully(buffer, 0, count)
                        done += count
                        progress(done, file.size)
                    }
                }
                // A short body makes the Knob remove what arrived.
                if (done != file.size) {
                    throw CardException(Res.string.card_error_changed, file.name)
                }
            }
        }
        check(http.put(target) { setBody(body) }, file.name)
    }

    /** Makes the folder [path]. */
    suspend fun makeFolder(path: String) {
        val target = url(path, folder = false, "created" to cardStamp(nowMillis()))
        check(http.request(target) { method = MKCOL }, path.trimEnd('/').substringAfterLast('/'))
    }

    /** Removes the file or empty folder [path]. */
    suspend fun delete(path: String) {
        check(
            http.request(url(path, folder = false)) { method = HttpMethod.Delete },
            path.trimEnd('/').substringAfterLast('/'),
        )
    }

    /** Throws the Knob's own sentence, "the folder is not empty", when it refused. */
    private suspend fun check(response: HttpResponse, name: String) {
        if (response.status.isSuccess()) return
        val why = response.bodyAsText().trim()
        if (why.isEmpty()) throw CardException(Res.string.card_error_refused, name, response.status.value)
        throw CardException(Res.string.common_name_value, name, why)
    }

    private fun url(path: String, folder: Boolean, vararg query: Pair<String, String>): Url = URLBuilder(base).apply {
        val segments = path.split('/').filter { it.isNotEmpty() }
        appendPathSegments(if (folder) segments + "" else segments)
        query.forEach { (key, value) -> parameters.append(key, value) }
    }.build()

    private companion object {
        const val CHUNK = 16 * 1024
        val MKCOL = HttpMethod("MKCOL")
    }
}
