package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 陈旧数据的警示黄，与 getSemanticColor 的告警色一致。
private val StaleColor = Color(0xFFD4A027)

/**
 * 数据新鲜度文案：相对时长 + 精确本地时间，如「更新于 2 小时前 · 07/07 10:26」。
 * 陈旧（来自缓存）时额外追加「（缓存）」。
 */
internal fun formatUpdatedAt(
    updatedAt: Long,
    stale: Boolean = false,
    now: Long = System.currentTimeMillis()
): String {
    val relative = formatRelativeSince(updatedAt, now)
    val exact = SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()).format(Date(updatedAt))
    val suffix = if (stale) "（缓存）" else ""
    return "更新于 $relative · $exact$suffix"
}

/** 相对时长：刚刚 / X 分钟前 / X 小时前 / X 天前（满 24 小时进位为「天」）。 */
internal fun formatRelativeSince(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val diff = (now - timestamp).coerceAtLeast(0)
    val minutes = diff / 60_000
    val hours = minutes / 60
    val days = hours / 24
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "$minutes 分钟前"
        hours < 24 -> "$hours 小时前"
        else -> "$days 天前"
    }
}

/**
 * 卡片底部的更新时间行（展开态卡片使用）：时钟图标 + 更新文案。
 * 数据陈旧时整行变为警示黄，并改用带回环箭头的时钟图标。
 */
@Composable
internal fun CardFreshnessFooter(usageInfo: UsageInfo, modifier: Modifier = Modifier) {
    val color = if (usageInfo.stale) StaleColor else MaterialTheme.colorScheme.onSurfaceVariant
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (usageInfo.stale) Icons.Outlined.History else Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(13.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = formatUpdatedAt(usageInfo.updatedAt, usageInfo.stale),
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/**
 * 陈旧数据徽标（紧凑列表态使用）：仅在数据来自缓存时显示一枚警示黄时钟，
 * 让用户无需展开即可察觉数据不是最新的；数据新鲜时不占位。
 */
@Composable
internal fun StaleBadge(usageInfo: UsageInfo, modifier: Modifier = Modifier) {
    if (!usageInfo.stale) return
    Icon(
        imageVector = Icons.Outlined.History,
        contentDescription = "数据已陈旧（缓存）",
        modifier = modifier.size(15.dp),
        tint = StaleColor
    )
}
