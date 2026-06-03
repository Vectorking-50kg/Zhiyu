package funapp.ctrlcv.zhiyu.core.domain.model

/**
 * 单个平台在状态栏通知等紧凑场景下的主指标。
 *
 * @param text    展示文案，如「45%」「¥12.30」「无限制」「--」
 * @param percent 当主指标为用量百分比时给出 0..100 的整数，供进度条渲染；
 *                余额、无限制、无数据等场景为 null（不绘制进度条）
 * @param label   主指标的含义说明，如「5 小时限额」「账户余额」，用于在数值左侧标注；
 *                无可用数据时为 null
 */
data class UsageMetric(
    val text: String,
    val percent: Int?,
    val label: String? = null,
)

/**
 * 计算单个平台的主指标，取值口径与首页 [UsageInfo] 卡片保持一致：
 * - 货币余额平台（AIHubMix「余额」、DeepSeek「账户余额」）优先展示余额；
 * - 其余平台展示最高用量百分比；
 * - 仅有无上限额度时展示「无限制」；无任何可用数据时返回「--」。
 */
fun UsageInfo.primaryMetric(): UsageMetric {
    balanceItem()?.let { item ->
        return UsageMetric(formatBalance(item.valueText!!), percent = null, label = item.label)
    }
    val maxItem = items
        .filter { it.percent >= 0f && !it.unlimited }
        .maxByOrNull { it.percent }
    return when {
        maxItem != null -> UsageMetric("${maxItem.percent.toInt()}%", maxItem.percent.toInt(), label = maxItem.label)
        else -> {
            val unlimited = items.firstOrNull { it.unlimited }
            if (unlimited != null) UsageMetric("无限制", percent = null, label = unlimited.label)
            else UsageMetric("--", percent = null)
        }
    }
}

/** 仅取主指标文案，便于折叠态摘要等纯文本场景复用。 */
fun UsageInfo.primaryMetricText(): String = primaryMetric().text

/** 余额类平台（AIHubMix「余额」、DeepSeek「账户余额」）的余额条目，含有效 valueText 时才返回。 */
private fun UsageInfo.balanceItem(): UsageItem? = when (platform) {
    Platform.AIHUBMIX -> items.firstOrNull { it.label == "余额" }
    Platform.DEEPSEEK -> items.firstOrNull { it.label == "账户余额" }
    else -> null
}?.takeIf { it.valueText != null }

private fun formatBalance(valueText: String): String {
    val prefix = when {
        valueText.startsWith("$") -> "$"
        valueText.startsWith("¥") -> "¥"
        else -> ""
    }
    val num = valueText.removePrefix(prefix).toDoubleOrNull() ?: return valueText
    return "$prefix${String.format("%.2f", num)}"
}

