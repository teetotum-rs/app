package io.github.teetotum_rs.app

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

actual fun nowMillis(): Long = System.currentTimeMillis()

actual fun cardStamp(millis: Long): String {
    val time = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    val held = time.coerceIn(FIRST, LAST)
    return held.format(FORMAT)
}

actual fun dateTimeText(millis: Long): String =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault()).format(SHOWN)

private val FIRST = LocalDateTime.of(1980, 1, 1, 0, 0, 0)
private val LAST = LocalDateTime.of(2107, 12, 31, 23, 59, 59)
private val FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
private val SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
