package funapp.ctrlcv.zhiyu.core.data.notification

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 通知相关的用户偏好：常驻通知总开关、固定到状态栏的平台集合，以及各类事件提醒开关。
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

    /** 用量阈值提醒（≥80% 警告 / ≥95% 紧急），默认开启，实际能否弹出仍受系统通知权限约束。 */
    var usageAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_USAGE_ALERT, true)
        set(value) = prefs.edit { putBoolean(KEY_USAGE_ALERT, value) }

    /** 额度重置提醒：限额接近用尽后，检测到额度重置时提醒。 */
    var resetReminderEnabled: Boolean
        get() = prefs.getBoolean(KEY_RESET_REMINDER, true)
        set(value) = prefs.edit { putBoolean(KEY_RESET_REMINDER, value) }

    /** 登录过期提醒：网页平台会话失效时提醒重新登录。 */
    var sessionExpiredAlertEnabled: Boolean
        get() = prefs.getBoolean(KEY_SESSION_EXPIRED_ALERT, true)
        set(value) = prefs.edit { putBoolean(KEY_SESSION_EXPIRED_ALERT, value) }

    /** 当前固定到状态栏的平台。 */
    fun pinnedPlatforms(): Set<Platform> {
        val keys = prefs.getStringSet(KEY_PINNED, null) ?: return emptySet()
        return Platform.displayOrder.filter { it.key in keys }.toSet()
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
        private const val KEY_USAGE_ALERT = "usage_alert_enabled"
        private const val KEY_RESET_REMINDER = "reset_reminder_enabled"
        private const val KEY_SESSION_EXPIRED_ALERT = "session_expired_alert_enabled"
    }
}
