package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.RateLimitException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import okhttp3.Response
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** HTTP errors are operational metadata; neither response bodies nor parser errors escape. */
internal object UsageHttpResponse {
    fun read(response: Response, platform: Platform, nowMillis: Long = System.currentTimeMillis()): String {
        val body = response.body?.string().orEmpty()
        when {
            response.code == 401 -> throw SessionExpiredException(platform)
            response.code == 429 || isGitHubRateLimit(response, platform) ->
                throw RateLimitException(platform, retryDelay(response, platform, nowMillis), response.code)
            response.code == 403 && platform == Platform.OPENCODE_GO && explicitlyRequiresGoSubscription(body) ->
                throw UsageRequestException(platform, UsageFailure(UsageFailureKind.SUBSCRIPTION_REQUIRED, 403, occurredAt = nowMillis))
            response.code == 403 && explicitlyRequiresAuthentication(body) -> throw SessionExpiredException(platform)
            response.code == 403 -> throw PermissionDeniedException(platform)
            response.code in 500..599 -> throw UsageRequestException(platform, UsageFailure(UsageFailureKind.SERVER, response.code, occurredAt = nowMillis))
            !response.isSuccessful -> throw UsageRequestException(platform, UsageFailure(UsageFailureKind.UNKNOWN, response.code, occurredAt = nowMillis))
            body.isBlank() -> throw ApiStructureChangedException(platform, "Empty usage response")
        }
        return body
    }

    private fun isGitHubRateLimit(response: Response, platform: Platform): Boolean =
        platform == Platform.GITHUB_COPILOT && response.code == 403 &&
            (response.header("X-RateLimit-Remaining") == "0" || response.header("Retry-After") != null)

    private fun retryDelay(response: Response, platform: Platform, nowMillis: Long): Long? {
        retryAfter(response.header("Retry-After"), nowMillis)?.let { return it }
        if (platform != Platform.GITHUB_COPILOT || response.header("X-RateLimit-Remaining") != "0") return null
        val epochSeconds = response.header("X-RateLimit-Reset")?.toLongOrNull()?.takeIf { it > 0 }
        return epochSeconds?.let { (it - nowMillis / 1000).coerceAtLeast(0) }
    }

    private fun explicitlyRequiresAuthentication(body: String): Boolean = runCatching {
        val root = JsonParser.parseString(body.take(8192)).takeIf { it.isJsonObject }?.asJsonObject ?: return@runCatching false
        val error = root.objectOrNull("error")
        val code = error?.stringOrNull("code") ?: error?.stringOrNull("type") ?: root.stringOrNull("error")
            ?: root.stringOrNull("error_code")
        code?.lowercase() in setOf("authentication_error", "invalid_api_key", "invalid_token", "token_expired", "session_expired", "invalid_session", "unauthorized")
    }.getOrDefault(false)

    private fun explicitlyRequiresGoSubscription(body: String): Boolean = runCatching {
        val root = JsonParser.parseString(body.take(8192)).takeIf { it.isJsonObject }?.asJsonObject
        root?.objectOrNull("error")?.stringOrNull("type") == "EntitlementError"
    }.getOrDefault(false)

    private fun retryAfter(value: String?, nowMillis: Long): Long? {
        if (value == null) return null
        value.trim().toLongOrNull()?.let { return it.coerceAtLeast(0) }
        return runCatching {
            val resetAt = ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            ((resetAt - nowMillis) / 1000L).coerceAtLeast(0)
        }.getOrNull()
    }
}
