package funapp.ctrlcv.zhiyu.core.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.materialkolor.PaletteStyle
import com.materialkolor.rememberDynamicColorScheme
import funapp.ctrlcv.zhiyu.core.domain.model.AppearanceSettings
import funapp.ctrlcv.zhiyu.core.domain.model.ThemeColorSource
import funapp.ctrlcv.zhiyu.core.domain.model.ThemePalette
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.storage.AppearancePreferences
import top.yukonga.miuix.kmp.theme.Colors
import top.yukonga.miuix.kmp.theme.darkColorScheme as miuixDarkColors
import top.yukonga.miuix.kmp.theme.lightColorScheme as miuixLightColors
import top.yukonga.miuix.kmp.utils.SmoothRoundedCornerShape

val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }
val LocalAppearancePreferences = staticCompositionLocalOf<AppearancePreferences> {
    error("AppearancePreferences must be provided by ZhiyuTheme")
}
val LocalBaseDensity = staticCompositionLocalOf { Density(1f) }

/** Shared layout stays familiar; component shapes, typography and interactions follow the style. */
data class MonitorStyle(
    val pagePadding: Dp = 20.dp,
    val cardShape: Shape = RoundedCornerShape(20.dp),
    val groupShape: Shape = RoundedCornerShape(16.dp),
    val titleSize: Int = 30,
    val fontFamily: FontFamily = FontFamily.Default,
)

val MaterialMonitorStyle = MonitorStyle()
val MiuixMonitorStyle = MonitorStyle(
    pagePadding = 16.dp,
    cardShape = SmoothRoundedCornerShape(26.dp),
    groupShape = SmoothRoundedCornerShape(22.dp),
    titleSize = 32,
    fontFamily = FontFamily.Default,
)
val LocalMonitorStyle = staticCompositionLocalOf { MaterialMonitorStyle }

@Composable
fun rememberAppearancePalette(settings: AppearanceSettings, dark: Boolean): MonitorPalette {
    val base = if (dark) MonitorDark else MonitorLight
    val nativeMiuix = if (dark) miuixDarkColors() else miuixLightColors()
    val palette = when (settings.colorSource) {
        ThemeColorSource.DEFAULT -> if (settings.uiStyle == UiStyle.MATERIAL) base else base.copy(
            background = nativeMiuix.background, surface = nativeMiuix.surface,
            soft = nativeMiuix.secondaryContainer, text = nativeMiuix.onSurface,
            muted = nativeMiuix.onSurfaceVariantSummary, subtle = nativeMiuix.onSurfaceSecondary,
            line = nativeMiuix.dividerLine, primary = nativeMiuix.primary,
            onPrimary = nativeMiuix.onPrimary, toolbar = nativeMiuix.surface,
            indicator = nativeMiuix.tertiaryContainer, track = nativeMiuix.surfaceContainerHighest,
            sheet = nativeMiuix.surface,
        )
        ThemeColorSource.SYSTEM, ThemeColorSource.CUSTOM -> {
            val context = LocalContext.current
            val systemColors = if (settings.colorSource == ThemeColorSource.SYSTEM && Build.VERSION.SDK_INT >= 31) {
                if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            } else null
            val generated = rememberDynamicColorScheme(
                seedColor = systemColors?.primary ?: Color(settings.seedColor),
                isDark = dark, isAmoled = false, style = settings.palette.materialStyle(),
            )
            // The default Monet style uses the actual Android scheme, including system contrast choices.
            val colors = if (systemColors != null && settings.palette == ThemePalette.TONAL_SPOT) systemColors else generated
            base.withMaterialColors(colors)
        }
    }
    return if (dark && settings.pureBlack) palette.copy(
        background = Color.Black, surface = Color(0xFF080808), soft = Color(0xFF141414),
        toolbar = Color(0xFF101010), sheet = Color(0xFF101010), line = Color(0xFF292929),
    ) else palette
}

private fun ThemePalette.materialStyle() = when (this) {
    ThemePalette.TONAL_SPOT -> PaletteStyle.TonalSpot
    ThemePalette.NEUTRAL -> PaletteStyle.Neutral
    ThemePalette.VIBRANT -> PaletteStyle.Vibrant
    ThemePalette.EXPRESSIVE -> PaletteStyle.Expressive
    ThemePalette.RAINBOW -> PaletteStyle.Rainbow
    ThemePalette.FRUIT_SALAD -> PaletteStyle.FruitSalad
    ThemePalette.MONOCHROME -> PaletteStyle.Monochrome
    ThemePalette.FIDELITY -> PaletteStyle.Fidelity
    ThemePalette.CONTENT -> PaletteStyle.Content
}

private fun MonitorPalette.withMaterialColors(scheme: ColorScheme) = copy(
    background = scheme.background, surface = scheme.surfaceContainerLowest,
    soft = scheme.surfaceContainerHigh, text = scheme.onSurface, muted = scheme.onSurfaceVariant,
    subtle = scheme.onSurfaceVariant.copy(alpha = .72f), line = scheme.outlineVariant.copy(alpha = .55f),
    primary = scheme.primary, onPrimary = scheme.onPrimary, toolbar = scheme.surfaceContainer,
    indicator = scheme.secondaryContainer, track = scheme.surfaceContainerHighest,
    sheet = scheme.surfaceContainerLow,
)

/** Miuix is a real second renderer, sharing the same resolved palette as the app's custom cards. */
fun MonitorPalette.miuixColors(dark: Boolean): Colors = (if (dark) miuixDarkColors() else miuixLightColors()).copy(
    primary = primary, onPrimary = onPrimary, primaryVariant = indicator, onPrimaryVariant = text,
    primaryContainer = indicator, onPrimaryContainer = primary,
    disabledPrimary = primary.copy(alpha = .38f), disabledOnPrimary = onPrimary.copy(alpha = .6f),
    disabledPrimaryButton = primary.copy(alpha = .38f), disabledOnPrimaryButton = onPrimary.copy(alpha = .6f),
    disabledPrimarySlider = primary.copy(alpha = .38f), secondary = soft, onSecondary = text,
    secondaryVariant = indicator, onSecondaryVariant = text, secondaryContainer = soft,
    onSecondaryContainer = text, secondaryContainerVariant = indicator, onSecondaryContainerVariant = text,
    background = background, onBackground = text, onBackgroundVariant = muted,
    surface = surface, onSurface = text, surfaceVariant = soft,
    onSurfaceSecondary = muted, onSurfaceVariantSummary = muted, onSurfaceVariantActions = primary,
    disabledOnSurface = subtle, outline = line, dividerLine = line,
    surfaceContainer = toolbar, onSurfaceContainer = text, onSurfaceContainerVariant = muted,
    surfaceContainerHigh = soft, onSurfaceContainerHigh = text,
    surfaceContainerHighest = indicator, onSurfaceContainerHighest = text,
)

@Composable
fun MonitorPalette.animated(): MonitorPalette {
    @Composable fun animated(color: Color) = animateColorAsState(color, tween(220), label = "appearance color").value
    return copy(
        background = animated(background), surface = animated(surface), soft = animated(soft),
        text = animated(text), muted = animated(muted), subtle = animated(subtle), line = animated(line),
        primary = animated(primary), onPrimary = animated(onPrimary), toolbar = animated(toolbar),
        indicator = animated(indicator), green = animated(green), amber = animated(amber), red = animated(red),
        greenSoft = animated(greenSoft), amberSoft = animated(amberSoft), redSoft = animated(redSoft),
        track = animated(track), sheet = animated(sheet),
    )
}
