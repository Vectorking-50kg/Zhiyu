package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import funapp.ctrlcv.zhiyu.core.domain.model.primaryMetric
import funapp.ctrlcv.zhiyu.core.domain.model.messageFor
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class WidgetPlatformItem(
    val name: String,
    val mainPercent: Float,
    val resetInfo: String? = null,
    val mainText: String = if (mainPercent >= 0) "${mainPercent.toInt()}%" else "--",
    val status: String? = null,
    val platform: Platform? = null,
    val accountId: String? = null,
    val accountName: String = name,
    val planLabel: String? = null,
    val metricLabel: String? = null,
    val elapsedPercent: Float? = null,
    val details: List<UsageItem> = emptyList(),
    val updatedAt: Long = 0,
    val statusLabel: String? = null,
    val pinned: Boolean = false,
    val paused: Boolean = false,
) {
    val accountKey: String? get() = platform?.let { p -> accountId?.let { WidgetAccountReference(p, it).key } }
    val reference: WidgetAccountReference? get() = platform?.let { p -> accountId?.let { WidgetAccountReference(p, it) } }
}

data class WidgetUsageData(
    val items: List<WidgetPlatformItem> = emptyList(),
    val lastUpdated: Long = 0L
)

fun List<UsageInfo>.toWidgetUsageData(now: Long = System.currentTimeMillis()): WidgetUsageData {
    val projected = map { it.atTime(now) }
    return WidgetUsageData(
        items = projected.map { info ->
            val metric = info.primaryMetric()
            val window = info.items.firstOrNull { it.label == metric.label }
            WidgetPlatformItem(
                name = info.platform.displayName,
                mainPercent = metric.percent?.toFloat() ?: -1f,
                mainText = metric.text,
                // Absolute times remain truthful between system-controlled widget updates.
                resetInfo = window?.resetAt?.let { timestamp ->
                    if (timestamp <= now) "等待额度更新" else DateTimeFormatter.ofPattern("MM-dd HH:mm 重置")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(timestamp))
                } ?: window?.resetCountdown ?: info.resetInfo,
                status = info.refreshFailure?.messageFor(info.platform) ?: if (info.stale) "缓存数据" else null,
                platform = info.platform,
                accountId = info.accountId,
                planLabel = info.planLabel,
                metricLabel = metric.label,
                elapsedPercent = metric.elapsedPercent,
                details = info.items,
                updatedAt = info.updatedAt,
                statusLabel = when (info.refreshFailure?.kind) {
                    UsageFailureKind.AUTH_REQUIRED -> if (info.platform.requiresApiKey) "更新密钥" else "重新登录"
                    UsageFailureKind.SUBSCRIPTION_REQUIRED -> "检查订阅"
                    UsageFailureKind.PERMISSION_DENIED -> "权限异常"
                    UsageFailureKind.RATE_LIMITED -> "请求限流"
                    UsageFailureKind.NETWORK -> "网络异常"
                    UsageFailureKind.SERVER -> "服务异常"
                    UsageFailureKind.INVALID_RESPONSE -> "解析异常"
                    UsageFailureKind.UNKNOWN -> "刷新失败"
                    null -> if (info.stale) "缓存" else null
                },
            )
        },
        lastUpdated = projected.filter { it.items.isNotEmpty() }.maxOfOrNull { it.updatedAt } ?: 0
    )
}

/** Include registered accounts without a successful fetch, and retain account identity. */
fun widgetUsageData(
    accounts: List<Account>, snapshots: List<UsageInfo>, pinnedPlatforms: Set<Platform> = emptySet(),
    now: Long = System.currentTimeMillis(),
): WidgetUsageData {
    val byAccount = snapshots.associateBy { it.platform to it.accountId }
    val data = accounts.map { account ->
        byAccount[account.platform to account.id]
            ?: UsageInfo(account.platform, emptyList(), accountId = account.id, updatedAt = 0)
    }.toWidgetUsageData(now)
    return data.copy(items = data.items.zip(accounts).map { (item, account) ->
        item.copy(
            accountName = account.displayName,
            planLabel = item.planLabel ?: account.planType.takeIf { it.isNotBlank() },
            pinned = account.pinned ?: (account.platform in pinnedPlatforms),
            paused = !account.monitoringEnabled,
            status = if (!account.monitoringEnabled) "监控已暂停" else item.status,
            statusLabel = when {
                !account.monitoringEnabled -> "已暂停"
                item.statusLabel != null && item.statusLabel != "缓存" -> item.statusLabel
                item.updatedAt <= 0 -> "待同步"
                else -> item.statusLabel
            },
        )
    })
}

/** Keep the highest measured quota visible even when a small widget cannot show every window. */
fun WidgetPlatformItem.visibleDetails(limit: Int): List<UsageItem> {
    if (limit <= 0) return emptyList()
    val shown = details.take(limit).toMutableList()
    val primary = details.firstOrNull { it.label == metricLabel }
    if (primary != null && primary !in shown) shown[shown.lastIndex] = primary
    return shown
}
