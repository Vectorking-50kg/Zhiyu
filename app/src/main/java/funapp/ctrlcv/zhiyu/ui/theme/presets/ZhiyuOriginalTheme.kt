package funapp.ctrlcv.zhiyu.ui.theme.presets

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import funapp.ctrlcv.zhiyu.ui.theme.PresetTheme

val ZhiyuOriginalThemePreset by lazy {
    PresetTheme(
        id = "zhiyu",
        displayName = "知余",
        standardLight = lightColorScheme(
            background = Color(0xFFFAF7F3),
            surface = Color(0xFFF2EDE8),
            surfaceVariant = Color(0xFFEAE4DC),
            onBackground = Color(0xFF1A1714),
            onSurface = Color(0xFF1A1714),
            onSurfaceVariant = Color(0xFF8A8178),
            outline = Color(0xFFD8D0C6),
            outlineVariant = Color(0xFFE5DDD5),
            primary = Color(0xFF4A6FD4),
            onPrimary = Color.White,
            error = Color(0xFFD94F4F),
            onError = Color.White
        ),
        standardDark = darkColorScheme(
            background = Color(0xFF1A1714),
            surface = Color(0xFF242019),
            surfaceVariant = Color(0xFF332E27),
            onBackground = Color(0xFFF2EDE8),
            onSurface = Color(0xFFF2EDE8),
            onSurfaceVariant = Color(0xFFADA49A),
            outline = Color(0xFF4A4237),
            outlineVariant = Color(0xFF3D352A),
            primary = Color(0xFF7B9EE8),
            onPrimary = Color(0xFF1A1714),
            error = Color(0xFFEF7070),
            onError = Color(0xFF1A1714)
        ),
    )
}
