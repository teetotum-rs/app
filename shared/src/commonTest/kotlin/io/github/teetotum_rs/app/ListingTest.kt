package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListingTest {
    @Test
    fun decodesAFolder() {
        val listing = Listing.decode(
            """{"version":"v0.3.3 e11dd8a","path":"/MUSIC/","card_bytes":31902400512,"entries":[""" +
                """{"name":"雨.mp3","directory":false,"size":4096,"created":"2026-09-16T12:00:00",""" +
                """"modified":null,"accessed":"2026-09-16","attributes":"A"},""" +
                """{"name":"Covers","directory":true,"size":0,"created":null,"modified":null,""" +
                """"accessed":null,"attributes":""}]}""",
        )
        assertEquals("/MUSIC/", listing.path)
        assertEquals(31902400512, listing.cardBytes)
        assertEquals(2, listing.entries.size)
        assertEquals("雨.mp3", listing.entries[0].name)
        assertNull(listing.entries[0].modified)
        assertTrue(listing.entries[1].directory)
    }

    @Test
    fun anOpenObjectIsAnError() {
        assertFailsWith<Exception> {
            Listing.decode("""{"version":"v0.3.3 x","path":"/","card_bytes":null,"entries":[""")
        }
    }

    @Test
    fun readsReleasesAndSkipsDevelopmentBuilds() {
        assertEquals(FirmwareVersion(0, 3, 3), FirmwareVersion.parse("v0.3.3 e11dd8a"))
        assertNull(FirmwareVersion.parse("canary e11dd8a"))
        assertTrue(FirmwareVersion.parse("v0.3.2 e63b255")!! < OLDEST_FIRMWARE)
        assertTrue(FirmwareVersion.parse("v0.10.0 abc")!! > OLDEST_FIRMWARE)
    }
}

class SizeTextTest {
    @Test
    fun picksTheUnit() {
        assertEquals("0 B", sizeText(0))
        assertEquals("1023 B", sizeText(1023))
        assertEquals("1.0 KiB", sizeText(1024))
        assertEquals("4.5 MiB", sizeText(4_718_592))
        assertEquals("29.7 GiB", sizeText(31_902_400_512))
    }
}

class ParentTest {
    @Test
    fun goesUpOneFolder() {
        assertEquals("/", parentOf("/"))
        assertEquals("/", parentOf("/MUSIC/"))
        assertEquals("/MUSIC/", parentOf("/MUSIC/雨/"))
    }
}
