package funapp.ctrlcv.zhiyu.core.domain.model

private const val STALE_AFTER_MS = 30 * 60 * 1000L

/** Re-project time labels; never infer new usage from the clock or refresh fetchedAt. */
fun UsageInfo.atTime(now: Long = System.currentTimeMillis()): UsageInfo = copy(
    items = items.map { it.atTime(now) },
    stale = stale || refreshFailure != null || now - updatedAt >= STALE_AFTER_MS
)

fun UsageItem.atTime(now: Long = System.currentTimeMillis()): UsageItem {
    val target = resetAt ?: return this // Legacy snapshots remain readable until the next fetch.
    val remainingSeconds = (target.toDouble() - now.toDouble()) / 1000.0
    val duration = windowDurationSeconds?.takeIf { it > 0 }
    return copy(
        resetCountdown = formatResetCountdown(target, now),
        elapsedPercent = duration?.let {
            ((1.0 - remainingSeconds / it) * 100.0).coerceIn(0.0, 100.0).toFloat()
        }
    )
}

fun formatResetCountdown(resetAt: Long, now: Long = System.currentTimeMillis()): String {
    if (resetAt <= now) return "重置时间已到，等待更新"
    val minutes = ((resetAt.toDouble() - now.toDouble()) / 60_000.0).toLong()
    val hours = minutes / 60
    return when {
        hours >= 24 -> "${hours / 24}天后重置"
        hours > 0 -> "${hours}小时${if (minutes % 60 > 0) "${minutes % 60}分钟" else ""}后重置"
        minutes > 0 -> "${minutes}分钟后重置"
        else -> "即将重置"
    }
}
