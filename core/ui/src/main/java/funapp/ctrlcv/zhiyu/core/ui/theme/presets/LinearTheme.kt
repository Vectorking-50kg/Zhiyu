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

val LinearThemePreset by lazy {
    PresetTheme(
        id = "linear",
        displayName = "薰夜",
        standardLight = linearLightScheme,
        standardDark = linearDarkScheme,
        typography = linearTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.3f,
            cardPadding = 24.dp,
            progressBarHeight = 6.dp,
            progressBarCornerRadius = 3.dp,
            buttonCornerRadius = 8.dp,
            sectionTitleWeight = 500,
        ),
    )
}

private val linearLightScheme = lightColorScheme(
    primary = Color(0xFF5E6AD2),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD8DAF0),
    onPrimaryContainer = Color(0xFF2A2E68),
    secondary = Color(0xFF7A7FAD),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8DAE8),
    onSecondaryContainer = Color(0xFF3A3C58),
    tertiary = Color(0xFF27A644),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E8C8),
    onTertiaryContainer = Color(0xFF0A3A14),
    error = Color(0xFFD04848),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD8D8),
    onErrorContainer = Color(0xFF5A0A0A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    surfaceVariant = Color(0xFFF5F6F6),
    onSurfaceVariant = Color(0xFF62666D),
    outline = Color(0xFF8A8F98),
    outlineVariant = Color(0xFFE0E2E4),
    surfaceDim = Color(0xFFE8EAEC),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F6F6),
    surfaceContainer = Color(0xFFF0F1F2),
    surfaceContainerHigh = Color(0xFFE8EAEC),
    surfaceContainerHighest = Color(0xFFE0E2E4),
    inverseSurface = Color(0xFF0F1011),
    inverseOnSurface = Color(0xFFF7F8F8),
    inversePrimary = Color(0xFF828FFF),
)

private val linearDarkScheme = darkColorScheme(
    primary = Color(0xFF828FFF),
    onPrimary = Color(0xFF0A0C28),
    primaryContainer = Color(0xFF2A2E68),
    onPrimaryContainer = Color(0xFFC8CCF0),
    secondary = Color(0xFF7A7FAD),
    onSecondary = Color(0xFF1A1C38),
    secondaryContainer = Color(0xFF34343A),
    onSecondaryContainer = Color(0xFFB8BCD0),
    tertiary = Color(0xFF59D480),
    onTertiary = Color(0xFF0A2A14),
    tertiaryContainer = Color(0xFF0A3A14),
    onTertiaryContainer = Color(0xFFC0E8C8),
    error = Color(0xFFEF7070),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFDD8D8),
    background = Color(0xFF010102),
    onBackground = Color(0xFFF7F8F8),
    surface = Color(0xFF010102),
    onSurface = Color(0xFFF7F8F8),
    surfaceVariant = Color(0xFF18191A),
    onSurfaceVariant = Color(0xFF8A8F98),
    outline = Color(0xFF62666D),
    outlineVariant = Color(0xFF23252A),
    surfaceDim = Color(0xFF010102),
    surfaceBright = Color(0xFF191A1B),
    surfaceContainerLowest = Color(0xFF010102),
    surfaceContainerLow = Color(0xFF0F1011),
    surfaceContainer = Color(0xFF141516),
    surfaceContainerHigh = Color(0xFF18191A),
    surfaceContainerHighest = Color(0xFF23252A),
    inverseSurface = Color(0xFFF7F8F8),
    inverseOnSurface = Color(0xFF0F1011),
    inversePrimary = Color(0xFF5E6AD2),
)

private val linearTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.8).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.4).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.05).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.05).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
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
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.4.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
)
