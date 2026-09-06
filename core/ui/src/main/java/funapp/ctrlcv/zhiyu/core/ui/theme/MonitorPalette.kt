package funapp.ctrlcv.zhiyu.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

@Immutable
data class MonitorPalette(
    val background: Color, val surface: Color, val soft: Color,
    val text: Color, val muted: Color, val subtle: Color, val line: Color,
    val primary: Color, val onPrimary: Color, val toolbar: Color, val indicator: Color,
    val green: Color, val amber: Color, val red: Color,
    val greenSoft: Color, val amberSoft: Color, val redSoft: Color, val track: Color,
    val sheet: Color,
)

val MonitorLight = MonitorPalette(
    Color(0xFFF5F6F7), Color.White, Color(0xFFF0F2F3),
    Color(0xFF212729), Color(0xFF667178), Color(0xFF929AA0), Color(0xFFE9EDEF),
    Color(0xFF253135), Color.White, Color(0xFFEEF0F1), Color(0xFFDCE2E4),
    Color(0xFF4A9D6F), Color(0xFFBB8C21), Color(0xFFCF5555),
    Color(0xFFEDF6EF), Color(0xFFFBF4E6), Color(0xFFFBEEEE), Color(0xFFEDF0F1), Color.White,
)
val MonitorDark = MonitorPalette(
    Color(0xFF101315), Color(0xFF1B1F22), Color(0xFF252B2F),
    Color(0xFFEDF1F2), Color(0xFF9AA5AC), Color(0xFF75828B), Color(0xFF2A3238),
    Color(0xFFD9E7E8), Color(0xFF213137), Color(0xFF282E33), Color(0xFF454E55),
    Color(0xFF65B58B), Color(0xFFD1A548), Color(0xFFE57F7F),
    Color(0xFF1D342A), Color(0xFF382F1F), Color(0xFF392729), Color(0xFF2B3237), Color(0xFF20262A),
)
val LocalMonitorPalette = compositionLocalOf { MonitorLight }

fun MonitorPalette.materialColors(dark: Boolean) = if (dark) darkColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = indicator, onPrimaryContainer = text,
    secondary = primary, onSecondary = onPrimary, secondaryContainer = soft, onSecondaryContainer = text,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = soft, onSurfaceVariant = muted, outline = muted, outlineVariant = line,
    surfaceContainer = background, surfaceContainerHigh = sheet, surfaceContainerHighest = indicator,
    surfaceContainerLow = soft, surfaceBright = surface, error = red, errorContainer = redSoft,
) else lightColorScheme(
    primary = primary, onPrimary = onPrimary, primaryContainer = indicator, onPrimaryContainer = text,
    secondary = primary, onSecondary = onPrimary, secondaryContainer = soft, onSecondaryContainer = text,
    background = background, onBackground = text, surface = surface, onSurface = text,
    surfaceVariant = soft, onSurfaceVariant = muted, outline = muted, outlineVariant = line,
    surfaceContainer = background, surfaceContainerHigh = sheet, surfaceContainerHighest = indicator,
    surfaceContainerLow = soft, surfaceBright = surface, error = red, errorContainer = redSoft,
)

fun monitorTextStyle(size: Int, lineHeight: Int = (size * 1.5).toInt(), weight: Int = 400, tracking: Float = 0f) = TextStyle(
    fontFamily = InterFontFamily, fontSize = size.sp, lineHeight = lineHeight.sp,
    fontWeight = FontWeight(weight), letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
val MonitorTypography = Typography(
    headlineLarge = monitorTextStyle(30, 40, 600, -1f),
    headlineMedium = monitorTextStyle(24, 34, 600, -.6f),
    titleLarge = monitorTextStyle(23, 32, 600, -.5f),
    titleMedium = monitorTextStyle(16, 23, 600, -.35f),
    titleSmall = monitorTextStyle(14, 21, 500),
    bodyLarge = monitorTextStyle(14, 21), bodyMedium = monitorTextStyle(13, 20),
    bodySmall = monitorTextStyle(12, 20), labelLarge = monitorTextStyle(13, 20, 500),
    labelMedium = monitorTextStyle(11, 17), labelSmall = monitorTextStyle(10, 16),
)
