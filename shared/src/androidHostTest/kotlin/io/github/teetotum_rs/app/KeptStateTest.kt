package io.github.teetotum_rs.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.isDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** What the app keeps between runs besides the settings: the menu's folded groups and where each page was left. */
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
// A short phone, on which the menu is taller than the screen; Robolectric needs Java 21 from SDK 35 on.
@Config(sdk = [34], qualifiers = "w360dp-h480dp")
class KeptStateTest {
    private var preferences by mutableStateOf(Preferences())

    /** Each increment starts the app anew, as after closing it, with the preferences it kept. */
    private var run by mutableIntStateOf(0)

    private fun ComposeUiTest.app(start: Preferences) {
        preferences = start
        setContent {
            key(run) {
                App(
                    radio = object : Radio {
                        override suspend fun join(code: JoinCode) = Unit

                        override fun leave() = Unit
                    },
                    downloads = object : Downloads {
                        override fun create(name: String) = error("no downloads here")
                    },
                    bluetooth = FakeBluetooth(),
                    bluetoothAccess = { it() },
                    picker = { { } },
                    back = { _, _ -> },
                    preferences = preferences,
                    onPreferences = { preferences = it },
                ) { }
            }
        }
    }

    private fun ComposeUiTest.restart() {
        run++
        waitForIdle()
    }

    @Test
    fun exitIsInReachOnAShortScreen() = runComposeUiTest {
        app(Preferences())
        onNodeWithContentDescription("Open menu").performClick()
        onNodeWithText("Exit").assertIsNotDisplayed()
        onNodeWithText("Exit").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun keepsTheFoldedGroups() = runComposeUiTest {
        app(Preferences(start = Start.Last, lastPage = Page.About))
        onNodeWithContentDescription("Open menu").performClick()
        onNodeWithText("Card over Wi-Fi").assertExists()
        onNodeWithContentDescription("Hide pages under Home").performClick()
        waitForIdle()
        assertEquals(setOf(Page.Home), preferences.folded)
        restart()
        onNodeWithContentDescription("Open menu").performClick()
        onNodeWithText("Card over Wi-Fi").assertDoesNotExist()
        onNodeWithContentDescription("Show pages under Home").assertExists()
    }

    @Test
    fun opensEachPageWhereItWasLeft() = runComposeUiTest {
        app(Preferences(start = Start.Last, lastPage = Page.Help))
        waitUntil(timeoutMillis = WAIT_MS) { !onNodeWithText(SECTION).isDisplayed() }
        onNodeWithText(SECTION).performScrollTo()
        waitForIdle()
        val kept = assertNotNull(preferences.scroll[Page.Help])
        assertTrue(kept.offset > 0)
        restart()
        waitUntil(timeoutMillis = WAIT_MS) { onNodeWithText(SECTION).isDisplayed() }
    }

    @Test
    fun keepsAPageWhereItWasLeftWhileTheAppRuns() = runComposeUiTest {
        app(Preferences(start = Start.Last, lastPage = Page.Help))
        waitUntil(timeoutMillis = WAIT_MS) { !onNodeWithText(SECTION).isDisplayed() }
        onNodeWithText(SECTION).performScrollTo()
        onNodeWithContentDescription("Open settings").performClick()
        onNodeWithText(SECTION).assertDoesNotExist()
        onNodeWithContentDescription("Open menu").performClick()
        onNodeWithText("Help").performClick()
        waitUntil(timeoutMillis = WAIT_MS) { onNodeWithText(SECTION).isDisplayed() }
    }

    private companion object {
        /**
         * A heading down the help page; above it at first, until the Markdown library has laid out the text off the
         * main thread.
         */
        const val SECTION = "From other apps"

        const val WAIT_MS = 5_000L
    }
}
