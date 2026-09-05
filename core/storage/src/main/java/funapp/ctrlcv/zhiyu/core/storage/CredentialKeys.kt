package funapp.ctrlcv.zhiyu.core.storage

import funapp.ctrlcv.zhiyu.core.domain.model.Platform

/** Match complete account identities, not prefixes shared by imported account IDs. */
internal fun credentialKeyBelongsTo(key: String, platform: Platform, accountId: String): Boolean {
    val owner = "${platform.key}_$accountId"
    return when {
        key.endsWith("_cookie") -> key == "${owner}_cookie"
        key.endsWith("_oauth") -> key == "${owner}_oauth"
        else -> key.substringBeforeLast("_extra_", missingDelimiterValue = "") == owner
    }
}
