package funapp.ctrlcv.zhiyu.core.ui.theme.presets

import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import funapp.ctrlcv.zhiyu.core.ui.theme.BrandThemeConfig
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetTheme

val AppleThemePreset by lazy {
    PresetTheme(
        id = "apple",
        displayName = "湛蓝",
        standardLight = appleLightScheme,
        standardDark = appleDarkScheme,
        typography = appleTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 18.dp,
            cardInnerCornerRadius = 8.dp,
            cardBorderWidth = 0.dp,
            cardPadding = 18.dp,
            buttonCornerRadius = 9999.dp,
            sectionTitleWeight = 600,
        ),
    )
}

private val appleLightScheme = lightColorScheme(
    primary = Color(0xFF0066CC),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD0E4FF),
    onPrimaryContainer = Color(0xFF003070),
    secondary = Color(0xFF1D1D1F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8E8EA),
    onSecondaryContainer = Color(0xFF1D1D1F),
    tertiary = Color(0xFF0066CC),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFD0E4FF),
    onTertiaryContainer = Color(0xFF003070),
    error = Color(0xFFFF3B30),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFD5D2),
    onErrorContainer = Color(0xFF5A0A0A),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1D1D1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1D1D1F),
    surfaceVariant = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFF333333),
    outline = Color(0xFF7A7A7A),
    outlineVariant = Color(0xFFE0E0E0),
    surfaceDim = Color(0xFFE0E0E0),
    surfaceBright = Color(0xFFFFFFFF),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFAFAFC),
    surfaceContainer = Color(0xFFF5F5F7),
    surfaceContainerHigh = Color(0xFFE8E8EA),
    surfaceContainerHighest = Color(0xFFE0E0E0),
    inverseSurface = Color(0xFF1D1D1F),
    inverseOnSurface = Color(0xFFF5F5F7),
    inversePrimary = Color(0xFF64B5F6),
)

private val appleDarkScheme = darkColorScheme(
    primary = Color(0xFF2997FF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF003070),
    onPrimaryContainer = Color(0xFFA0C8FF),
    secondary = Color(0xFFFFFFFF),
    onSecondary = Color(0xFF000000),
    secondaryContainer = Color(0xFF2A2A2C),
    onSecondaryContainer = Color(0xFFCCCCCC),
    tertiary = Color(0xFF2997FF),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFF003070),
    onTertiaryContainer = Color(0xFFA0C8FF),
    error = Color(0xFFFF453A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFF500A0A),
    onErrorContainer = Color(0xFFFFD5D2),
    background = Color(0xFF000000),
    onBackground = Color(0xFFFFFFFF),
    surface = Color(0xFF000000),
    onSurface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFF272729),
    onSurfaceVariant = Color(0xFFCCCCCC),
    outline = Color(0xFF555558),
    outlineVariant = Color(0xFF2A2A2C),
    surfaceDim = Color(0xFF000000),
    surfaceBright = Color(0xFF272729),
    surfaceContainerLowest = Color(0xFF000000),
    surfaceContainerLow = Color(0xFF1A1A1C),
    surfaceContainer = Color(0xFF252527),
    surfaceContainerHigh = Color(0xFF272729),
    surfaceContainerHighest = Color(0xFF2A2A2C),
    inverseSurface = Color(0xFFF5F5F7),
    inverseOnSurface = Color(0xFF1D1D1F),
    inversePrimary = Color(0xFF0066CC),
)

private val appleTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 34.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.374).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    displaySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 21.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.231.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.374).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.374).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.224).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 17.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.374).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.224).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.12).sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.224).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.12).sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontSize = 12.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = (-0.12).sp,
    ),
)
