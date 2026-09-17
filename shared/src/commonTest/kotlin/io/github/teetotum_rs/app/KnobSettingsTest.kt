package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class KnobSettingsTest {
    private val red = KnobSettings(theme = 6, brightness = 4, haptics = 4, orientation = 0)

    @Test
    fun readsWhatTheKnobSends() {
        assertEquals(red, KnobSettings.decode(byteArrayOf(1, 6, 4, 4, 0)))
        assertContentEquals(byteArrayOf(1, 6, 4, 4, 0), red.encode())
    }

    @Test
    fun takesTheEndsOfEveryRange() {
        val low = KnobSettings(0, 1, 0, 0)
        val high = KnobSettings(KnobSettings.THEMES.lastIndex, 10, 9, 3)
        assertEquals(low, KnobSettings.decode(low.encode()))
        assertEquals(high, KnobSettings.decode(high.encode()))
    }

    @Test
    fun refusesAStepPastItsRange() {
        val bad = listOf(
            red.copy(theme = 11),
            red.copy(brightness = 0),
            red.copy(brightness = 11),
            red.copy(haptics = 10),
            red.copy(orientation = 4),
        )
        for (settings in bad) {
            assertNull(KnobSettings.decode(settings.encode()), settings.toString())
        }
        // A byte above 127 is a large step, not a negative one.
        assertNull(KnobSettings.decode(byteArrayOf(1, -1, 4, 4, 0)))
    }

    @Test
    fun refusesAnotherFormatOrLength() {
        assertNull(KnobSettings.decode(byteArrayOf(2, 6, 4, 4, 0)))
        assertNull(KnobSettings.decode(byteArrayOf(1, 6, 4, 4)))
        assertNull(KnobSettings.decode(byteArrayOf(1, 6, 4, 4, 0, 0)))
    }
}
