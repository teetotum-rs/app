package io.github.teetotum_rs.app

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UpdateResultTest {
    private fun status(uptime: Long, version: String? = "v0.3.4 1a2b3c4") =
        KnobStatus(uptimeSeconds = uptime, networks = 0, version = version)

    /** A Knob that answers the status asks in turn from [answers]; null stands for one out of range. */
    private class Answering(vararg answers: KnobStatus?) : Bluetooth by FakeBluetooth() {
        val left = ArrayDeque(answers.toList())
        var asked = 0

        override suspend fun status(): KnobStatus {
            asked++
            return left.removeFirstOrNull() ?: throw BluetoothFailed(Res.string.bluetooth_error_out_of_range)
        }
    }

    @Test
    fun skipsTheAnswerFromBeforeTheRestart() = runTest {
        val knob = Answering(status(uptime = 500, version = "v0.3.3 c01fa92"), null, status(uptime = 2))
        val status = statusAfterRestart(knob, committedAt = testScheduler.currentTime) { testScheduler.currentTime }
        assertEquals(status(uptime = 2), status)
        assertEquals(3, knob.asked)
    }

    @Test
    fun givesUpOnAKnobThatDoesNotAnswer() = runTest {
        val knob = Answering()
        assertNull(statusAfterRestart(knob, committedAt = testScheduler.currentTime) { testScheduler.currentTime })
        assertEquals(4, knob.asked)
    }

    @Test
    fun comparesTheVersionWithTheFile() {
        assertEquals(messageOf(Res.string.firmware_runs, "v0.3.4 1a2b3c4"), updateResult("v0.3.4 1a2b3c4", status(2)))
        assertEquals(
            messageOf(Res.string.firmware_runs_other, "v0.3.4 1a2b3c4", "v0.3.5 5d6e7f8"),
            updateResult("v0.3.5 5d6e7f8", status(2)),
        )
        assertEquals(messageOf(Res.string.firmware_runs_unknown), updateResult("v0.3.4 1a2b3c4", status(2, null)))
        assertEquals(messageOf(Res.string.firmware_no_answer), updateResult("v0.3.4 1a2b3c4", null))
    }

    @Test
    fun warnsBeforeTheSameOrAnOlderRelease() {
        val running = "v0.4.0 1a2b3c4"
        assertEquals(messageOf(Res.string.firmware_warn_same, running), sendWarning(running, running))
        assertEquals(messageOf(Res.string.firmware_warn_older, running), sendWarning("v0.3.9 5d6e7f8", running))
        assertNull(sendWarning("v0.3.5 5d6e7f8", "v0.3.4 1a2b3c4"))
        assertNull(sendWarning("v0.3.4 5d6e7f8", "v0.3.4 1a2b3c4"))
        assertNull(sendWarning("v0.3.3 5d6e7f8", "canary 1a2b3c4"))
        assertNull(sendWarning("v0.3.3 5d6e7f8", null))
    }
}
