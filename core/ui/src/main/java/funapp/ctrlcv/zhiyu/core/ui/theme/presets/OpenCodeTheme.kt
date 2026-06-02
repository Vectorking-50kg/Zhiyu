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
import funapp.ctrlcv.zhiyu.core.ui.theme.GeistMonoFontFamily
import funapp.ctrlcv.zhiyu.core.ui.theme.InterFontFamily
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetTheme

val OpenCodeThemePreset by lazy {
    PresetTheme(
        id = "opencode",
        displayName = "矩阵",
        standardLight = openCodeLightScheme,
        standardDark = openCodeDarkScheme,
        typography = openCodeTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 8.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 1.dp,
            cardBorderAlpha = 0.4f,
            cardPadding = 16.dp,
            progressBarHeight = 6.dp,
            progressBarCornerRadius = 2.dp,
            buttonCornerRadius = 6.dp,
            sectionTitleWeight = 500,
        ),
    )
}

private val openCodeLightScheme = lightColorScheme(
    primary = Color(0xFF1A8A3E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8F0D4),
    onPrimaryContainer = Color(0xFF0A3A14),
    secondary = Color(0xFF3A7A5A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC0E0D0),
    onSecondaryContainer = Color(0xFF0A2A18),
    tertiary = Color(0xFF0088CC),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC0E0F0),
    onTertiaryContainer = Color(0xFF003050),
    error = Color(0xFFD03030),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD8D8),
    onErrorContainer = Color(0xFF5A0A0A),
    background = Color(0xFFF8FAF8),
    onBackground = Color(0xFF1A2A1E),
    surface = Color(0xFFF8FAF8),
    onSurface = Color(0xFF1A2A1E),
    surfaceVariant = Color(0xFFE8F0E8),
    onSurfaceVariant = Color(0xFF4A5A4E),
    outline = Color(0xFF7A8A7E),
    outlineVariant = Color(0xFFD0DCD0),
    surfaceDim = Color(0xFFD8E4D8),
    surfaceBright = Color(0xFFF8FAF8),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F6F0),
    surfaceContainer = Color(0xFFE8F0E8),
    surfaceContainerHigh = Color(0xFFDCE8DC),
    surfaceContainerHighest = Color(0xFFD0DCD0),
    inverseSurface = Color(0xFF1A2A1E),
    inverseOnSurface = Color(0xFFE8F0E8),
    inversePrimary = Color(0xFF60D880),
)

private val openCodeDarkScheme = darkColorScheme(
    primary = Color(0xFF60D880),
    onPrimary = Color(0xFF0A2A10),
    primaryContainer = Color(0xFF0A3A14),
    onPrimaryContainer = Color(0xFFA0F0B0),
    secondary = Color(0xFF80C0A0),
    onSecondary = Color(0xFF0A1A10),
    secondaryContainer = Color(0xFF1A3A28),
    onSecondaryContainer = Color(0xFFC0E0D0),
    tertiary = Color(0xFF60C0F0),
    onTertiary = Color(0xFF002040),
    tertiaryContainer = Color(0xFF003050),
    onTertiaryContainer = Color(0xFFA0D0F0),
    error = Color(0xFFF06060),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A0A0A),
    onErrorContainer = Color(0xFFFDD8D8),
    background = Color(0xFF0A0E0A),
    onBackground = Color(0xFFD0E8D0),
    surface = Color(0xFF0A0E0A),
    onSurface = Color(0xFFD0E8D0),
    surfaceVariant = Color(0xFF1A2418),
    onSurfaceVariant = Color(0xFF90A890),
    outline = Color(0xFF5A6A5A),
    outlineVariant = Color(0xFF2A3828),
    surfaceDim = Color(0xFF0A0E0A),
    surfaceBright = Color(0xFF1A2418),
    surfaceContainerLowest = Color(0xFF060806),
    surfaceContainerLow = Color(0xFF101810),
    surfaceContainer = Color(0xFF182018),
    surfaceContainerHigh = Color(0xFF1A2A1A),
    surfaceContainerHighest = Color(0xFF2A3828),
    inverseSurface = Color(0xFFD0E8D0),
    inverseOnSurface = Color(0xFF0A0E0A),
    inversePrimary = Color(0xFF1A8A3E),
)

private val openCodeTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 32.sp,
    ),
    displayMedium = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 24.sp,
    ),
    displaySmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
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
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
)
