package funapp.ctrlcv.zhiyu.core.storage

import android.content.SharedPreferences
import funapp.ctrlcv.zhiyu.core.domain.model.AppearanceSettings
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.ThemeColorSource
import funapp.ctrlcv.zhiyu.core.domain.model.ThemePalette
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import kotlinx.coroutines.flow.produceIn
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppearancePreferencesTest {
    @Test
    fun `existing color mode is retained without replacing the legacy theme`() {
        val prefs = MemoryPreferences(mapOf("colorMode" to "DARK", "themeId" to "cream"))
        val appearance = AppearancePreferences(prefs)

        assertEquals(AppearanceSettings(colorMode = ColorMode.DARK), appearance.read())
        appearance.update { it.copy(pureBlack = true) }

        assertEquals(ColorMode.DARK, appearance.read().colorMode)
        assertEquals("DARK", prefs.all["colorMode"])
        assertEquals("cream", prefs.all["themeId"])
    }

    @Test
    fun `unknown enum names and incorrect preference types fall back safely`() {
        val prefs = MemoryPreferences(mapOf(
            "appearance_ui_style" to "REMOVED_STYLE",
            "colorMode" to "automatic",
            "appearance_color_source" to 3,
            "appearance_seed_color" to "not-a-color",
            "appearance_palette" to "UNKNOWN_PALETTE",
            "appearance_pure_black" to "true",
            "appearance_bottom_bar_style" to "REMOVED_BAR",
            "appearance_scale" to "large",
        ))

        assertEquals(AppearanceSettings(), AppearancePreferences(prefs).read())
    }

    @Test
    fun `stored scale is clamped and nonfinite values use the default`() {
        val prefs = MemoryPreferences()
        val appearance = AppearancePreferences(prefs)
        val cases = listOf(
            0.3f to 0.8f,
            1.9f to 1.2f,
            1.1f to 1.1f,
            Float.NaN to 1f,
            Float.POSITIVE_INFINITY to 1f,
            Float.NEGATIVE_INFINITY to 1f,
        )

        cases.forEach { (stored, expected) ->
            prefs.edit().putFloat("appearance_scale", stored).apply()
            assertEquals(expected, appearance.read().scale, 0f)
        }
    }

    @Test
    fun `all appearance settings persist while unrelated keys remain untouched`() {
        val unrelated = mapOf("themeId" to "legacy", "other_setting" to true)
        val prefs = MemoryPreferences(unrelated)
        val appearance = AppearancePreferences(prefs)
        val expected = AppearanceSettings(
            uiStyle = UiStyle.MIUIX,
            colorMode = ColorMode.LIGHT,
            colorSource = ThemeColorSource.CUSTOM,
            seedColor = 0xFFAB4E1A.toInt(),
            palette = ThemePalette.FIDELITY,
            pureBlack = true,
            bottomBarStyle = BottomBarStyle.GLASS,
            scale = 1.15f,
        )

        appearance.update { expected }

        assertEquals(expected, AppearancePreferences(prefs).read())
        unrelated.forEach { (key, value) -> assertEquals(value, prefs.all[key]) }
        assertTrue((prefs.all.keys - unrelated.keys).all {
            it == "colorMode" || it.startsWith("appearance_")
        })
    }

    @Test
    fun `updates normalize scale before persistence and transform the latest settings`() {
        val prefs = MemoryPreferences()
        val appearance = AppearancePreferences(prefs)

        appearance.update { it.copy(scale = 2f, pureBlack = true) }
        assertEquals(1.2f, prefs.all["appearance_scale"])
        appearance.update { it.copy(scale = Float.NaN) }

        assertEquals(AppearanceSettings(scale = 1f, pureBlack = true), appearance.read())
        assertEquals(1f, prefs.all["appearance_scale"])
    }

    @Test
    fun `changes starts with current settings and combines a multi-key update`() = runBlocking {
        val prefs = MemoryPreferences(mapOf("colorMode" to "LIGHT"))
        val appearance = AppearancePreferences(prefs)
        val changes = appearance.changes.produceIn(this)
        try {
            assertEquals(ColorMode.LIGHT, withTimeout(1_000) { changes.receive() }.colorMode)

            appearance.update { it.copy(uiStyle = UiStyle.MIUIX, pureBlack = true, scale = 0.9f) }
            assertEquals(appearance.read(), withTimeout(1_000) { changes.receive() })

            prefs.edit().putString("themeId", "another-legacy-theme").apply()
            yield()
            yield()
            assertTrue(changes.tryReceive().isFailure)

            prefs.edit().putString("colorMode", "DARK").apply()
            assertEquals(ColorMode.DARK, withTimeout(1_000) { changes.receive() }.colorMode)

            prefs.edit().clear().apply()
            assertEquals(AppearanceSettings(), withTimeout(1_000) { changes.receive() })
        } finally {
            changes.cancel()
        }
        yield()
        assertEquals(0, prefs.listenerCount)
    }
}

private class MemoryPreferences(initial: Map<String, Any> = emptyMap()) : SharedPreferences {
    private val values = initial.toMutableMap()
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    val listenerCount: Int get() = listeners.size

    override fun getAll(): Map<String, *> = values.toMap()
    override fun contains(key: String?): Boolean = key in values
    override fun getString(key: String?, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getInt(key: String?, defValue: Int): Int = values[key] as? Int ?: defValue
    override fun getLong(key: String?, defValue: Long): Long = values[key] as? Long ?: defValue
    override fun getFloat(key: String?, defValue: Float): Float = values[key] as? Float ?: defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = values[key] as? Boolean ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        (values[key] as? Set<String>)?.toMutableSet() ?: defValues

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners += listener
    }

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        listeners -= listener
    }

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private var clearRequested = false

        private fun put(key: String?, value: Any?): SharedPreferences.Editor = apply {
            if (key != null) pending[key] = value
        }

        override fun putString(key: String?, value: String?) = put(key, value)
        override fun putStringSet(key: String?, values: MutableSet<String>?) = put(key, values?.toSet())
        override fun putInt(key: String?, value: Int) = put(key, value)
        override fun putLong(key: String?, value: Long) = put(key, value)
        override fun putFloat(key: String?, value: Float) = put(key, value)
        override fun putBoolean(key: String?, value: Boolean) = put(key, value)
        override fun remove(key: String?) = put(key, null)
        override fun clear(): SharedPreferences.Editor = apply { clearRequested = true }
        override fun apply() { commit() }
        override fun commit(): Boolean {
            val before = values.toMap()
            if (clearRequested) values.clear()
            pending.forEach { (key, value) ->
                if (value == null) values.remove(key) else values[key] = value
            }
            val changed = pending.keys.filter { before[it] != values[it] }
            listeners.toList().forEach { listener ->
                if (clearRequested) listener.onSharedPreferenceChanged(this@MemoryPreferences, null)
                changed.forEach { listener.onSharedPreferenceChanged(this@MemoryPreferences, it) }
            }
            return true
        }
    }
}
