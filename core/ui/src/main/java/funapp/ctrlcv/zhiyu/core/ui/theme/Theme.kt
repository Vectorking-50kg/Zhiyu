package funapp.ctrlcv.zhiyu.core.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.storage.AppearancePreferences
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ZhiyuTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) { AppearancePreferences(context.applicationContext) }
    val appearance by preferences.changes.collectAsStateWithLifecycle(preferences.read())

    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (appearance.colorMode) {
        ColorMode.SYSTEM -> systemDark
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }

    val palette = rememberAppearancePalette(appearance, darkTheme).animated()
    val colorScheme = palette.materialColors(darkTheme)
    val baseDensity = LocalDensity.current
    val density = remember(baseDensity, appearance.scale) { Density(baseDensity.density * appearance.scale, baseDensity.fontScale) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            context.activity()?.window?.let { window -> WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            } }
        }
    }

    CompositionLocalProvider(
        LocalMonitorPalette provides palette,
        LocalAppearanceSettings provides appearance,
        LocalAppearancePreferences provides preferences,
        LocalMonitorStyle provides if (appearance.uiStyle == UiStyle.MIUIX) MiuixMonitorStyle else MaterialMonitorStyle,
        LocalBaseDensity provides baseDensity,
        LocalDensity provides density,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = MonitorTypography,
            content = {
                val materialIndication = LocalIndication.current
                MiuixTheme(colors = palette.miuixColors(darkTheme)) {
                    CompositionLocalProvider(LocalIndication provides
                        if (appearance.uiStyle == UiStyle.MIUIX) LocalIndication.current else materialIndication,
                    ) { content() }
                }
            }
        )
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}
