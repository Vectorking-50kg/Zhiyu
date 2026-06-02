package funapp.ctrlcv.zhiyu.core.data.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 状态栏常驻余额通知的用户偏好：总开关 + 选中固定到状态栏的平台集合。
 *
 * 不含敏感数据，使用普通 SharedPreferences；可被 RefreshWorker 与设置页共同读写。
 */
@Singleton
class NotificationPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** 常驻通知总开关。 */
    var persistentEnabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_ENABLED, value) }

    /** 当前固定到状态栏的平台。 */
    fun pinnedPlatforms(): Set<Platform> {
        val keys = prefs.getStringSet(KEY_PINNED, null) ?: return emptySet()
        return Platform.entries.filter { it.key in keys }.toSet()
    }

    /** 增删单个固定平台。 */
    fun setPinned(platform: Platform, pinned: Boolean) {
        val keys = (prefs.getStringSet(KEY_PINNED, emptySet()) ?: emptySet()).toMutableSet()
        if (pinned) keys.add(platform.key) else keys.remove(platform.key)
        prefs.edit { putStringSet(KEY_PINNED, keys) }
    }

    companion object {
        private const val PREFS_NAME = "notification_prefs"
        private const val KEY_ENABLED = "persistent_enabled"
        private const val KEY_PINNED = "pinned_platforms"
    }
}
