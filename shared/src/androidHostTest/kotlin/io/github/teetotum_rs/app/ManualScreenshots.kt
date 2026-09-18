package io.github.teetotum_rs.app

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.v2.runComposeUiTest
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.TimeZone
import kotlin.test.Test

/**
 * The app screens the user manual shows, drawn with a Knob and a network in memory so no real device, network or
 * name appears. `./gradlew recordRoborazziAndroidHostTest` writes them to docs/manual/images.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Robolectric needs Java 21 from SDK 35 on; the build compiles for 17.
@Config(sdk = [34], qualifiers = RobolectricDeviceQualifiers.Pixel7)
// One test per picture in the manual.
@Suppress("TooManyFunctions")
class ManualScreenshots {
    /** Streams downloads from the card; they stop halfway and wait until the test ends. */
    private val streams = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** The phone's time in the pictures; it moves only when a test moves it. */
    private val clock = ManualClock()
    private val zone = TimeZone.getDefault()

    @Before
    fun fixZone() = TimeZone.setDefault(TimeZone.getTimeZone("UTC"))

    @After
    fun stopStreams() {
        streams.cancel()
        TimeZone.setDefault(zone)
    }

    private fun ComposeUiTest.app(
        page: Page,
        bluetooth: Bluetooth = ManualKnob(clock),
        code: JoinCode? = null,
        catalogue: CatalogueLoad = CatalogueLoad.Tap,
        picks: List<Pick> = emptyList(),
        start: Start = Start.Last,
    ) = setContent {
        App(
            radio = NoRadio,
            downloads = NoDownloads,
            bluetooth = bluetooth,
            bluetoothAccess = { it() },
            picker = { onPick -> { onPick(picks) } },
            back = { _, _ -> },
            code = code,
            preferences = Preferences(start = start, catalogue = catalogue, lastPage = page),
            http = { HttpClient(MockEngine { answer(it) }) },
            now = { clock.millis },
        ) { }
    }

    private fun ComposeUiTest.shoot(name: String) {
        waitForIdle()
        onRoot().captureRoboImage("$IMAGES/$name.png")
    }

    private fun ComposeUiTest.waitForText(text: String) = waitUntil(timeoutMillis = WAIT_MS) {
        onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
    }

    @Test
    fun home() = runComposeUiTest {
        app(Page.Home)
        shoot("app-home")
    }

    @Test
    fun menu() = runComposeUiTest {
        app(Page.Home)
        onNodeWithContentDescription("Open menu").performClick()
        shoot("app-menu")
    }

    @Test
    fun cardFolder() = runComposeUiTest {
        app(Page.Card, code = CODE)
        waitForText("notes.txt")
        shoot("app-card-folder")
    }

    @Test
    fun cardTransfer() = runComposeUiTest {
        app(Page.Card, code = CODE)
        waitForText(PODCAST)
        onNodeWithText(PODCAST).performClick()
        waitForText("${sizeText(PODCAST_HALF.toLong())} of")
        shoot("app-card-transfer")
    }

    @Test
    fun status() = runComposeUiTest {
        app(Page.Status, ManualKnob(clock, running = NEW_FIRMWARE))
        waitForText("Running for")
        shoot("app-status")
    }

    @Test
    fun pluginsCatalogue() = runComposeUiTest {
        app(Page.Plugins, catalogue = CatalogueLoad.Open)
        waitForText(NEARBY_SUMMARY)
        // Scrolled to the end: the whole catalogue and the card for your own plugin.
        onNode(hasScrollAction() and hasAnyDescendant(hasText(NEARBY_SUMMARY)))
            .performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, Float.MAX_VALUE) }
        shoot("app-plugins-catalogue")
    }

    @Test
    fun pluginsSending() = runComposeUiTest {
        app(Page.Plugins, ManualKnob(clock, pluginSent = PLUGIN_PART), catalogue = CatalogueLoad.Open)
        waitForText(NEARBY_SUMMARY)
        onAllNodesWithText("Send to Knob")[0].performScrollTo().performClick()
        waitForText("% of ${sizeText(TestPlugins.entry.size.toLong())}")
        shoot("app-plugins-sending")
    }

    @Test
    fun pluginsOnKnob() = runComposeUiTest {
        app(Page.Plugins)
        waitForText(COUNTER_SUMMARY)
        shoot("app-plugins-on-knob")
    }

    @Test
    fun knobSettings() = runComposeUiTest {
        app(Page.KnobSettings)
        waitForText("Brightness")
        shoot("app-knob-settings")
    }

    @Test
    fun firmwarePicked() = runComposeUiTest {
        app(Page.Firmware, picks = listOf(firmwarePick(NEW_FIRMWARE)))
        onNodeWithText("Choose file").performClick()
        waitForText(FIRMWARE_FILE)
        shoot("app-firmware-picked")
    }

    @Test
    fun firmwareWarning() = runComposeUiTest {
        app(Page.Firmware, ManualKnob(clock, running = NEW_FIRMWARE), picks = listOf(firmwarePick(NEW_FIRMWARE)))
        onNodeWithText("Choose file").performClick()
        waitForText("The Knob already runs")
        shoot("app-firmware-warning")
    }

    @Test
    fun firmwareSending() = runComposeUiTest {
        val knob = ManualKnob(clock, firmwareHangs = true)
        app(Page.Firmware, knob, picks = listOf(firmwarePick(NEW_FIRMWARE)))
        onNodeWithText("Choose file").performClick()
        waitForText(FIRMWARE_FILE)
        onNodeWithText("Send to Knob").performClick()
        // What a Bluetooth link sends in that time, about 40 % of the file.
        clock.millis += SENDING_FOR_S * 1000
        runOnIdle { knob.firmwareProgress(SENDING_FOR_S * BLUETOOTH_BYTES_PER_S) }
        waitForText("/s ·")
        shoot("app-firmware-sending")
    }

    @Test
    fun firmwareDone() = runComposeUiTest {
        app(Page.Firmware, picks = listOf(firmwarePick(NEW_FIRMWARE)))
        onNodeWithText("Choose file").performClick()
        waitForText(FIRMWARE_FILE)
        onNodeWithText("Send to Knob").performClick()
        waitForText("The Knob restarted")
        shoot("app-firmware-done")
    }

    @Test
    fun settings() = runComposeUiTest {
        // As a new user finds them: opening on Home.
        app(Page.Home, start = Start.Home)
        onNodeWithContentDescription("Open settings").performClick()
        shoot("app-settings")
    }

    @Test
    fun about() = runComposeUiTest {
        app(Page.About)
        shoot("app-about")
    }

    /** The catalogue, the Knob's card and the one plugin the catalogue sends, as the network would answer. */
    private fun MockRequestHandleScope.answer(request: HttpRequestData) = when (val url = request.url.toString()) {
        CATALOGUE_URL -> respond(
            Json.encodeToString(Catalogue.serializer(), Catalogue(1, CATALOGUE)),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

        TestPlugins.entry.url -> respond(TestPlugins.remote)

        "$KNOB_URL/" -> respond(
            Json.encodeToString(Listing.serializer(), ROOT),
            headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
        )

        "$KNOB_URL/$PODCAST" -> respond(
            streams.writer {
                channel.writeFully(ByteArray(PODCAST_HALF))
                channel.flush()
                awaitCancellation()
            }.channel,
            headers = headersOf(HttpHeaders.ContentLength, PODCAST_SIZE.toString()),
        )

        else -> respondError(HttpStatusCode.NotFound, "no $url in screenshots")
    }

    /**
     * A Knob in memory that runs [running], has a card and the bundled plugins plus one of its own. Sending a plugin
     * stops after [pluginSent] bytes when given; sending firmware waits for [firmwareProgress] when [firmwareHangs],
     * otherwise the Knob restarts into the new firmware.
     */
    private class ManualClock(var millis: Long = SHOT_AT)

    private class ManualKnob(
        private val clock: ManualClock,
        private var running: String = OLD_FIRMWARE,
        private val pluginSent: Int? = null,
        private val firmwareHangs: Boolean = false,
    ) : Bluetooth by FakeBluetooth(KNOB_PLUGINS) {
        private var reportFirmware: (Int) -> Unit = {}
        private var restarted = false

        fun firmwareProgress(sent: Long) = reportFirmware(sent.toInt())

        override suspend fun status(): KnobStatus {
            // The app takes the Knob for restarted once its uptime is shorter than the time since the update.
            if (restarted) clock.millis += RESTART_MS
            return KnobStatus(
                uptimeSeconds = if (restarted) 0 else UPTIME_S,
                networks = NETWORKS,
                version = running,
                cardBytes = CARD_BYTES,
            )
        }

        override suspend fun sendPlugin(module: ByteArray, header: ByteArray, progress: (sent: Int) -> Unit) {
            if (pluginSent == null) return progress(module.size)
            progress(pluginSent)
            awaitCancellation()
        }

        override suspend fun sendFirmware(image: ByteArray, signature: ByteArray, progress: (sent: Int) -> Unit) {
            if (firmwareHangs) {
                reportFirmware = progress
                awaitCancellation()
            }
            progress(image.size)
            running = NEW_FIRMWARE
            restarted = true
        }
    }

    private object NoRadio : Radio {
        override suspend fun join(code: JoinCode) = Unit

        override fun leave() = Unit
    }

    private object NoDownloads : Downloads {
        override fun create(name: String): FileSink = object : FileSink {
            override fun write(bytes: ByteArray, count: Int) = Unit

            override fun commit() = name

            override fun abort() = Unit
        }
    }

    private companion object {
        const val IMAGES = "../docs/manual/images"
        const val WAIT_MS = 10_000L

        /** 2026-09-18 10:24:00 UTC. */
        const val SHOT_AT = 1_789_727_040_000L
        const val SENDING_FOR_S = 72L
        const val RESTART_MS = 5_000L

        /** About what a firmware update over Bluetooth reaches, 11 to 12 KiB/s. */
        const val BLUETOOTH_BYTES_PER_S = 11_800L

        const val OLD_FIRMWARE = "v0.3.3 5d6e7f8"
        const val NEW_FIRMWARE = "v0.4.0 1a2b3c4"
        const val FIRMWARE_FILE = "teetotum-v0.4.0.tfw"
        const val FIRMWARE_SIZE = 2_113_264

        const val UPTIME_S = 2L * 3600 + 34 * 60 + 12
        const val NETWORKS = 7
        const val CARD_BYTES = 31_914_983_424L

        const val PLUGIN_PART = 820
        const val NEARBY_SUMMARY = "Wi-Fi and Bluetooth around you"
        const val COUNTER_SUMMARY = "counts detents"

        const val PODCAST = "interview.mp3"
        const val PODCAST_SIZE = 40_265_318
        const val PODCAST_HALF = 17_825_792

        val CODE = JoinCode("TeeToTum", "knob-password")

        val ROOT = Listing(
            version = NEW_FIRMWARE,
            path = "/",
            cardBytes = CARD_BYTES,
            entries = listOf(
                Entry("covers", directory = true, size = 0),
                Entry("music", directory = true, size = 0),
                Entry("podcasts", directory = true, size = 0),
                Entry(PODCAST, directory = false, size = PODCAST_SIZE.toLong()),
                Entry("notes.txt", directory = false, size = 1_234),
                Entry("playlist.m3u", directory = false, size = 2_871),
            ),
        )

        val KNOB_PLUGINS = listOf(
            bundled("HID remote", "remote for the phone's player", "41a9ad2d2290788d", 1381),
            bundled("Teetotum", "a die of 2 to 256 sides", "6799220a4230b177", 2737),
            bundled("Nearby", NEARBY_SUMMARY, "a699c0784d62a9df", 7266),
            KnobPlugin("Counter", COUNTER_SUMMARY, "0.1.0", "0f1e2d3c4b5a6978", 1720, 0, false, true),
        )

        val CATALOGUE = listOf(
            TestPlugins.entry,
            CataloguePlugin(
                name = "Teetotum",
                summary = "a die of 2 to 256 sides",
                version = "0.0.0",
                rights = listOf("KNOB", "RANDOM"),
                id = "6799220a4230b177",
                key = TestPlugins.entry.key,
                size = 2737,
                sha256 = "c5ac0f92387c74390f51cfc4dfd58d2b8c3daa3af2b047559cf3a6f4ca2e6195",
                url = "https://example.invalid/teetotum-plugin.wasm",
            ),
            CataloguePlugin(
                name = "Nearby",
                summary = NEARBY_SUMMARY,
                version = "0.0.0",
                rights = listOf("KNOB", "RADIO", "HAPTIC"),
                id = "a699c0784d62a9df",
                key = TestPlugins.entry.key,
                size = 7266,
                sha256 = "d0149637e26e0ba88a6e51fb192bd425d5530fb8b35b2cf2dd7f8be7eb27b6c0",
                url = "https://example.invalid/nearby.wasm",
            ),
        )

        fun bundled(name: String, summary: String, id: String, size: Long) =
            KnobPlugin(name, summary, "0.0.0", id, size, slot = null, bundled = true, installed = true)

        /** A signed firmware file that names itself [version], laid out as the Knob's firmware images are. */
        fun firmwarePick(version: String): Pick {
            val image = ByteArray(FIRMWARE_SIZE)
            image[0] = 0xe9.toByte()
            byteArrayOf(0x32, 0x54, 0xcd.toByte(), 0xab.toByte()).copyInto(image, 32)
            version.encodeToByteArray().copyInto(image, 48)
            "teetotum-firmware".encodeToByteArray().copyInto(image, 80)
            val signed = image + ByteArray(FirmwareService.SIGNATURE)
            return Pick(FIRMWARE_FILE, signed.size.toLong(), null) {
                var at = 0
                object : FileSource {
                    override fun read(buffer: ByteArray, count: Int): Int {
                        if (at == signed.size) return -1
                        val n = minOf(count, signed.size - at)
                        signed.copyInto(buffer, 0, at, at + n)
                        at += n
                        return n
                    }

                    override fun close() = Unit
                }
            }
        }
    }
}
