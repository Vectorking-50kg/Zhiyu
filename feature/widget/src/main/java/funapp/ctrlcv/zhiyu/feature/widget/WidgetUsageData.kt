package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
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
    val status: String? = null
)

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
                status = info.refreshFailure?.messageFor(info.platform) ?: if (info.stale) "缓存数据" else null
            )
        },
        lastUpdated = projected.filter { it.items.isNotEmpty() }.maxOfOrNull { it.updatedAt } ?: 0
    )
}
