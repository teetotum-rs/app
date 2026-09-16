package io.github.teetotum_rs.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class JoinCodeTest {
    @Test
    fun readsTheKnobsCode() {
        assertEquals(
            JoinCode("TeeToTum-A5E6", "k7p2m9x4"),
            JoinCode.parse("WIFI:T:WPA;S:TeeToTum-A5E6;P:k7p2m9x4;;"),
        )
    }

    @Test
    fun unescapesReservedCharacters() {
        assertEquals(
            JoinCode("a;b", """p\:,"q"""),
            JoinCode.parse("""WIFI:T:WPA;S:a\;b;P:p\\\:\,\"q;;"""),
        )
    }

    @Test
    fun takesFieldsInAnyOrder() {
        assertEquals(JoinCode("n", "p"), JoinCode.parse("WIFI:P:p;S:n;T:WPA;;"))
    }

    @Test
    fun refusesOtherCodes() {
        assertNull(JoinCode.parse("https://teetotum-rs.github.io/firmware/"))
        assertNull(JoinCode.parse("WIFI:T:nopass;S:open;;"))
        assertNull(JoinCode.parse("WIFI:T:WPA;S:;P:p;;"))
        assertNull(JoinCode.parse("WIFI:T:WPA;S:n;;"))
    }
}
