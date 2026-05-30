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

val VercelThemePreset by lazy {
    PresetTheme(
        id = "vercel",
        displayName = "Vercel",
        standardLight = vercelLightScheme,
        standardDark = vercelDarkScheme,
        typography = vercelTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 6.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.08f,
            cardPadding = 16.dp,
            progressBarHeight = 6.dp,
            progressBarCornerRadius = 3.dp,
            buttonCornerRadius = 6.dp,
            sectionTitleWeight = 600,
        ),
    )
}

private val vercelLightScheme = lightColorScheme(
    primary = Color(0xFF171717),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE0E0E0),
    onPrimaryContainer = Color(0xFF171717),
    secondary = Color(0xFF0070F3),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E5FF),
    onSecondaryContainer = Color(0xFF003070),
    tertiary = Color(0xFF7928CA),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD8CCF1),
    onTertiaryContainer = Color(0xFF4C2889),
    error = Color(0xFFEE0000),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7D4D6),
    onErrorContainer = Color(0xFFC50000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF171717),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171717),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF4D4D4D),
    outline = Color(0xFF888888),
    outlineVariant = Color(0xFFEBEBEB),
    surfaceDim = Color(0xFFEBEBEB),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFA),
    surfaceContainer = Color(0xFFF5F5F5),
    surfaceContainerHigh = Color(0xFFEBEBEB),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF171717),
    inverseOnSurface = Color(0xFFF2F2F2),
    inversePrimary = Color(0xFF888888),
)

private val vercelDarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF333333),
    onPrimaryContainer = Color(0xFFF2F2F2),
    secondary = Color(0xFF3291FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF0A2A50),
    onSecondaryContainer = Color(0xFFA0C8FF),
    tertiary = Color(0xFFA855F7),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF3A1870),
    onTertiaryContainer = Color(0xFFD8CCF1),
    error = Color(0xFFFF4444),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF500000),
    onErrorContainer = Color(0xFFF7D4D6),
    background = Color(0xFF000000),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF1A1A1A),
    onSurfaceVariant = Color(0xFF888888),
    outline = Color(0xFF4D4D4D),
    outlineVariant = Color(0xFF2A2A2A),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF1A1A1A),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF0A0A0A),
    surfaceContainer = Color(0xFF111111),
    surfaceContainerHigh = Color(0xFF1A1A1A),
    surfaceContainerHighest = Color(0xFF2A2A2A),
    inverseSurface = Color(0xFFEDEDED),
    inverseOnSurface = Color(0xFF171717),
    inversePrimary = Color(0xFF171717),
)

private val vercelTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 32.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-1.28).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 24.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.96).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.6).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 18.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = (-0.28).sp,
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
        letterSpacing = (-0.28).sp,
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
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
    ),
)
