package funapp.ctrlcv.zhiyu.core.domain.model

import java.io.IOException
import kotlinx.coroutines.CancellationException

enum class UsageFailureKind {
    AUTH_REQUIRED, PERMISSION_DENIED, RATE_LIMITED, NETWORK, SERVER, INVALID_RESPONSE, UNKNOWN
}

/** Safe operational metadata only: no exception text, tokens, URLs or response bodies. */
data class UsageFailure(
    val kind: UsageFailureKind,
    val httpStatus: Int? = null,
    val retryAt: Long? = null,
    val occurredAt: Long = System.currentTimeMillis()
) {
    val message: String
        get() = when (kind) {
            UsageFailureKind.AUTH_REQUIRED -> "登录已失效，请重新登录"
            UsageFailureKind.PERMISSION_DENIED -> "暂时无法访问，请检查账号权限或稍后重试"
            UsageFailureKind.RATE_LIMITED -> "请求过于频繁，请稍后重试"
            UsageFailureKind.NETWORK -> "网络连接失败，请检查网络后重试"
            UsageFailureKind.SERVER -> "平台服务暂时异常，请稍后重试"
            UsageFailureKind.INVALID_RESPONSE -> "暂时无法识别平台返回的数据"
            UsageFailureKind.UNKNOWN -> "更新失败，请稍后重试"
        }

    val requiresLogin: Boolean get() = kind == UsageFailureKind.AUTH_REQUIRED
    val retryable: Boolean get() = kind in setOf(
        UsageFailureKind.NETWORK, UsageFailureKind.SERVER, UsageFailureKind.RATE_LIMITED
    )
}

fun UsageFailure.messageFor(platform: Platform): String =
    if (requiresLogin && platform.requiresApiKey) "API 密钥无效，请检查后重试" else message

fun Throwable.toUsageFailure(now: Long = System.currentTimeMillis()): UsageFailure = when (this) {
    is CancellationException -> throw this
    is UsageRequestException -> failure
    is NoCookieException, is SessionExpiredException -> UsageFailure(UsageFailureKind.AUTH_REQUIRED, occurredAt = now)
    is PermissionDeniedException -> UsageFailure(UsageFailureKind.PERMISSION_DENIED, 403, occurredAt = now)
    is RateLimitException -> UsageFailure(
        UsageFailureKind.RATE_LIMITED,
        httpStatus = 429,
        retryAt = retryAfterSeconds?.coerceAtLeast(0)?.let { seconds ->
            runCatching { Math.addExact(now, Math.multiplyExact(seconds, 1000L)) }.getOrDefault(Long.MAX_VALUE)
        },
        occurredAt = now
    )
    is ApiStructureChangedException -> UsageFailure(UsageFailureKind.INVALID_RESPONSE, occurredAt = now)
    is IOException -> UsageFailure(UsageFailureKind.NETWORK, occurredAt = now)
    else -> UsageFailure(UsageFailureKind.UNKNOWN, occurredAt = now)
}
