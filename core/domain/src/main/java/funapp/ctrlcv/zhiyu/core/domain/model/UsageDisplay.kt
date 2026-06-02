package funapp.ctrlcv.zhiyu.core.domain.model

/**
 * 状态栏通知等紧凑场景下，单个平台的主指标文案。
 *
 * 取值口径与首页 [UsageInfo] 卡片保持一致：
 * - 货币余额平台（AIHubMix「余额」、DeepSeek「账户余额」）优先展示余额，如「¥12.30」；
 * - 其余平台展示最高用量百分比，如「45%」；
 * - 仅有无上限额度时展示「无限制」；无任何可用数据时返回「--」。
 */
fun UsageInfo.primaryMetricText(): String {
    balanceText()?.let { return it }
    val maxPercent = items
        .filter { it.percent >= 0f && !it.unlimited }
        .maxOfOrNull { it.percent }
    return when {
        maxPercent != null -> "${maxPercent.toInt()}%"
        items.any { it.unlimited } -> "无限制"
        else -> "--"
    }
}

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
