package funapp.ctrlcv.zhiyu.core.domain.model

data class UsageInfo(
    val platform: Platform,
    val items: List<UsageItem>,
    val planLabel: String? = null,
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
    val valueText: String? = null,
    // MiniMax Token Plan 专用：该窗口为无上限（无限制），不展示百分比进度
    val unlimited: Boolean = false,
    // MiniMax Token Plan 专用：boost 提升后的总额度百分比（如 200 表示「总额度 200%」），仅在有提升时展示
    val boostPercent: Int? = null
)
