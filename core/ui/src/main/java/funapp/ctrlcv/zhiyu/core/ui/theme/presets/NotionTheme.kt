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

val NotionThemePreset by lazy {
    PresetTheme(
        id = "notion",
        displayName = "紫陌",
        standardLight = notionLightScheme,
        standardDark = notionDarkScheme,
        typography = notionTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 0.dp,
            cardPadding = 18.dp,
            buttonCornerRadius = 8.dp,
            sectionTitleWeight = 600,
        ),
    )
}

private val notionLightScheme = lightColorScheme(
    primary = Color(0xFF5645D4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE6E0F5),
    onPrimaryContainer = Color(0xFF2A1A7A),
    secondary = Color(0xFF0075DE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCECFA),
    onSecondaryContainer = Color(0xFF003070),
    tertiary = Color(0xFFDD5B00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE8D4),
    onTertiaryContainer = Color(0xFF793400),
    error = Color(0xFFE03131),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD8D8),
    onErrorContainer = Color(0xFF5A0A0A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1A1A1A),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF37352F),
    surfaceVariant = Color(0xFFF6F5F4),
    onSurfaceVariant = Color(0xFF5D5B54),
    outline = Color(0xFF787671),
    outlineVariant = Color(0xFFE5E3DF),
    surfaceDim = Color(0xFFE5E3DF),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAF9),
    surfaceContainer = Color(0xFFF6F5F4),
    surfaceContainerHigh = Color(0xFFF0EEEC),
    surfaceContainerHighest = Color(0xFFE5E3DF),
    inverseSurface = Color(0xFF0A1530),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFA090F0),
)

private val notionDarkScheme = darkColorScheme(
    primary = Color(0xFFA090F0),
    onPrimary = Color(0xFF1A0A5A),
    primaryContainer = Color(0xFF3A2A99),
    onPrimaryContainer = Color(0xFFD6B6F6),
    secondary = Color(0xFF68B8FF),
    onSecondary = Color(0xFF002850),
    secondaryContainer = Color(0xFF003070),
    onSecondaryContainer = Color(0xFFA0C8FF),
    tertiary = Color(0xFFFF8A40),
    onTertiary = Color(0xFF3A1A00),
    tertiaryContainer = Color(0xFF5A3000),
    onTertiaryContainer = Color(0xFFFFE0C0),
    error = Color(0xFFFF6060),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A0A0A),
    onErrorContainer = Color(0xFFFDD8D8),
    background = Color(0xFF191919),
    onBackground = Color(0xFFE8E6E2),
    surface = Color(0xFF191919),
    onSurface = Color(0xFFE8E6E2),
    surfaceVariant = Color(0xFF2A2A28),
    onSurfaceVariant = Color(0xFFA4A097),
    outline = Color(0xFF787671),
    outlineVariant = Color(0xFF3A3A38),
    surfaceDim = Color(0xFF191919),
    surfaceBright = Color(0xFF3A3A38),
    surfaceContainerLowest = Color(0xFF101010),
    surfaceContainerLow = Color(0xFF202020),
    surfaceContainer = Color(0xFF262624),
    surfaceContainerHigh = Color(0xFF302E2C),
    surfaceContainerHighest = Color(0xFF3A3A38),
    inverseSurface = Color(0xFFF6F5F4),
    inverseOnSurface = Color(0xFF1A1A1A),
    inversePrimary = Color(0xFF5645D4),
)

private val notionTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.SemiBold,
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
        fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Normal,
        lineHeight = 24.8.sp,
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
        fontWeight = FontWeight.SemiBold,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
    ),
)
