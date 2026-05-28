package funapp.ctrlcv.zhiyu.core.ui.theme

import androidx.compose.material3.ColorScheme
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.AutumnThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.BlackThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.OceanThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.SakuraThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.SpringThemePreset
import funapp.ctrlcv.zhiyu.core.ui.theme.presets.ZhiyuOriginalThemePreset

data class PresetTheme(
    val id: String,
    val displayName: String,
    val standardLight: ColorScheme,
    val standardDark: ColorScheme,
) {
    fun getColorScheme(dark: Boolean): ColorScheme =
        if (dark) standardDark else standardLight
}

val PresetThemes by lazy {
    listOf(
        ZhiyuOriginalThemePreset,
        SakuraThemePreset,
        OceanThemePreset,
        SpringThemePreset,
        AutumnThemePreset,
        BlackThemePreset,
    )
}

fun findPresetTheme(id: String): PresetTheme =
    PresetThemes.find { it.id == id } ?: ZhiyuOriginalThemePreset
