package funapp.ctrlcv.zhiyu.core.data.notification

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.SharedPreferences
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.data.R
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 事件型提醒通知：用量阈值告警、额度重置提醒、登录过期提醒。
 *
 * 与 [BalanceNotificationManager] 的常驻通知互补——常驻通知持续陈列数据，
 * 本类只在状态发生「值得打扰」的变化时弹一次：
 * - 阈值告警：任一限额窗口用量升破 80%（警告）/ 95%（紧急）时提醒，同级别只提醒一次，
 *   回落后自动解除，下个周期再次升破才会再提醒；
 * - 重置提醒：某窗口上次观测 ≥80%、本次大幅回落，视为额度已重置并告知（借鉴 CodexBar 的周重置提示）；
 * - 登录过期：网页平台会话失效（[SessionEventBus] 收到 401/403）时提醒重新登录，
 *   重新拿到新鲜数据后自动撤销。
 *
 * 去重状态保存在普通 SharedPreferences（仅百分比快照，无敏感数据），
 * 由 [UsageRepositoryImpl][funapp.ctrlcv.zhiyu.core.data.repository.UsageRepositoryImpl]
 * 在每次成功刷新后驱动，后台 Worker 与首页手动刷新共用同一条路径。
 */
@Singleton
class UsageAlertManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val prefs: NotificationPreferences,
    private val gson: Gson,
    sessionEventBus: SessionEventBus,
    private val accountStore: AccountStore,
) {
    private val notificationManager = NotificationManagerCompat.from(context)
    private val state: SharedPreferences =
        context.getSharedPreferences(STATE_PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        // API 平台的 401 多半是密钥填错而非「过期」，且没有可跳转的重新登录流程，只提醒网页平台
        scope.launch {
            sessionEventBus.events.collect { event ->
                if (event is SessionEvent.SessionExpired && !event.platform.requiresApiKey) {
                    onSessionExpired(event.platform)
                }
            }
        }
    }

    /** 由仓库在拿到一份新鲜（非缓存回退）的用量数据后调用。 */
    fun onUsageUpdated(info: UsageInfo) {
        if (info.stale) return
        // 能拿到新鲜数据说明凭据有效，撤销此前的登录过期提醒
        clearSessionExpired(info.platform)
        if (info.accountId != null && accountStore.getAccounts(info.platform)
                .firstOrNull { it.id == info.accountId }?.usageAlertEnabled == false) return

        val quotaItems = info.items.filter { it.percent >= 0f && !it.unlimited }
        val prev = loadState(info.platform)
        if (quotaItems.isEmpty()) {
            checkDepletedBalance(info)
            if (prev != null) state.edit { remove(stateKey(info.platform)) }
            return
        }

        checkQuotaReset(info.platform, quotaItems, prev)
        val level = checkThreshold(info.platform, quotaItems, prev)

        saveState(
            info.platform,
            PlatformAlertState(
                percents = quotaItems.associate { (it.windowId ?: it.label) to it.percent },
                notifiedLevel = level,
            ),
        )
    }

    /** 某窗口上次观测已接近用尽、本次大幅回落 → 判定为额度重置。 */
    @SuppressLint("MissingPermission")
    private fun checkQuotaReset(
        platform: Platform,
        items: List<UsageItem>,
        prev: PlatformAlertState?,
    ) {
        prev ?: return
        val resetItems = items.filter { item ->
            val old = prev.percents[item.windowId ?: item.label] ?: prev.percents[item.label] ?: return@filter false
            old >= RESET_TRACK_PERCENT && item.percent <= old - RESET_DROP_PERCENT
        }
        if (resetItems.isEmpty() || !prefs.resetReminderEnabled || !canNotify()) return

        val text = resetItems.joinToString(" · ") { "${it.label}已重置，当前 ${it.percent.toInt()}%" }
        notificationManager.notify(
            RESET_ID_BASE + platform.ordinal,
            baseBuilder(CHANNEL_RESET)
                .setContentTitle("${platform.displayName} 额度已重置")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    /**
     * 阈值告警。返回本轮的告警级别以便持久化：
     * 级别只在升高时提醒（80→95 会再次提醒），降低时静默降级并撤销过时的告警。
     */
    @SuppressLint("MissingPermission")
    private fun checkThreshold(
        platform: Platform,
        items: List<UsageItem>,
        prev: PlatformAlertState?,
    ): Int {
        val level = items.maxOf { levelOf(it.percent) }
        val prevLevel = prev?.notifiedLevel ?: LEVEL_NONE

        if (level == LEVEL_NONE) {
            if (prevLevel > LEVEL_NONE) notificationManager.cancel(ALERT_ID_BASE + platform.ordinal)
            return level
        }
        if (level <= prevLevel || !prefs.usageAlertEnabled || !canNotify()) return level

        val danger = level == LEVEL_DANGER
        val text = items
            .filter { it.percent >= WARN_PERCENT }
            .sortedByDescending { it.percent }
            .joinToString(" · ") { item ->
                buildString {
                    append("${item.label} ${item.percent.toInt()}%")
                    item.resetCountdown?.takeIf { it.isNotBlank() }?.let { append("（$it）") }
                }
            }
        notificationManager.notify(
            ALERT_ID_BASE + platform.ordinal,
            baseBuilder(if (danger) CHANNEL_DANGER else CHANNEL_WARN)
                .setContentTitle("${platform.displayName} 用量${if (danger) "告急" else "偏高"}")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setPriority(if (danger) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .build(),
        )
        return level
    }

    @SuppressLint("MissingPermission")
    private fun onSessionExpired(platform: Platform) {
        if (!prefs.sessionExpiredAlertEnabled || !canNotify()) return
        // 后台每 15 分钟刷新都会触发 401，只在首次失效时提醒一次，恢复后由 clearSessionExpired 复位
        if (state.getBoolean(expiredKey(platform), false)) return
        state.edit { putBoolean(expiredKey(platform), true) }

        val text = "会话已失效，请打开 App 重新登录以继续更新用量"
        notificationManager.notify(
            EXPIRED_ID_BASE + platform.ordinal,
            baseBuilder(CHANNEL_EXPIRED)
                .setContentTitle("${platform.displayName} 登录已过期")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .build(),
        )
    }

    private fun clearSessionExpired(platform: Platform) {
        if (!state.getBoolean(expiredKey(platform), false)) return
        state.edit { remove(expiredKey(platform)) }
        notificationManager.cancel(EXPIRED_ID_BASE + platform.ordinal)
    }

    private fun baseBuilder(channelId: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_balance_notification)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .apply { launchIntent()?.let { setContentIntent(it) } }

    private fun launchIntent(): PendingIntent? {
        val intent = context.packageManager
            .getLaunchIntentForPackage(context.packageName) ?: return null
        return PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun canNotify(): Boolean = prefs.notificationsEnabled && notificationManager.areNotificationsEnabled()

    @SuppressLint("MissingPermission")
    private fun checkDepletedBalance(info: UsageInfo) {
        if (info.platform !in setOf(Platform.ZEN, Platform.AIHUBMIX, Platform.DEEPSEEK) || !prefs.usageAlertEnabled) return
        val raw = info.items.firstOrNull { it.label in setOf("余额", "账户余额") }?.valueText ?: return
        val balance = raw.removePrefix("$").removePrefix("¥").replace(",", "").toDoubleOrNull()
            ?.takeIf { it.isFinite() } ?: return
        val key = "balance_depleted_${info.platform.key}_${info.accountId.orEmpty()}"
        val notified = state.getBoolean(key, false)
        if (balance > 0) { state.edit { putBoolean(key, false) }; return }
        if (notified || !canNotify()) return
        notificationManager.notify(ALERT_ID_BASE + info.platform.ordinal, baseBuilder(CHANNEL_WARN)
            .setContentTitle("${info.platform.displayName} 余额已用尽")
            .setContentText("当前余额 $raw，请检查账户余额。")
            .build())
        state.edit { putBoolean(key, true) }
    }

    private fun levelOf(percent: Float): Int = when {
        percent >= DANGER_PERCENT -> LEVEL_DANGER
        percent >= WARN_PERCENT -> LEVEL_WARN
        else -> LEVEL_NONE
    }

    private fun loadState(platform: Platform): PlatformAlertState? {
        val json = state.getString(stateKey(platform), null) ?: return null
        return try {
            gson.fromJson(json, PlatformAlertState::class.java)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveState(platform: Platform, value: PlatformAlertState) {
        state.edit { putString(stateKey(platform), gson.toJson(value)) }
    }

    private fun stateKey(platform: Platform) = "alert_${platform.key}"
    private fun expiredKey(platform: Platform) = "expired_${platform.key}"

    /** 单平台的去重状态：各限额窗口最近一次观测的百分比 + 已提醒过的告警级别。 */
    private data class PlatformAlertState(
        val percents: Map<String, Float> = emptyMap(),
        val notifiedLevel: Int = LEVEL_NONE,
    )

    companion object {
        // 渠道在 ZhiyuApp 启动时统一注册，ID 以此处为准
        const val CHANNEL_WARN = "usage_warn"
        const val CHANNEL_DANGER = "usage_danger"
        const val CHANNEL_EXPIRED = "session_expired"
        const val CHANNEL_RESET = "quota_reset"

        // 与渠道描述（80% 提醒 / 95% 强提醒）保持一致
        private const val WARN_PERCENT = 80f
        private const val DANGER_PERCENT = 95f

        // 上次 ≥80% 且本次回落超过 40 个百分点，判定该窗口额度已重置；
        // 阈值取得比「归零」宽松，容忍重置后到下次刷新之间产生的少量新用量
        private const val RESET_TRACK_PERCENT = 80f
        private const val RESET_DROP_PERCENT = 40f

        private const val LEVEL_NONE = 0
        private const val LEVEL_WARN = 1
        private const val LEVEL_DANGER = 2

        // 常驻通知占用 1001，事件型通知按平台错开各占一段
        private const val ALERT_ID_BASE = 2000
        private const val EXPIRED_ID_BASE = 3000
        private const val RESET_ID_BASE = 4000

        private const val STATE_PREFS_NAME = "usage_alert_state"
    }
}
