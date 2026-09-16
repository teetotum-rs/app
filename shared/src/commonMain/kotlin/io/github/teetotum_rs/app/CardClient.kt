package io.github.teetotum_rs.app

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.URLBuilder
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess

/** The address the Knob serves its card on while the dialog is open. */
const val KNOB_URL = "http://192.168.4.1"

class CardException(message: String) : Exception(message)

class CardClient(private val http: HttpClient, private val base: String = KNOB_URL) {
    /** The folder at [path], `/` or `/NAME/SUB/`. */
    suspend fun list(path: String): Listing {
        val url = URLBuilder(base).apply {
            val segments = path.split('/').filter { it.isNotEmpty() }
            appendPathSegments(segments + "")
        }.build()
        val response = http.get(url) { header(HttpHeaders.Accept, ContentType.Application.Json.toString()) }
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
}
