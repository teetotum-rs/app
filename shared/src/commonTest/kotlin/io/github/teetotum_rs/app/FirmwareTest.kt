package io.github.teetotum_rs.app

import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FirmwareTest {
    /** The first 128 bytes of the Knob's firmware 0.3.3 as `espflash save-image` writes it. */
    private val head = Base64.decode(
        "6QUCIICKN0DuAAAACQAAAABjAAAAAAABIAAAPNRIBQAyVM2rAAAAAAAAAAAAAAAAMC4zLjMAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" +
            "AAB0ZWV0b3R1bS1maXJtd2FyZQAAAAAAAAAAAAAAAAAAADIzOjEwOjA3AAAAAAAAAAA=",
    )

    /** What `teetotum-pack firmware` appended to that image. */
    private val signature = Base64.decode(
        "/Rfz72aE+R0+6FFxD9QrrxS7JSmp3yUTpu9RUMy85XHgbTXUmmh2myH2kh3WxvPBZiGwTWwERhGR0thuDTdODQ==",
    )

    @Test
    fun readsNameAndVersionAndSplitsOffTheSignature() {
        val file = firmwareOf(head + signature)
        assertEquals("teetotum-firmware", file.name)
        assertEquals("0.3.3", file.version)
        assertContentEquals(head, file.image)
        assertContentEquals(signature, file.signature)
    }

    @Test
    fun refusesWhatTheKnobWouldNot() {
        assertFailsWith<FirmwareInvalid> { firmwareOf(signature) }
        assertFailsWith<FirmwareInvalid> { firmwareOf(head) }
        assertFailsWith<FirmwareInvalid> { firmwareOf(byteArrayOf(0) + head.copyOfRange(1, head.size) + signature) }
        assertFailsWith<FirmwareInvalid> { firmwareOf(ByteArray(FirmwareService.IMAGE_MAX + 65)) }
    }

    @Test
    fun countsPercentLikeTheKnob() {
        assertEquals(0, percentOf(0, 2_113_264))
        assertEquals(33, percentOf(1, 3))
        assertEquals(99, percentOf(2_113_263, 2_113_264))
        assertEquals(100, percentOf(2_113_264, 2_113_264))
        assertEquals(0, percentOf(0, 0))
    }

    @Test
    fun announcesLengthAndSignature() {
        val update = FirmwareService.update(2_113_264, signature)
        assertEquals(1 + 4 + 64, update.size)
        assertEquals(FirmwareService.UPDATE, update[0])
        assertContentEquals(byteArrayOf(0xf0.toByte(), 0x3e, 0x20, 0), update.copyOfRange(1, 5))
        assertContentEquals(signature, update.copyOfRange(5, update.size))
    }
}
