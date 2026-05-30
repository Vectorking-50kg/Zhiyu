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

val ClaudeThemePreset by lazy {
    PresetTheme(
        id = "claude",
        displayName = "Claude",
        standardLight = claudeLightScheme,
        standardDark = claudeDarkScheme,
        typography = claudeTypography,
        brandConfig = BrandThemeConfig(
            cardCornerRadius = 12.dp,
            cardInnerCornerRadius = 4.dp,
            cardBorderWidth = 0.dp,
            cardPadding = 18.dp,
            progressBarHeight = 8.dp,
            progressBarCornerRadius = 4.dp,
            buttonCornerRadius = 8.dp,
            sectionTitleWeight = 500,
        ),
    )
}

private val claudeLightScheme = lightColorScheme(
    primary = Color(0xFFCC785C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFF5F0E8),
    onPrimaryContainer = Color(0xFF5A3A28),
    secondary = Color(0xFF5DB8A6),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4EFE9),
    onSecondaryContainer = Color(0xFF1A4A40),
    tertiary = Color(0xFFE8A55A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFCECD0),
    onTertiaryContainer = Color(0xFF5A4020),
    error = Color(0xFFC64545),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFDD8D8),
    onErrorContainer = Color(0xFF5A1A1A),
    background = Color(0xFFFAF9F5),
    onBackground = Color(0xFF141413),
    surface = Color(0xFFFAF9F5),
    onSurface = Color(0xFF141413),
    surfaceVariant = Color(0xFFEFE9DE),
    onSurfaceVariant = Color(0xFF6C6A64),
    outline = Color(0xFF8E8B82),
    outlineVariant = Color(0xFFE6DFD8),
    surfaceDim = Color(0xFFE8E0D2),
    surfaceBright = Color(0xFFFAF9F5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF5F0E8),
    surfaceContainer = Color(0xFFEFE9DE),
    surfaceContainerHigh = Color(0xFFE8E0D2),
    surfaceContainerHighest = Color(0xFFE6DFD8),
    inverseSurface = Color(0xFF252320),
    inverseOnSurface = Color(0xFFFAF9F5),
    inversePrimary = Color(0xFFE8A88C),
)

private val claudeDarkScheme = darkColorScheme(
    primary = Color(0xFFE8A88C),
    onPrimary = Color(0xFF3A1A0A),
    primaryContainer = Color(0xFF5A3A28),
    onPrimaryContainer = Color(0xFFF5D8C8),
    secondary = Color(0xFF7DD4C2),
    onSecondary = Color(0xFF0A2A22),
    secondaryContainer = Color(0xFF1A4A40),
    onSecondaryContainer = Color(0xFFD4EFE9),
    tertiary = Color(0xFFF0C87A),
    onTertiary = Color(0xFF3A2A10),
    tertiaryContainer = Color(0xFF5A4020),
    onTertiaryContainer = Color(0xFFFCECD0),
    error = Color(0xFFEF7070),
    onError = Color(0xFF3A0A0A),
    errorContainer = Color(0xFF5A1A1A),
    onErrorContainer = Color(0xFFFDD8D8),
    background = Color(0xFF181715),
    onBackground = Color(0xFFFAF9F5),
    surface = Color(0xFF181715),
    onSurface = Color(0xFFFAF9F5),
    surfaceVariant = Color(0xFF302E28),
    onSurfaceVariant = Color(0xFFA09D96),
    outline = Color(0xFF6C6A64),
    outlineVariant = Color(0xFF3A3832),
    surfaceDim = Color(0xFF181715),
    surfaceBright = Color(0xFF3A3832),
    surfaceContainerLowest = Color(0xFF0F0E0D),
    surfaceContainerLow = Color(0xFF1F1E1B),
    surfaceContainer = Color(0xFF252320),
    surfaceContainerHigh = Color(0xFF302E28),
    surfaceContainerHighest = Color(0xFF3A3832),
    inverseSurface = Color(0xFFFAF9F5),
    inverseOnSurface = Color(0xFF252320),
    inversePrimary = Color(0xFFCC785C),
)

private val claudeTypography = Typography(
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
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = InterFontFamily,
        fontWeight = FontWeight.Medium,
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
    defaultFontFamily = InterFontFamily,
)
