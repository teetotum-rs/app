package io.github.teetotum_rs.app

import org.jetbrains.compose.resources.StringResource

/** Talks to the Knob in Bluetooth range. */
interface Bluetooth {
    /** Finds the Knob, reads its status and lets it go again; throws [BluetoothFailed] when it cannot. */
    suspend fun status(): KnobStatus

    /**
     * Sends a plugin [module] with its slot [header] to the Knob while its Settings > Receive is open;
     * [progress] hears the module bytes sent. Throws [BluetoothFailed] when it cannot.
     */
    suspend fun sendPlugin(module: ByteArray, header: ByteArray, progress: (sent: Int) -> Unit)

    /**
     * Sends a firmware [image] with its [signature] to the Knob while its Settings > Receive is open; the Knob
     * checks the signature, switches to the image and restarts. [progress] hears the image bytes sent. Throws
     * [BluetoothFailed] when it cannot.
     */
    suspend fun sendFirmware(image: ByteArray, signature: ByteArray, progress: (sent: Int) -> Unit)

    /** The plugins on the Knob, bundled and in its slots; throws [BluetoothFailed] when it cannot. */
    suspend fun plugins(): List<KnobPlugin>

    /**
     * Deletes the plugin in [slot] while the Knob's Settings > Receive is open, after which the Knob
     * restarts. Throws [BluetoothFailed] when it cannot.
     */
    suspend fun deletePlugin(slot: Int)

    /** The Knob's theme, brightness, clicks and orientation; throws [BluetoothFailed] when it cannot. */
    suspend fun knobSettings(): KnobSettings

    /**
     * Writes [settings] to the Knob, which shows and keeps them, pairing first if need be. Throws
     * [BluetoothFailed] when it cannot.
     */
    suspend fun writeKnobSettings(settings: KnobSettings)
}

class BluetoothFailed(override val shown: Message) :
    Exception(shown.toString()),
    Shown {
    constructor(resource: StringResource, vararg args: Any) : this(messageOf(resource, *args))
}

/**
 * What the Knob reports over Bluetooth: seconds since it started, Wi-Fi networks in its last scan,
 * its firmware's version text and its card's size in bytes, zero without a card. Firmware up to
 * 0.3.3 sends neither version nor card size.
 */
data class KnobStatus(
    val uptimeSeconds: Long,
    val networks: Int,
    val version: String? = null,
    val cardBytes: Long? = null,
)

/** The Knob's GATT service that reports its status, and its characteristics. */
object KnobService {
    const val NAME = "TeeToTum"
    const val SERVICE = "aa7154b7-6b8f-4c5b-aa39-a6cd78bad6bb"
    const val UPTIME = "4b22a5ab-422b-4c76-a176-d7be74ec9bb7"
    const val NETWORKS = "4ea309d6-ee6a-4be8-b753-1925723a2e15"
    const val VERSION = "3a298945-67fa-444e-9739-e0698bc95ca9"
    const val CARD_BYTES = "5bd092f3-61c8-4fc5-b755-f19328dd0172"
    const val SETTINGS = "dcec6510-5a6a-41c3-b83f-a638357034ff"
}

/** An unsigned little-endian integer of up to eight bytes, the way the Knob sends numbers. */
fun unsignedOf(bytes: ByteArray): Long = bytes.foldRight(0L) { byte, value -> value shl 8 or (byte.toLong() and 0xff) }

/** The Knob's firmware as a line to show: its version, or what its silence means. */
fun firmwareText(version: String?): Message =
    version?.let(Message::Raw) ?: messageOf(Res.string.status_firmware_unknown)

/**
 * The Knob's card as a line to show, or why there is none: its size in decimal gigabytes, as cards
 * are sold, to three significant digits and cut rather than rounded, as the Knob shows it.
 */
fun cardText(bytes: Long?): Message = when (bytes) {
    null -> messageOf(Res.string.status_card_unknown)

    0L -> messageOf(Res.string.status_no_card)

    else -> (bytes / 1_000_000).let { mb ->
        val shown = when {
            mb < 1_000 -> "0.${mb.toString().padStart(3, '0')} GB"
            mb < 10_000 -> "${mb / 1_000}.${(mb / 10 % 100).toString().padStart(2, '0')} GB"
            mb < 100_000 -> "${mb / 1_000}.${mb / 100 % 10} GB"
            else -> "${mb / 1_000} GB"
        }
        Message.Raw(shown)
    }
}

/** [sent] of [total] in whole percent, rounded down as the Knob shows it. */
fun percentOf(sent: Int, total: Int): Int = (sent.toLong() * 100 / total.coerceAtLeast(1)).toInt()

/** A duration in its two largest units, such as `2 d 5 h`, `3 min 12 s` or `45 s`. */
fun uptimeText(seconds: Long): String {
    val parts = listOf(
        seconds / 86_400 to "d",
        seconds / 3_600 % 24 to "h",
        seconds / 60 % 60 to "min",
        seconds % 60 to "s",
    )
    val first = parts.indexOfFirst { it.first > 0 }.takeIf { it >= 0 } ?: return "0 s"
    return parts.drop(first).take(2).filterIndexed { index, part -> index == 0 || part.first > 0 }
        .joinToString(" ") { "${it.first} ${it.second}" }
}
