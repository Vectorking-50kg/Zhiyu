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

val RaycastThemePreset by lazy {
    PresetTheme(
        id = "raycast",
        displayName = "Raycast",
        standardLight = raycastLightScheme,
        standardDark = raycastDarkScheme,
        typography = raycastTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 10.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.6f,
            cardPadding = 16.dp,
            progressBarHeight = 8.dp,
            progressBarCornerRadius = 4.dp,
            buttonCornerRadius = 8.dp,
            sectionTitleWeight = 500,
        ),
    )
}

private val raycastLightScheme = lightColorScheme(
    primary = Color(0xFF2A2B2D),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF3A3C40),
    onPrimaryContainer = Color(0xFFCDCDCD),
    secondary = Color(0xFF57C1FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF1A3A50),
    onSecondaryContainer = Color(0xFFA0D8FF),
    tertiary = Color(0xFF59D499),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF1A4030),
    onTertiaryContainer = Color(0xFFA0ECC8),
    error = Color(0xFFFF6161),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF401818),
    onErrorContainer = Color(0xFFFFA8A8),
    background = Color(0xFF0D0D0D),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF0D0D0D),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF18191A),
    onSurfaceVariant = Color(0xFF9C9C9D),
    outline = Color(0xFF434345),
    outlineVariant = Color(0xFF242728),
    surfaceDim = Color(0xFF07080A),
    surfaceBright = Color(0xFF18191A),
    surfaceContainerLowest = Color(0xFF07080A),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF101111),
    surfaceContainerHigh = Color(0xFF18191A),
    surfaceContainerHighest = Color(0xFF242728),
    inverseSurface = Color(0xFFF4F4F6),
    inverseOnSurface = Color(0xFF0D0D0D),
    inversePrimary = Color(0xFF57C1FF),
)

private val raycastDarkScheme = darkColorScheme(
    primary = Color(0xFFFFFFFF),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF242728),
    onPrimaryContainer = Color(0xFFD3D3D4),
    secondary = Color(0xFF57C1FF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF122A3A),
    onSecondaryContainer = Color(0xFFA0D8FF),
    tertiary = Color(0xFF59D499),
    onTertiary = Color(0xFF000000),
    tertiaryContainer = Color(0xFF0A2A1A),
    onTertiaryContainer = Color(0xFFA0ECC8),
    error = Color(0xFFFF6161),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF3A0F0F),
    onErrorContainer = Color(0xFFFFA8A8),
    background = Color(0xFF07080A),
    onBackground = Color(0xFFF4F4F6),
    surface = Color(0xFF07080A),
    onSurface = Color(0xFFF4F4F6),
    surfaceVariant = Color(0xFF141516),
    onSurfaceVariant = Color(0xFF9C9C9D),
    outline = Color(0xFF434345),
    outlineVariant = Color(0xFF242728),
    surfaceDim = Color(0xFF050506),
    surfaceBright = Color(0xFF141516),
    surfaceContainerLowest = Color(0xFF050506),
    surfaceContainerLow = Color(0xFF0D0D0D),
    surfaceContainer = Color(0xFF0F1011),
    surfaceContainerHigh = Color(0xFF141516),
    surfaceContainerHighest = Color(0xFF18191A),
    inverseSurface = Color(0xFFF4F4F6),
    inverseOnSurface = Color(0xFF07080A),
    inversePrimary = Color(0xFF57C1FF),
)

private val raycastTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = 0.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 28.sp,
        letterSpacing = 0.2.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = 0.2.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        letterSpacing = 0.2.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 0.2.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 25.6.sp,
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
        letterSpacing = 0.1.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        letterSpacing = 0.1.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
    ),
    defaultFontFamily = InterFontFamily,
)
