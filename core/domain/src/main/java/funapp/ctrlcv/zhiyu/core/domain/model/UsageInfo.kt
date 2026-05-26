package funapp.ctrlcv.zhiyu.core.domain.model

data class UsageInfo(
    val platform: Platform,
    val items: List<UsageItem>,
    val resetInfo: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val stale: Boolean = false
)

data class UsageItem(
    val label: String,
    // -1f = 纯信息行（不显示进度条，用 valueText 展示）; 0-100 = 百分比
    val percent: Float,
    val resetCountdown: String? = null,
    // 当 percent < 0 时作为主要展示内容（如 "¥10.50"）；
    // 当 percent >= 0 时作为进度条下方的补充说明
    val valueText: String? = null
)
