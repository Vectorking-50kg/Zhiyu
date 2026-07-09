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
import funapp.ctrlcv.zhiyu.core.ui.theme.InterDisplayFontFamily
import funapp.ctrlcv.zhiyu.core.ui.theme.InterFontFamily
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetTheme

val CreamThemePreset by lazy {
    PresetTheme(
        id = "cream",
        displayName = "奶油",
        standardLight = creamLightScheme,
        standardDark = creamDarkScheme,
        typography = creamTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 20.dp,
            cardInnerCornerRadius = 5.dp,
            cardBorderWidth = 0.dp,
            cardBorderAlpha = 1f,
            cardBorderHairline = true,
            cardPadding = 18.dp,
            progressBarHeight = 14.dp,
            progressBarCornerRadius = 7.dp,
            useShadowElevation = false,
            buttonCornerRadius = 10.dp,
            sectionTitleWeight = 600,
            progressBarShowTimeSegment = true,
        ),
    )
}

private val creamLightScheme = lightColorScheme(
    primary = Color(0xFFB98D3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF3E4C2),
    onPrimaryContainer = Color(0xFF4A3A18),
    secondary = Color(0xFF6E8F6B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDCE8D8),
    onSecondaryContainer = Color(0xFF20351E),
    tertiary = Color(0xFFB2704A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF3DCCB),
    onTertiaryContainer = Color(0xFF4A2A15),
    error = Color(0xFFD94F4F),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFF7D6D3),
    onErrorContainer = Color(0xFF5A1A1A),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF1F1E1A),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF1F1E1A),
    surfaceVariant = Color(0xFFEDEBE1),
    onSurfaceVariant = Color(0xFF8F8B7E),
    outline = Color(0xFFA6A296),
    outlineVariant = Color(0xFFBBBAB6),
    surfaceDim = Color(0xFFE7E5DB),
    surfaceBright = Color(0xFFF4F3ED),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF4F3ED),
    surfaceContainer = Color(0xFFEEECE2),
    surfaceContainerHigh = Color(0xFFE7E5DB),
    surfaceContainerHighest = Color(0xFFDFDDD1),
    inverseSurface = Color(0xFF2A2822),
    inverseOnSurface = Color(0xFFFAF9F5),
    inversePrimary = Color(0xFFE3B876),
)

private val creamDarkScheme = darkColorScheme(
    primary = Color(0xFFE3B876),
    onPrimary = Color(0xFF3A2A0A),
    primaryContainer = Color(0xFF4A3A18),
    onPrimaryContainer = Color(0xFFF3E4C2),
    secondary = Color(0xFF9DBD98),
    onSecondary = Color(0xFF16260F),
    secondaryContainer = Color(0xFF2A3D26),
    onSecondaryContainer = Color(0xFFDCE8D8),
    tertiary = Color(0xFFD99B72),
    onTertiary = Color(0xFF3A200E),
    tertiaryContainer = Color(0xFF4A2A15),
    onTertiaryContainer = Color(0xFFF3DCCB),
    error = Color(0xFFEF7070),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFF7D6D3),
    background = Color(0xFF191813),
    onBackground = Color(0xFFF2F0E8),
    surface = Color(0xFF191813),
    onSurface = Color(0xFFF2F0E8),
    surfaceVariant = Color(0xFF2B2820),
    onSurfaceVariant = Color(0xFFA6A296),
    outline = Color(0xFF6E6A5F),
    outlineVariant = Color(0xFF37342B),
    surfaceDim = Color(0xFF191813),
    surfaceBright = Color(0xFF221F1A),
    surfaceContainerLowest = Color(0xFF0F0E0B),
    surfaceContainerLow = Color(0xFF1D1B16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2B2820),
    surfaceContainerHighest = Color(0xFF37342B),
    inverseSurface = Color(0xFFF2F0E8),
    inverseOnSurface = Color(0xFF221F1A),
    inversePrimary = Color(0xFFB98D3E),
)

private val creamTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.3).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterDisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = (16 * 1.55).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
    ),
)
