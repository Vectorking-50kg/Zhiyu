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
    val percent: Float,
    val resetCountdown: String? = null
)
