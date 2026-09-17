package io.github.teetotum_rs.app

/** Reads what the Knob in Bluetooth range reports about itself. */
interface Bluetooth {
    /** Finds the Knob, reads its status and lets it go again; throws [BluetoothFailed] when it cannot. */
    suspend fun status(): KnobStatus
}

class BluetoothFailed(message: String) : Exception(message)

/**
 * What the Knob reports over Bluetooth: seconds since it started, Wi-Fi networks in its last scan,
 * and its firmware's version text, which firmware up to 0.3.3 does not send.
 */
data class KnobStatus(val uptimeSeconds: Long, val networks: Int, val version: String? = null)

/** The Knob's GATT service that reports its status, and its characteristics. */
object KnobService {
    const val NAME = "TeeToTum"
    const val SERVICE = "aa7154b7-6b8f-4c5b-aa39-a6cd78bad6bb"
    const val UPTIME = "4b22a5ab-422b-4c76-a176-d7be74ec9bb7"
    const val NETWORKS = "4ea309d6-ee6a-4be8-b753-1925723a2e15"
    const val VERSION = "3a298945-67fa-444e-9739-e0698bc95ca9"
}

/** An unsigned little-endian integer of up to eight bytes, the way the Knob sends numbers. */
fun unsignedOf(bytes: ByteArray): Long =
    bytes.foldRight(0L) { byte, value -> value shl 8 or (byte.toLong() and 0xff) }

/** The Knob's firmware as a line to show: its version, or what its silence means. */
fun firmwareText(version: String?): String =
    version ?: "Older than 0.3.4, the first firmware to report its version here."

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
