package funapp.ctrlcv.zhiyu.core.ui.theme.presets

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import funapp.ctrlcv.zhiyu.core.ui.theme.BrandThemeConfig
import funapp.ctrlcv.zhiyu.core.ui.theme.InterFontFamily
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetTheme

val CursorThemePreset by lazy {
    PresetTheme(
        id = "cursor",
        displayName = "焰橙",
        standardLight = cursorLightScheme,
        standardDark = cursorDarkScheme,
        typography = cursorTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.5f,
            cardPadding = 24.dp,
            progressBarHeight = 8.dp,
            progressBarCornerRadius = 4.dp,
            buttonCornerRadius = 8.dp,
            sectionTitleWeight = 600,
        ),
    )
}

private val cursorLightScheme = lightColorScheme(
    primary = Color(0xFFF54E00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFDE0CC),
    onPrimaryContainer = Color(0xFF5A2200),
    secondary = Color(0xFF5A5852),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE6E5E0),
    onSecondaryContainer = Color(0xFF26251E),
    tertiary = Color(0xFF1F8A65),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC8E8D8),
    onTertiaryContainer = Color(0xFF0A3A28),
    error = Color(0xFFCF2D56),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD4DC),
    onErrorContainer = Color(0xFF5A0A1A),
    background = Color(0xFFF7F7F4),
    onBackground = Color(0xFF26251E),
    surface = Color(0xFFF7F7F4),
    onSurface = Color(0xFF26251E),
    surfaceVariant = Color(0xFFEFEEE8),
    onSurfaceVariant = Color(0xFF5A5852),
    outline = Color(0xFF807D72),
    outlineVariant = Color(0xFFE6E5E0),
    surfaceDim = Color(0xFFE6E5E0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF7),
    surfaceContainer = Color(0xFFF2F1EC),
    surfaceContainerHigh = Color(0xFFEFEEE8),
    surfaceContainerHighest = Color(0xFFE6E5E0),
    inverseSurface = Color(0xFF26251E),
    inverseOnSurface = Color(0xFFF7F7F4),
    inversePrimary = Color(0xFFFFAA78),
)

private val cursorDarkScheme = darkColorScheme(
    primary = Color(0xFFFF8A50),
    onPrimary = Color(0xFF3A1500),
    primaryContainer = Color(0xFF5A2200),
    onPrimaryContainer = Color(0xFFFDE0CC),
    secondary = Color(0xFFA09C92),
    onSecondary = Color(0xFF26251E),
    secondaryContainer = Color(0xFF3A3832),
    onSecondaryContainer = Color(0xFFE6E5E0),
    tertiary = Color(0xFF59D499),
    onTertiary = Color(0xFF0A2A18),
    tertiaryContainer = Color(0xFF0A3A28),
    onTertiaryContainer = Color(0xFFC8E8D8),
    error = Color(0xFFFF6080),
    onError = Color(0xFF3A0510),
    errorContainer = Color(0xFF5A0A1A),
    onErrorContainer = Color(0xFFFDD4DC),
    background = Color(0xFF1A1918),
    onBackground = Color(0xFFF2F1EC),
    surface = Color(0xFF1A1918),
    onSurface = Color(0xFFF2F1EC),
    surfaceVariant = Color(0xFF32302A),
    onSurfaceVariant = Color(0xFFA09C92),
    outline = Color(0xFF5A5852),
    outlineVariant = Color(0xFF32302A),
    surfaceDim = Color(0xFF1A1918),
    surfaceBright = Color(0xFF3A3832),
    surfaceContainerLowest = Color(0xFF111110),
    surfaceContainerLow = Color(0xFF221F1E),
    surfaceContainer = Color(0xFF282624),
    surfaceContainerHigh = Color(0xFF32302A),
    surfaceContainerHighest = Color(0xFF3A3832),
    inverseSurface = Color(0xFFF2F1EC),
    inverseOnSurface = Color(0xFF26251E),
    inversePrimary = Color(0xFFF54E00),
)

private val cursorTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.72).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.325).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.11).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.88.sp,
    ),
)
