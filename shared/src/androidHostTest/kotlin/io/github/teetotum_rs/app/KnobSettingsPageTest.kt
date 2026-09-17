package io.github.teetotum_rs.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
class KnobSettingsPageTest {
    private fun ComposeUiTest.page(bluetooth: Bluetooth) = setContent {
        MaterialTheme { KnobSettingsPage(bluetooth, access = { it() }) }
    }

    @Test
    fun showsWhatTheKnobHolds() = runComposeUiTest {
        page(FakeBluetooth())
        onNodeWithText("Red").assertIsSelected()
        onNodeWithText("0°").assertIsSelected()
        onNodeWithText("40 %").assertExists()
    }

    @Test
    fun writesAChangeAtOnce() = runComposeUiTest {
        val knob = FakeBluetooth()
        page(knob)
        onNodeWithText("Cyan").performClick()
        waitForIdle()
        assertEquals(listOf(knob.settings), knob.written)
        assertEquals(8, knob.settings.theme)
        onNodeWithText("Cyan").assertIsSelected()
        // Below the first screen of the page.
        onNodeWithText("90°").performScrollTo().performClick()
        waitForIdle()
        assertEquals(KnobSettings(theme = 8, brightness = 4, haptics = 4, orientation = 1), knob.written.last())
    }

    @Test
    fun takesAFailedChangeBack() = runComposeUiTest {
        val knob = object : Bluetooth by FakeBluetooth() {
            override suspend fun writeKnobSettings(settings: KnobSettings) =
                throw BluetoothFailed(Res.string.bluetooth_error_refused_settings)
        }
        page(knob)
        onNodeWithText("Cyan").performClick()
        waitForIdle()
        onNodeWithText("The Knob refused the settings.").assertExists()
        onNodeWithText("Red").assertIsSelected()
    }
}
