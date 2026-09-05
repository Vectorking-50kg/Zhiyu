package funapp.ctrlcv.zhiyu.core.domain.model

data class Account(
    val id: String,
    val platform: Platform,
    val displayName: String,
    val planType: String = "",
    val providerAccountId: String? = null
)
