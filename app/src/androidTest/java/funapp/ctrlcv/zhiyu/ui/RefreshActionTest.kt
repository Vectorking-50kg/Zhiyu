package funapp.ctrlcv.zhiyu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshActionTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun refreshIconRotatesOnlyUntilTheRefreshCompletes() {
        var refreshing by mutableStateOf(false)
        var requests = 0
        compose.setContent {
            MaterialTheme {
                RefreshAction(refreshing) { requests++; refreshing = true }
            }
        }
        compose.mainClock.autoAdvance = false
        val button = compose.onNodeWithContentDescription("刷新所有账户")
        fun pixels(): IntArray {
            val bitmap = button.captureToImage().asAndroidBitmap()
            return IntArray(bitmap.width * bitmap.height).also {
                bitmap.getPixels(it, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            }
        }
        val idle = pixels()
        button.performTouchInput { click() }
        compose.mainClock.advanceTimeBy(32)
        button.assertIsNotEnabled()
        compose.mainClock.advanceTimeBy(500) // Compare rotation after the initial press ripple ends.
        val firstFrame = pixels()
        compose.mainClock.advanceTimeBy(200)
        assertFalse("The refresh glyph must visibly rotate", firstFrame.contentEquals(pixels()))
        button.performTouchInput { click() }
        compose.runOnIdle { assertEquals("Refreshing must not start a duplicate request", 1, requests) }

        compose.runOnIdle { refreshing = false }
        compose.mainClock.advanceTimeBy(800) // Allow the press ripple to finish as well.
        button.assertIsEnabled()
        val stopped = pixels()
        assertTrue("The idle icon must return to its original orientation", idle.contentEquals(stopped))
        compose.mainClock.advanceTimeBy(120)
        assertTrue("No rotation should continue after completion", stopped.contentEquals(pixels()))
    }
}
