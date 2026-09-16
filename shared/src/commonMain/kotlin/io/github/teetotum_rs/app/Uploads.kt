package io.github.teetotum_rs.app

/** A file the user picked to send to the Knob. */
class Pick(
    val name: String,
    val size: Long,
    /** Milliseconds since the epoch, or null where the platform does not know. */
    val modified: Long?,
    val open: () -> FileSource,
)

interface FileSource : AutoCloseable {
    /** Reads up to [count] bytes into [buffer]; -1 at the end. */
    fun read(buffer: ByteArray, count: Int): Int
}

/** Milliseconds since the epoch. */
expect fun nowMillis(): Long

/** A time as the Knob takes it, `YYYYMMDDhhmmss` in local time, held to the years FAT can store. */
expect fun cardStamp(millis: Long): String
