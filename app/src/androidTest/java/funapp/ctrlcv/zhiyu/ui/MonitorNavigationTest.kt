package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import funapp.ctrlcv.zhiyu.core.domain.model.AppearanceSettings
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.ui.components.rememberGlassBackdrop
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import top.yukonga.miuix.kmp.theme.MiuixTheme

@RunWith(Parameterized::class)
class MonitorNavigationTest(private val style: UiStyle, private val bar: BottomBarStyle) {
    @get:Rule val compose = createComposeRule()

    @Test
    fun tapSwitchesImmediatelyAndReselectsWithoutWaitingForAnotherTap() {
        var page by mutableStateOf(MonitorPage.OVERVIEW)
        var reselections = 0
        compose.setContent {
            val platformConfiguration = LocalViewConfiguration.current
            // A deliberately long double-tap interval makes accidental gesture arbitration visible.
            val configuration = remember(platformConfiguration) {
                object : ViewConfiguration by platformConfiguration {
                    override val doubleTapTimeoutMillis = 3_000L
                }
            }
            CompositionLocalProvider(
                LocalAppearanceSettings provides AppearanceSettings(uiStyle = style, bottomBarStyle = bar),
                LocalViewConfiguration provides configuration,
            ) {
                MaterialTheme {
                    MiuixTheme {
                        Box {
                            MonitorNavigation(page, { page = it }, { reselections++ }, rememberGlassBackdrop())
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false

        listOf("账户" to MonitorPage.ACCOUNTS, "设置" to MonitorPage.SETTINGS, "概览" to MonitorPage.OVERVIEW)
            .forEachIndexed { index, (label, target) ->
                // A semantics performClick would bypass the gesture detector and miss the delay.
                compose.onNodeWithContentDescription(label).performTouchInput { click() }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnIdle { assertEquals("The first tap must switch immediately", target, page) }

                compose.onNodeWithContentDescription(label).performTouchInput { click() }
                compose.mainClock.advanceTimeBy(32)
                compose.runOnIdle { assertEquals("Reselect must also respond to a single tap", index + 1, reselections) }
            }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}/{1}")
        fun configurations(): List<Array<Any>> = UiStyle.entries.flatMap { style ->
            BottomBarStyle.entries.map { bar -> arrayOf<Any>(style, bar) }
        }
    }
}
