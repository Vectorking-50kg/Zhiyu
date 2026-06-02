package funapp.ctrlcv.zhiyu.core.domain.model

/**
 * 单个平台在状态栏通知等紧凑场景下的主指标。
 *
 * @param text    展示文案，如「45%」「¥12.30」「无限制」「--」
 * @param percent 当主指标为用量百分比时给出 0..100 的整数，供进度条渲染；
 *                余额、无限制、无数据等场景为 null（不绘制进度条）
 */
data class UsageMetric(
    val text: String,
    val percent: Int?,
)

/**
 * 计算单个平台的主指标，取值口径与首页 [UsageInfo] 卡片保持一致：
 * - 货币余额平台（AIHubMix「余额」、DeepSeek「账户余额」）优先展示余额；
 * - 其余平台展示最高用量百分比；
 * - 仅有无上限额度时展示「无限制」；无任何可用数据时返回「--」。
 */
fun UsageInfo.primaryMetric(): UsageMetric {
    balanceText()?.let { return UsageMetric(it, percent = null) }
    val maxPercent = items
        .filter { it.percent >= 0f && !it.unlimited }
        .maxOfOrNull { it.percent }
    return when {
        maxPercent != null -> UsageMetric("${maxPercent.toInt()}%", maxPercent.toInt())
        items.any { it.unlimited } -> UsageMetric("无限制", percent = null)
        else -> UsageMetric("--", percent = null)
    }
}

/** 仅取主指标文案，便于折叠态摘要等纯文本场景复用。 */
fun UsageInfo.primaryMetricText(): String = primaryMetric().text

private fun UsageInfo.balanceText(): String? {
    val raw = when (platform) {
        Platform.AIHUBMIX -> items.firstOrNull { it.label == "余额" }?.valueText
        Platform.DEEPSEEK -> items.firstOrNull { it.label == "账户余额" }?.valueText
        else -> null
    } ?: return null
    return formatBalance(raw)
}

private fun formatBalance(valueText: String): String {
    val prefix = when {
        valueText.startsWith("$") -> "$"
        valueText.startsWith("¥") -> "¥"
        else -> ""
    }
    val num = valueText.removePrefix(prefix).toDoubleOrNull() ?: return valueText
    return "$prefix${String.format("%.2f", num)}"
}

