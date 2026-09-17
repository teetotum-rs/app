package io.github.teetotum_rs.app

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

class StampTest {
    private val zone = TimeZone.getDefault()

    @BeforeTest
    fun inBerlin() = TimeZone.setDefault(TimeZone.getTimeZone("Europe/Berlin"))

    @AfterTest
    fun back() = TimeZone.setDefault(zone)

    @Test
    fun writesLocalTime() {
        // 2026-09-16T14:05:09Z is 16:05:09 in summer time.
        assertEquals("20260916160509", cardStamp(1789567509000))
    }

    @Test
    fun showsLocalDateAndTime() {
        assertEquals("2026-09-16 16:05:09", dateTimeText(1789567509000))
    }

    @Test
    fun holdsTimesBeforeFat() {
        assertEquals("19800101000000", cardStamp(0))
    }
}
