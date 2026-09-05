package funapp.ctrlcv.zhiyu.core.network.oauth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.RateLimitException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class OAuthTokenClient internal constructor(
    baseClient: OkHttpClient,
    private val endpoints: OAuthEndpoints,
    private val now: () -> Long = System::currentTimeMillis,
    private val pause: suspend (Long) -> Unit = { delay(it) }
) {
    @Inject constructor(client: OkHttpClient) : this(client, OAuthEndpoints())

    // Credential exchanges must never pass through body loggers or follow a redirect with secrets.
    private val client = baseClient.newBuilder().apply {
        interceptors().clear()
        networkInterceptors().clear()
    }.followRedirects(false).followSslRedirects(false).callTimeout(30, TimeUnit.SECONDS).build()

    fun newClaudeChallenge(): ClaudeOAuthChallenge = OAuthProtocol.newClaudeChallenge()
    fun isClaudeCallback(url: String): Boolean = OAuthProtocol.isClaudeCallback(url)

    suspend fun exchangeClaude(challenge: ClaudeOAuthChallenge, callback: String): OAuthCredential {
        val code = OAuthProtocol.callbackCode(callback, challenge)
        return exchange(Platform.CLAUDE, mapOf(
            "grant_type" to "authorization_code", "code" to code,
            "code_verifier" to challenge.verifier, "state" to challenge.state,
            "redirect_uri" to OAuthProtocol.CLAUDE_CALLBACK
        ))
    }

    suspend fun refresh(platform: Platform, previous: OAuthCredential): OAuthCredential {
        val refresh = previous.refreshToken ?: throw SessionExpiredException(platform)
        return exchange(platform, mapOf("grant_type" to "refresh_token", "refresh_token" to refresh), previous)
    }

    suspend fun requestCodexDeviceCode(): DeviceCodeChallenge {
        val response = post(Platform.CHATGPT, endpoints.codexDevice, mapOf(
            "client_id" to OAuthProtocol.CODEX_CLIENT_ID
        ), json = true)
        checkStatus(Platform.CHATGPT, response)
        val body = parse(Platform.CHATGPT, response.body)
        val userCode = body.text("user_code") ?: body.text("usercode") ?: malformed(Platform.CHATGPT)
        val deviceId = body.text("device_auth_id") ?: malformed(Platform.CHATGPT)
        val verification = body.text("verification_uri")
        if (verification != null && verification != OAuthProtocol.CODEX_VERIFY) malformed(Platform.CHATGPT)
        // The official CLI caps polling at 15 minutes; some responses omit expires_in entirely.
        val expiry = body.number("expires_in")?.takeIf { it > 0 }?.coerceAtMost(900)?.let { now() + it * 1000 }
            ?: body.text("expires_at")?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
            ?.coerceAtMost(now() + 900_000)
            ?: (now() + 900_000)
        return DeviceCodeChallenge(
            userCode = userCode,
            deviceAuthId = deviceId,
            intervalSeconds = body.number("interval")?.coerceIn(5, 900) ?: 5,
            expiresAt = expiry
        )
    }

    suspend fun awaitCodexAuthorization(challenge: DeviceCodeChallenge): OAuthCredential {
        var interval = challenge.intervalSeconds
        while (now() < challenge.expiresAt) {
            pause(minOf(interval * 1000, (challenge.expiresAt - now()).coerceAtLeast(0)))
            if (now() >= challenge.expiresAt) break
            val response = post(Platform.CHATGPT, endpoints.codexPoll, mapOf(
                "device_auth_id" to challenge.deviceAuthId,
                "user_code" to challenge.userCode
            ), json = true)
            val parsed = runCatching { parse(Platform.CHATGPT, response.body) }.getOrNull()
            val error = parsed?.errorCode()
            if (error in setOf("access_denied", "expired_token")) throw SessionExpiredException(Platform.CHATGPT)
            if (error == "slow_down" || response.status == 429) {
                interval = maxOf(interval + 5, response.retryAfter ?: 0).coerceAtMost(900)
                continue
            }
            if (response.status in setOf(403, 404) || error == "authorization_pending") continue
            checkStatus(Platform.CHATGPT, response)
            val body = parsed ?: malformed(Platform.CHATGPT)
            return exchange(Platform.CHATGPT, mapOf(
                "grant_type" to "authorization_code",
                "code" to (body.text("authorization_code") ?: malformed(Platform.CHATGPT)),
                "code_verifier" to (body.text("code_verifier") ?: malformed(Platform.CHATGPT)),
                "redirect_uri" to OAuthProtocol.CODEX_CALLBACK
            ))
        }
        throw OAuthLoginExpiredException()
    }

    private suspend fun exchange(
        platform: Platform,
        fields: Map<String, String>,
        previous: OAuthCredential? = null
    ): OAuthCredential {
        val endpoint = if (platform == Platform.CLAUDE) endpoints.claudeToken else endpoints.codexToken
        val response = post(platform, endpoint,
            fields + ("client_id" to OAuthProtocol.clientId(platform)), json = platform == Platform.CLAUDE)
        checkStatus(platform, response)
        return try {
            OAuthProtocol.parseCredential(platform, parse(platform, response.body), now(), previous)
        } catch (e: IllegalArgumentException) {
            malformed(platform)
        } catch (e: IllegalStateException) {
            malformed(platform)
        }
    }

    private suspend fun post(platform: Platform, url: String, fields: Map<String, String>, json: Boolean): SafeResponse {
        val body = if (json) JsonObject().apply { fields.forEach { (key, value) -> addProperty(key, value) } }
            .toString().toRequestBody("application/json".toMediaType())
        else FormBody.Builder().apply { fields.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url).post(body).header("Accept", "application/json")
            .header("User-Agent", "Zhiyu/1.0").build()
        return suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            SafeResponse(it.code, it.body?.string().orEmpty(), it.header("Retry-After")?.toLongOrNull())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (e: IOException) {
                        if (continuation.isActive) continuation.resumeWithException(e)
                    }
                }
            })
        }
    }

    private fun checkStatus(platform: Platform, response: SafeResponse) {
        if (response.status in 200..299) return
        val code = runCatching { parse(platform, response.body).errorCode() }.getOrNull()
        when {
            response.status == 401 || code in setOf("invalid_grant", "expired_token", "invalid_token", "access_denied") ->
                throw SessionExpiredException(platform)
            response.status == 403 -> throw PermissionDeniedException(platform)
            response.status == 429 -> throw RateLimitException(platform, response.retryAfter)
            else -> throw UsageRequestException(platform, UsageFailure(
                if (response.status >= 500) UsageFailureKind.SERVER else UsageFailureKind.INVALID_RESPONSE,
                httpStatus = response.status
            ))
        }
    }

    private fun parse(platform: Platform, body: String): JsonObject = try {
        JsonParser.parseString(body).asJsonObject
    } catch (e: RuntimeException) { malformed(platform) }

    private fun malformed(platform: Platform): Nothing =
        throw ApiStructureChangedException(platform, "无法核验授权返回的数据，请重试或使用网页登录")

    private class SafeResponse(val status: Int, val body: String, val retryAfter: Long?) {
        override fun toString(): String = "OAuthResponse(status=$status)"
    }
}

internal data class OAuthEndpoints(
    val claudeToken: String = OAuthProtocol.CLAUDE_TOKEN,
    val codexToken: String = OAuthProtocol.CODEX_TOKEN,
    val codexDevice: String = OAuthProtocol.CODEX_DEVICE,
    val codexPoll: String = OAuthProtocol.CODEX_POLL
)

class OAuthLoginExpiredException : Exception("授权码已过期，请重新获取")
private fun JsonObject.errorCode(): String? = text("error") ?: obj("error")?.text("code")
    ?: obj("error")?.text("type") ?: text("error_code")
