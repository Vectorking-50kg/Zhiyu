package funapp.ctrlcv.zhiyu.core.data.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.data.R
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.primaryMetricText
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 维护状态栏常驻的「余额 / 用量」通知。
 *
 * 通知内容来自 [UsageCache]，由 RefreshWorker 在每次刷新后、以及 App 启动时调用 [refresh]
 * 重建。低优先级静默渠道 + setOngoing(true) 实现常驻且不可滑动清除。
 */
@Singleton
class BalanceNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: UsageCache,
    private val prefs: NotificationPreferences,
) {
    private val notificationManager = NotificationManagerCompat.from(context)

    /**
     * 根据当前偏好与缓存重建常驻通知。
     * 总开关关闭、未选中任何平台、或无通知权限时取消通知。
     */
    @SuppressLint("MissingPermission")
    fun refresh() {
        val pinned = prefs.pinnedPlatforms()
        if (!prefs.persistentEnabled || pinned.isEmpty() || !notificationManager.areNotificationsEnabled()) {
            cancel()
            return
        }
        ensureChannel()
        val infos = cache.getAll().filter { it.platform in pinned }
        notificationManager.notify(NOTIFICATION_ID, buildNotification(pinned, infos))
    }

    fun cancel() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    private fun buildNotification(pinned: Set<Platform>, infos: List<UsageInfo>): Notification {
        val byPlatform = infos.associateBy { it.platform }
        // 按 Platform 枚举顺序稳定排序，避免每次刷新通知里平台顺序跳动
        val lines = Platform.entries
            .filter { it in pinned }
            .map { platform ->
                val value = byPlatform[platform]?.primaryMetricText() ?: "--"
                "${platform.displayName}  $value"
            }
        val stale = infos.isNotEmpty() && infos.all { it.stale }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_balance_notification)
            .setContentTitle(if (stale) "AI 用量 · 余额（缓存）" else "AI 用量 · 余额")
            .setContentText(lines.joinToString("    "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(lines.joinToString("\n")))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        launchIntent()?.let { builder.setContentIntent(it) }
        return builder.build()
    }

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

    private fun ensureChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "余额常驻",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "在状态栏常驻显示所选平台的用量与余额"
            setShowBadge(false)
        }
        context.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "balance_persistent"
        private const val NOTIFICATION_ID = 1001
    }
}
