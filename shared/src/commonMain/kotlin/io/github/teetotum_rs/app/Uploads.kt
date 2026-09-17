package io.github.teetotum_rs.app

import kotlinx.io.IOException
import org.jetbrains.compose.resources.StringResource

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

/** [millis] as the phone's local date and time to show, `2026-09-17 12:30:05`. */
expect fun dateTimeText(millis: Long): String

/** Files another app shared with this one; [skipped] counts those that could not be sent. */
class Shared(val picks: List<Pick>, val skipped: Int)

/** A file on the phone that could not be read or written, with the line the user sees. */
class FileFailed(override val shown: Message) :
    IOException(shown.toString()),
    Shown {
    constructor(resource: StringResource, vararg args: Any) : this(messageOf(resource, *args))
}
