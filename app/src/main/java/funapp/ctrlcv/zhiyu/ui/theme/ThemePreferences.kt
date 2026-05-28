package funapp.ctrlcv.zhiyu.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

const val THEME_PREFS_NAME = "zhiyu_theme_prefs"
const val KEY_COLOR_MODE = "colorMode"
const val KEY_THEME_ID = "themeId"
const val DEFAULT_THEME_ID = "zhiyu"

@Composable
fun rememberColorMode(): MutableState<ColorMode> {
    var raw by rememberThemePrefString(KEY_COLOR_MODE, ColorMode.SYSTEM.name)
    val colorMode by remember(raw) {
        derivedStateOf {
            ColorMode.entries.firstOrNull { it.name == raw } ?: ColorMode.SYSTEM
        }
    }
    return remember {
        object : MutableState<ColorMode> {
            override var value: ColorMode
                get() = colorMode
                set(value) { raw = value.name }
            override fun component1() = value
            override fun component2(): (ColorMode) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberThemeId(): MutableState<String> {
    var raw by rememberThemePrefString(KEY_THEME_ID, DEFAULT_THEME_ID)
    val themeId by remember(raw) {
        derivedStateOf { raw ?: DEFAULT_THEME_ID }
    }
    return remember {
        object : MutableState<String> {
            override var value: String
                get() = themeId
                set(value) { raw = value }
            override fun component1() = value
            override fun component2(): (String) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberThemePrefString(key: String, default: String): MutableState<String?> {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE) }
    val flow = remember(key, default) { prefs.stringFlow(key, default) }
    val state by flow.collectAsStateWithLifecycle(prefs.getString(key, default))
    return remember {
        object : MutableState<String?> {
            override var value: String?
                get() = state
                set(value) { prefs.edit { putString(key, value) } }
            override fun component1() = value
            override fun component2(): (String?) -> Unit = { value = it }
        }
    }
}

private fun SharedPreferences.stringFlow(key: String, default: String?) =
    callbackFlow {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
            if (k == key) trySend(getString(key, default))
        }
        registerOnSharedPreferenceChangeListener(listener)
        if (contains(key)) trySend(getString(key, default))
        awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
    }.buffer(Channel.UNLIMITED)
