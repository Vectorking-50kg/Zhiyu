package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** 持久化首页供应商的显示偏好。隐藏集合之外的平台默认显示。 */
@Singleton
class HomePlatformPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _visiblePlatforms = MutableStateFlow(readVisiblePlatforms())
    val visiblePlatforms: StateFlow<Set<Platform>> = _visiblePlatforms.asStateFlow()

    fun setVisible(platform: Platform, visible: Boolean) {
        val hiddenKeys = prefs.getStringSet(KEY_HIDDEN_PLATFORMS, emptySet())
            .orEmpty()
            .toMutableSet()
        if (visible) hiddenKeys.remove(platform.key) else hiddenKeys.add(platform.key)
        prefs.edit { putStringSet(KEY_HIDDEN_PLATFORMS, hiddenKeys) }
        _visiblePlatforms.value = readVisiblePlatforms()
    }

    private fun readVisiblePlatforms(): Set<Platform> {
        val hiddenKeys = prefs.getStringSet(KEY_HIDDEN_PLATFORMS, emptySet()).orEmpty()
        return Platform.entries.filterNot { it.key in hiddenKeys }.toSet()
    }

    private companion object {
        const val PREFS_NAME = "home_platform_prefs"
        const val KEY_HIDDEN_PLATFORMS = "hidden_platforms"
    }
}
