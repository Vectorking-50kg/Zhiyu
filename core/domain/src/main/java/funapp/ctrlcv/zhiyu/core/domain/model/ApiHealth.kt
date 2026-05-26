package funapp.ctrlcv.zhiyu.core.domain.model

data class ApiHealth(
    val platform: Platform,
    val lastSuccess: Long,
    val consecutiveFailures: Int,
    val lastError: String? = null
)
