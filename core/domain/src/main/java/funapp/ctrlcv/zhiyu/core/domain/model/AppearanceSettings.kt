package funapp.ctrlcv.zhiyu.core.domain.model

enum class UiStyle {
    MATERIAL,
    MIUIX,
}

enum class ThemeColorSource {
    DEFAULT,
    SYSTEM,
    CUSTOM,
}

enum class ThemePalette {
    TONAL_SPOT,
    NEUTRAL,
    VIBRANT,
    EXPRESSIVE,
    RAINBOW,
    FRUIT_SALAD,
    MONOCHROME,
    FIDELITY,
    CONTENT,
}

enum class BottomBarStyle {
    FIXED,
    FLOATING,
    GLASS,
}

data class AppearanceSettings(
    val uiStyle: UiStyle = UiStyle.MATERIAL,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val colorSource: ThemeColorSource = ThemeColorSource.DEFAULT,
    val seedColor: Int = 0xFF4A7696.toInt(),
    val palette: ThemePalette = ThemePalette.TONAL_SPOT,
    val pureBlack: Boolean = false,
    val bottomBarStyle: BottomBarStyle = BottomBarStyle.FLOATING,
    val scale: Float = 1f,
) {
    fun normalized(): AppearanceSettings = copy(
        scale = if (scale.isFinite()) scale.coerceIn(MIN_SCALE, MAX_SCALE) else 1f,
    )

    companion object {
        const val MIN_SCALE = 0.8f
        const val MAX_SCALE = 1.2f
    }
}
