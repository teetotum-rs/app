package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertEquals

class BluetoothTest {
    @Test
    fun readsLittleEndian() {
        assertEquals(0x04030201L, unsignedOf(byteArrayOf(1, 2, 3, 4)))
        assertEquals(4_294_967_295L, unsignedOf(byteArrayOf(-1, -1, -1, -1)))
        assertEquals(200L, unsignedOf(byteArrayOf(200.toByte())))
        assertEquals(0L, unsignedOf(byteArrayOf()))
    }

    @Test
    fun showsTheTwoLargestUnits() {
        assertEquals("0 s", uptimeText(0))
        assertEquals("45 s", uptimeText(45))
        assertEquals("3 min 12 s", uptimeText(192))
        assertEquals("1 h", uptimeText(3_600 + 59))
        assertEquals("1 h 1 min", uptimeText(3_660))
        assertEquals("2 d 5 h", uptimeText(2 * 86_400 + 5 * 3_600 + 7))
    }

    @Test
    fun showsTheCardInDecimalGigabytes() {
        assertEquals("0.401 GB", cardText(401_604_608))
        assertEquals("0.040 GB", cardText(40_960_000))
        assertEquals("7.95 GB", cardText(7_958_691_840))
        assertEquals("1.05 GB", cardText(1_059_999_999))
        assertEquals("15.9 GB", cardText(15_931_539_456))
        assertEquals("10.0 GB", cardText(10_099_999_999))
        assertEquals("127 GB", cardText(127_865_454_592))
        assertEquals("1024 GB", cardText(1_024_000_000_000))
        assertEquals("No card in the Knob.", cardText(0))
        assertEquals("Needs firmware 0.3.4 or later.", cardText(null))
    }
}
