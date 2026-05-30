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

val StripeThemePreset by lazy {
    PresetTheme(
        id = "stripe",
        displayName = "轻纱",
        standardLight = stripeLightScheme,
        standardDark = stripeDarkScheme,
        typography = stripeTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.4f,
            cardElevation = 0.dp,
            cardPadding = 24.dp,
            useShadowElevation = true,
            progressBarHeight = 8.dp,
            progressBarCornerRadius = 9999.dp,
            buttonCornerRadius = 9999.dp,
            sectionTitleWeight = 300,
        ),
    )
}

private val stripeLightScheme = lightColorScheme(
    primary = Color(0xFF533AFD),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDD8FF),
    onPrimaryContainer = Color(0xFF2A1A8A),
    secondary = Color(0xFF665EFD),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0DCFF),
    onSecondaryContainer = Color(0xFF2A2070),
    tertiary = Color(0xFFEA2261),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFDD0DC),
    onTertiaryContainer = Color(0xFF5A0A28),
    error = Color(0xFFEA2261),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD0DC),
    onErrorContainer = Color(0xFF5A0A28),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF0D253D),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0D253D),
    surfaceVariant = Color(0xFFF6F9FC),
    onSurfaceVariant = Color(0xFF64748D),
    outline = Color(0xFF64748D),
    outlineVariant = Color(0xFFE3E8EE),
    surfaceDim = Color(0xFFE3E8EE),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F9FC),
    surfaceContainer = Color(0xFFF0F3F8),
    surfaceContainerHigh = Color(0xFFE3E8EE),
    surfaceContainerHighest = Color(0xFFD8DCE4),
    inverseSurface = Color(0xFF1C1E54),
    inverseOnSurface = Color(0xFFFFFFFF),
    inversePrimary = Color(0xFFB9B9F9),
)

private val stripeDarkScheme = darkColorScheme(
    primary = Color(0xFF9A90FF),
    onPrimary = Color(0xFF0A0A3A),
    primaryContainer = Color(0xFF2A1A8A),
    onPrimaryContainer = Color(0xFFD0CCFF),
    secondary = Color(0xFFB9B9F9),
    onSecondary = Color(0xFF1A1850),
    secondaryContainer = Color(0xFF2A2070),
    onSecondaryContainer = Color(0xFFE0DCFF),
    tertiary = Color(0xFFFF6888),
    onTertiary = Color(0xFF3A0A18),
    tertiaryContainer = Color(0xFF5A0A28),
    onTertiaryContainer = Color(0xFFFDD0DC),
    error = Color(0xFFFF6888),
    onError = Color(0xFF3A0A18),
    errorContainer = Color(0xFF5A0A28),
    onErrorContainer = Color(0xFFFDD0DC),
    background = Color(0xFF0D0F2A),
    onBackground = Color(0xFFE8ECF2),
    surface = Color(0xFF0D0F2A),
    onSurface = Color(0xFFE8ECF2),
    surfaceVariant = Color(0xFF1C1E54),
    onSurfaceVariant = Color(0xFF8898B0),
    outline = Color(0xFF5A6A82),
    outlineVariant = Color(0xFF2A2E58),
    surfaceDim = Color(0xFF0D0F2A),
    surfaceBright = Color(0xFF1C1E54),
    surfaceContainerLowest = Color(0xFF08091A),
    surfaceContainerLow = Color(0xFF101240),
    surfaceContainer = Color(0xFF181A50),
    surfaceContainerHigh = Color(0xFF1C1E54),
    surfaceContainerHighest = Color(0xFF2A2E58),
    inverseSurface = Color(0xFFF6F9FC),
    inverseOnSurface = Color(0xFF0D253D),
    inversePrimary = Color(0xFF533AFD),
)

private val stripeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 36.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-1.4).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 26.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.64).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 22.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.22).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 20.sp,
        fontWeight = FontWeight.Light,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Light,
    ),
    titleSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Light,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 16.sp,
        fontWeight = FontWeight.Light,
    ),
    bodyMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 15.sp,
        fontWeight = FontWeight.Light,
    ),
    bodySmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
    ),
    labelMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 13.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.39).sp,
    ),
    labelSmall = TextStyle(
        fontFamily = InterFontFamily,
        fontSize = 11.sp,
        fontWeight = FontWeight.Light,
    ),
)
