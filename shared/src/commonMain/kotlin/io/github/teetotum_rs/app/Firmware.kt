package io.github.teetotum_rs.app

import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.StringResource

/**
 * The Knob takes a firmware image over its plugin service while its Settings > Receive is open: it
 * writes the image into the partition it is not running from, checks the signature and restarts.
 */
object FirmwareService {
    /** Control: the image's length, little-endian, and its signature follow. */
    const val UPDATE: Byte = 5

    const val SIGNATURE = 64

    /** The bytes an app partition on the Knob holds. */
    const val IMAGE_MAX = 4 * 1024 * 1024

    /** One piece in this many waits for the Knob's reply; the Knob queues as many. */
    const val WINDOW = 8

    /** The control write that starts an update of [length] bytes signed with [signature]. */
    fun update(length: Int, signature: ByteArray): ByteArray =
        byteArrayOf(UPDATE) + ByteArray(4) { (length ushr 8 * it).toByte() } + signature

    /** Why the Knob refused, by its status code; null for a code that is no failure. */
    fun failure(code: Int): StringResource? = when (code) {
        0x82 -> Res.string.firmware_knob_no_update
        0x83 -> Res.string.plugins_knob_piece_missing
        0x84 -> Res.string.firmware_knob_mismatch
        0x85 -> Res.string.plugins_knob_flash_failed
        else -> null
    }
}

class FirmwareInvalid(override val shown: Message) :
    Exception(shown.toString()),
    Shown {
    constructor(resource: StringResource, vararg args: Any) : this(messageOf(resource, *args))
}

/** A signed firmware file: the ESP app [image], its [signature], and the [name] and [version] it gives itself. */
class FirmwareFile(val image: ByteArray, val signature: ByteArray, val name: String, val version: String)

private const val IMAGE_MAGIC = 0xe9.toByte()

/** The app description sits after the image header (24 bytes) and the first segment's header (8 bytes). */
private const val APP_DESC_AT = 32
private const val APP_DESC_MAGIC = 0xabcd5432L
private const val APP_VERSION_AT = APP_DESC_AT + 16
private const val APP_NAME_AT = APP_DESC_AT + 48
private const val APP_TEXT_MAX = 32

/**
 * The firmware in a `.tfw` file, an ESP app image followed by its Ed25519 signature; throws
 * [FirmwareInvalid] for anything the Knob would not take. The signature is not checked here: the
 * Knob does that before it switches.
 */
fun firmwareOf(signed: ByteArray): FirmwareFile {
    if (signed.size > FirmwareService.IMAGE_MAX + FirmwareService.SIGNATURE) {
        throw FirmwareInvalid(Res.string.firmware_error_too_long, FirmwareService.IMAGE_MAX)
    }
    val image = signed.copyOf((signed.size - FirmwareService.SIGNATURE).coerceAtLeast(0))
    if (image.size < APP_NAME_AT + APP_TEXT_MAX ||
        image[0] != IMAGE_MAGIC ||
        unsignedOf(image.copyOfRange(APP_DESC_AT, APP_DESC_AT + 4)) != APP_DESC_MAGIC
    ) {
        throw FirmwareInvalid(Res.string.firmware_error_not_firmware)
    }

    fun text(at: Int): String {
        val field = image.copyOfRange(at, at + APP_TEXT_MAX)
        return field.copyOf(field.indexOf(0).takeIf { it >= 0 } ?: field.size).decodeToString()
    }
    return FirmwareFile(image, signed.copyOfRange(image.size, signed.size), text(APP_NAME_AT), text(APP_VERSION_AT))
}

/** How long the Knob takes from the commit to answering again: it restarts after half a second and boots in two. */
private const val RESTART_WAIT_MS = 3_000L

/** How often the Knob is asked after an update; each ask scans for up to ten seconds. */
private const val RESTART_TRIES = 4

/**
 * The Knob's status once it answers from a start after [committedAt], the moment the update was written; null
 * when it does not within a few tries. A status from before the restart has an uptime longer than that.
 */
suspend fun statusAfterRestart(bluetooth: Bluetooth, committedAt: Long, now: () -> Long = ::nowMillis): KnobStatus? {
    repeat(RESTART_TRIES) {
        delay(RESTART_WAIT_MS)
        val status = try {
            bluetooth.status()
        } catch (_: BluetoothFailed) {
            null
        }
        if (status != null && status.uptimeSeconds * 1000 < now() - committedAt) return status
    }
    return null
}

/** What an update to [sent], the version in the file, came to, by the [status] the Knob answered with after it. */
fun updateResult(sent: String, status: KnobStatus?): Message = when {
    status == null -> messageOf(Res.string.firmware_no_answer)
    status.version == null -> messageOf(Res.string.firmware_runs_unknown)
    status.version == sent -> messageOf(Res.string.firmware_runs, sent)
    else -> messageOf(Res.string.firmware_runs_other, status.version, sent)
}
