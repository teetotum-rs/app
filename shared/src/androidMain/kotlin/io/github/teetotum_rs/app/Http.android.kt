package io.github.teetotum_rs.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import java.util.concurrent.TimeUnit

/** The Knob can take seconds to close a file on the card before it answers. */
actual fun httpClient(): HttpClient = HttpClient(OkHttp) {
    engine {
        config {
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
        }
    }
}
