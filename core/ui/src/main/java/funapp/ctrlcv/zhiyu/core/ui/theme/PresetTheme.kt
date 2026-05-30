package funapp.ctrlcv.zhiyu.core.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.compositionLocalOf
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.AppleThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.ClaudeThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.CursorThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.LinearThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.NotionThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.OpenCodeThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.RaycastThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.StripeThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.VercelThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.ZhiyuOriginalThemePreset

data class PresetTheme(
    val id: String,
    val displayName: String,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
    val typography: Typography = Typography(),
    val brandConfig: BrandThemeConfig = BrandThemeConfig(),
) {
    fun getColorScheme(dark: Boolean): ColorScheme =
        if (dark) standardDark else standardLight
}

val LocalBrandConfig = compositionLocalOf { BrandThemeConfig() }

val PresetThemes by lazy {
    listOf(
        ZhiyuOriginalThemePreset,
        ClaudeThemePreset,
        OpenCodeThemePreset,
        CursorThemePreset,
        RaycastThemePreset,
        VercelThemePreset,
        LinearThemePreset,
        NotionThemePreset,
        StripeThemePreset,
        AppleThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme =
    PresetThemes.find { it.id == id } ?: ZhiyuOriginalThemePreset
