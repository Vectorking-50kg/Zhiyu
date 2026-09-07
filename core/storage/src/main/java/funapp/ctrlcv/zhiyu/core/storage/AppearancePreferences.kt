package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.AppearanceSettings
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.ThemeColorSource
import funapp.ctrlcv.zhiyu.core.domain.model.ThemePalette
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

@Singleton
class AppearancePreferences internal constructor(private val prefs: SharedPreferences) {
    @Inject
    constructor(@ApplicationContext context: Context) : this(
        context.getSharedPreferences("zhiyu_theme_prefs", Context.MODE_PRIVATE),
    )

    fun read(): AppearanceSettings {
        // A single snapshot also tolerates values written with a different preference type.
        val stored = prefs.all
        val defaults = AppearanceSettings()
        return AppearanceSettings(
            uiStyle = stored.enumValue(KEY_UI_STYLE, defaults.uiStyle),
            colorMode = stored.enumValue(KEY_COLOR_MODE, defaults.colorMode),
            colorSource = stored.enumValue(KEY_COLOR_SOURCE, defaults.colorSource),
            seedColor = stored[KEY_SEED_COLOR] as? Int ?: defaults.seedColor,
            palette = stored.enumValue(KEY_PALETTE, defaults.palette),
            pureBlack = stored[KEY_PURE_BLACK] as? Boolean ?: defaults.pureBlack,
            bottomBarStyle = stored.enumValue(KEY_BOTTOM_BAR_STYLE, defaults.bottomBarStyle),
            scale = stored[KEY_SCALE] as? Float ?: defaults.scale,
        ).normalized()
    }

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        synchronized(prefs) {
            val value = transform(read()).normalized()
            prefs.edit()
                .putString(KEY_UI_STYLE, value.uiStyle.name)
                .putString(KEY_COLOR_MODE, value.colorMode.name)
                .putString(KEY_COLOR_SOURCE, value.colorSource.name)
                .putInt(KEY_SEED_COLOR, value.seedColor)
                .putString(KEY_PALETTE, value.palette.name)
                .putBoolean(KEY_PURE_BLACK, value.pureBlack)
                .putString(KEY_BOTTOM_BAR_STYLE, value.bottomBarStyle.name)
                .putFloat(KEY_SCALE, value.scale)
                .apply()
        }
    }

    val changes: Flow<AppearanceSettings> = callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            // Android 11+ reports clear() with a null key.
            if (key == null || key in APPEARANCE_KEYS) trySend(read())
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        trySend(read())
        awaitClose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }.buffer(Channel.CONFLATED).distinctUntilChanged()

    private companion object {
        const val KEY_UI_STYLE = "appearance_ui_style"
        const val KEY_COLOR_MODE = "colorMode"
        const val KEY_COLOR_SOURCE = "appearance_color_source"
        const val KEY_SEED_COLOR = "appearance_seed_color"
        const val KEY_PALETTE = "appearance_palette"
        const val KEY_PURE_BLACK = "appearance_pure_black"
        const val KEY_BOTTOM_BAR_STYLE = "appearance_bottom_bar_style"
        const val KEY_SCALE = "appearance_scale"
        val APPEARANCE_KEYS = setOf(
            KEY_UI_STYLE, KEY_COLOR_MODE, KEY_COLOR_SOURCE, KEY_SEED_COLOR,
            KEY_PALETTE, KEY_PURE_BLACK, KEY_BOTTOM_BAR_STYLE, KEY_SCALE,
        )

        inline fun <reified T : Enum<T>> Map<String, *>.enumValue(key: String, fallback: T): T =
            enumValues<T>().firstOrNull { it.name == this[key] } ?: fallback
    }
}
