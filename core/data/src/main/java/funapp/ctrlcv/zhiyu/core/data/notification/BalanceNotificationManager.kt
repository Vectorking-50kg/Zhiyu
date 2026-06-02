package funapp.ctrlcv.zhiyu.core.data.notification

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.res.ColorStateList
import android.os.Build
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.data.R
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.primaryMetric
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
        val platforms = Platform.entries.filter { it in pinned }
        val stale = infos.isNotEmpty() && infos.all { it.stale }
        val title = if (stale) "AI 用量 · 余额（缓存）" else "AI 用量 · 余额"

        // 折叠态摘要：纯文本一行，展开后由自定义大视图呈现进度条与状态色
        val summary = platforms.joinToString("    ") { platform ->
            "${platform.displayName} ${byPlatform[platform]?.primaryMetricText() ?: "--"}"
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_balance_notification)
            .setContentTitle(title)
            .setContentText(summary)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(buildExpandedView(title, platforms, byPlatform))
            .setOngoing(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)

        launchIntent()?.let { builder.setContentIntent(it) }
        return builder.build()
    }

    /** 构建展开态自定义视图：标题 + 每个平台一行（状态色点、名称、数值、进度条）。 */
    private fun buildExpandedView(
        title: String,
        platforms: List<Platform>,
        byPlatform: Map<Platform, UsageInfo>,
    ): RemoteViews {
        val expanded = RemoteViews(context.packageName, R.layout.notification_balance_expanded)
        expanded.setTextViewText(R.id.notif_header, title)
        expanded.removeAllViews(R.id.notif_rows)

        platforms.forEach { platform ->
            val metric = byPlatform[platform]?.primaryMetric()
            val percent = metric?.percent
            val color = if (percent != null) semanticColor(percent) else BALANCE_COLOR

            val row = RemoteViews(context.packageName, R.layout.notification_balance_row)
            row.setTextViewText(R.id.platform_name, platform.displayName)
            row.setTextViewText(R.id.platform_value, metric?.text ?: "--")
            row.setTextColor(R.id.platform_value, color)
            row.setInt(R.id.platform_dot, "setColorFilter", color)

            if (percent != null) {
                row.setViewVisibility(R.id.platform_bar, View.VISIBLE)
                row.setProgressBar(R.id.platform_bar, 100, percent, false)
                // 进度条着色需 API 31+；低版本沿用系统主题色，语义由数值文字颜色承担
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    row.setColorStateList(
                        R.id.platform_bar,
                        "setProgressTintList",
                        ColorStateList.valueOf(color),
                    )
                }
            } else {
                row.setViewVisibility(R.id.platform_bar, View.GONE)
            }
            expanded.addView(R.id.notif_rows, row)
        }
        return expanded
    }

    /** 与首页卡片一致的用量语义色：充裕(绿) / 偏高(琥珀) / 紧张(红)。 */
    private fun semanticColor(percent: Int): Int = when {
        percent < 70 -> COLOR_OK
        percent < 90 -> COLOR_WARN
        else -> COLOR_DANGER
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

        // 与 dashboard getSemanticColor 取色一致，保证通知与首页观感统一
        private const val COLOR_OK = 0xFF4A9D6F.toInt()
        private const val COLOR_WARN = 0xFFD4A027.toInt()
        private const val COLOR_DANGER = 0xFFD94F4F.toInt()
        // 余额类平台的状态点 / 数值用中性绿，表达「尚有余额」
        private const val BALANCE_COLOR = COLOR_OK
    }
}
