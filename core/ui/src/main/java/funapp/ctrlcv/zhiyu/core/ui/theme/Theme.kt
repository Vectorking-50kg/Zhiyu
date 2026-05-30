package funapp.ctrlcv.zhiyu.core.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode

val LocalDarkMode = compositionLocalOf { false }

@Composable
fun ZhiyuTheme(
    content: @Composable () -> Unit
) {
    val colorMode by rememberColorMode()
    val themeId by rememberThemeId()

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (colorMode) {
        ColorMode.SYSTEM -> systemDark
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }

    val preset = findPresetTheme(themeId)
    val colorScheme = preset.getColorScheme(darkTheme)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkMode provides darkTheme,
        LocalBrandConfig provides preset.brandConfig,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = preset.typography,
            content = content
        )
    }
}
