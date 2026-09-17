package io.github.teetotum_rs.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// Robolectric needs Java 21 from SDK 35 on; the build compiles for 17.
@Config(sdk = [34])
class PluginsPageTest {
    private val bundled = KnobPlugin("Teetotum", "a die", "0.1.0", "6799220a4230b177", 0, null, true, true)
    private val remote = KnobPlugin(
        name = "HID remote",
        summary = "remote for the phone's player",
        version = "0.0.0",
        id = "41a9ad2d2290788d",
        size = 1381,
        slot = 2,
        bundled = false,
        installed = true,
    )

    private fun ComposeUiTest.page(bluetooth: Bluetooth) = setContent {
        MaterialTheme {
            PluginsPage(bluetooth, access = { it() }, picker = { { } }, load = CatalogueLoad.Tap)
        }
    }

    @Test
    fun listsThePluginsOnTheKnob() = runComposeUiTest {
        page(FakeBluetooth(listOf(bundled, remote)))
        onNodeWithText("Teetotum").assertExists()
        onNodeWithText("HID remote").assertExists()
        onNodeWithText("Version 0.1.0 · 0 B · built in").assertExists()
        // The bundled plugin has no delete button.
        onAllNodesWithText("Delete").assertCountEquals(1)
    }

    @Test
    fun deletesAfterConfirming() = runComposeUiTest {
        val knob = FakeBluetooth(listOf(bundled, remote))
        page(knob)
        onNodeWithText("Delete").performClick()
        onNodeWithText("Delete HID remote?").assertExists()
        assertEquals(emptyList(), knob.deleted)
        onAllNodesWithText("Delete")[1].performClick()
        waitForIdle()
        assertEquals(listOf(2), knob.deleted)
        onNodeWithText("Deleted. The Knob restarts.").assertExists()
        onNodeWithText("HID remote").assertDoesNotExist()
    }

    @Test
    fun cancellingKeepsThePlugin() = runComposeUiTest {
        val knob = FakeBluetooth(listOf(remote))
        page(knob)
        onNodeWithText("Delete").performClick()
        onNodeWithText("Cancel").performClick()
        onNodeWithText("Delete HID remote?").assertDoesNotExist()
        assertEquals(emptyList(), knob.deleted)
    }

    @Test
    fun saysToOpenReceiveWhenTheKnobRefuses() = runComposeUiTest {
        val knob = FakeBluetooth(listOf(remote), receiving = false)
        page(knob)
        onNodeWithText("Delete").performClick()
        onAllNodesWithText("Delete")[1].performClick()
        waitForIdle()
        onNodeWithText("The Knob refused to delete. Open Settings > Receive on the Knob and try again.").assertExists()
        onNodeWithText("HID remote").assertExists()
        assertEquals(emptyList(), knob.deleted)
    }
}
