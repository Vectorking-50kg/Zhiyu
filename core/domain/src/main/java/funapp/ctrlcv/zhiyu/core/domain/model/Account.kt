package funapp.ctrlcv.zhiyu.core.domain.model

data class Account(
    val id: String,
    val platform: Platform,
    val displayName: String,
    val planType: String = "",
    val providerAccountId: String? = null,
    val monitoringEnabled: Boolean = true,
    val showOnOverview: Boolean? = null,
    val usageAlertEnabled: Boolean = true,
    val pinned: Boolean? = null,
)
