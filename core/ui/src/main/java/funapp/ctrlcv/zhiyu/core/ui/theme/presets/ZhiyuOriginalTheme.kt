package funapp.ctrlcv.zhiyu.core.ui.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetTheme

val ZhiyuOriginalThemePreset by lazy {
    PresetTheme(
        id = "zhiyu",
        displayName = "知余",
        standardLight = lightScheme,
        standardDark = darkScheme,
    )
}

private val lightScheme = lightColorScheme(
    primary = Color(0xFF4A6FD4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDDE3FB),
    onPrimaryContainer = Color(0xFF15326C),
    secondary = Color(0xFF8A7A66),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBDDC9),
    onSecondaryContainer = Color(0xFF463726),
    tertiary = Color(0xFF6C7A4A),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFEAF1C9),
    onTertiaryContainer = Color(0xFF353F1A),
    error = Color(0xFFD94F4F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFAF7F3),
    onBackground = Color(0xFF1A1714),
    surface = Color(0xFFFAF7F3),
    onSurface = Color(0xFF1A1714),
    surfaceVariant = Color(0xFFEAE4DC),
    onSurfaceVariant = Color(0xFF6F665C),
    outline = Color(0xFFC4BBAF),
    outlineVariant = Color(0xFFE5DDD5),
    surfaceDim = Color(0xFFE2DCD2),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF6F2EC),
    surfaceContainer = Color(0xFFF2EDE8),
    surfaceContainerHigh = Color(0xFFECE6DF),
    surfaceContainerHighest = Color(0xFFE5DDD5),
    inverseSurface = Color(0xFF302C28),
    inverseOnSurface = Color(0xFFF5F0EA),
    inversePrimary = Color(0xFFB7C5F1),
)

private val darkScheme = darkColorScheme(
    primary = Color(0xFF7B9EE8),
    onPrimary = Color(0xFF1A1714),
    primaryContainer = Color(0xFF2B438C),
    onPrimaryContainer = Color(0xFFDDE3FB),
    secondary = Color(0xFFCBB89C),
    onSecondary = Color(0xFF332919),
    secondaryContainer = Color(0xFF4A3F2D),
    onSecondaryContainer = Color(0xFFEBDDC9),
    tertiary = Color(0xFFB3C081),
    onTertiary = Color(0xFF1F2A0A),
    tertiaryContainer = Color(0xFF374120),
    onTertiaryContainer = Color(0xFFEAF1C9),
    error = Color(0xFFEF7070),
    onError = Color(0xFF1A1714),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF1A1714),
    onBackground = Color(0xFFF2EDE8),
    surface = Color(0xFF1A1714),
    onSurface = Color(0xFFF2EDE8),
    surfaceVariant = Color(0xFF332E27),
    onSurfaceVariant = Color(0xFFC4BBAF),
    outline = Color(0xFF7A7062),
    outlineVariant = Color(0xFF3D352A),
    surfaceDim = Color(0xFF1A1714),
    surfaceBright = Color(0xFF3D3631),
    surfaceContainerLowest = Color(0xFF110F0C),
    surfaceContainerLow = Color(0xFF211D19),
    surfaceContainer = Color(0xFF252119),
    surfaceContainerHigh = Color(0xFF302A22),
    surfaceContainerHighest = Color(0xFF3B342C),
    inverseSurface = Color(0xFFF2EDE8),
    inverseOnSurface = Color(0xFF302C28),
    inversePrimary = Color(0xFF4A6FD4),
)
