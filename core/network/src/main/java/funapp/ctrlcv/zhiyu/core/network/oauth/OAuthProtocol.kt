package funapp.ctrlcv.zhiyu.core.network.oauth

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Public-client protocol values; these are identifiers, never client secrets. */
internal object OAuthProtocol {
    const val CLAUDE_CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
    const val CLAUDE_CALLBACK = "https://console.anthropic.com/oauth/code/callback"
    const val CLAUDE_TOKEN = "https://claude.ai/v1/oauth/token"
    const val CODEX_CLIENT_ID = "app_EMoamEEZ73f0CkXaXp7hrann"
    const val CODEX_TOKEN = "https://auth.openai.com/oauth/token"
    const val CODEX_DEVICE = "https://auth.openai.com/api/accounts/deviceauth/usercode"
    const val CODEX_POLL = "https://auth.openai.com/api/accounts/deviceauth/token"
    const val CODEX_VERIFY = "https://auth.openai.com/codex/device"
    const val CODEX_CALLBACK = "https://auth.openai.com/deviceauth/callback"

    fun clientId(platform: Platform): String = when (platform) {
        Platform.CLAUDE -> CLAUDE_CLIENT_ID
        Platform.CHATGPT -> CODEX_CLIENT_ID
        else -> error("OAuth is unavailable for this provider")
    }

    fun newClaudeChallenge(): ClaudeOAuthChallenge {
        fun randomString(): String = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val verifier = randomString()
        val state = randomString()
        val challenge = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = "https://claude.ai/oauth/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("code", "true")
            .addQueryParameter("client_id", CLAUDE_CLIENT_ID)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("redirect_uri", CLAUDE_CALLBACK)
            // Usage requires user:profile. This monitor neither calls inference nor creates API keys.
            .addQueryParameter("scope", "user:profile")
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .build().toString()
        return ClaudeOAuthChallenge(url, verifier, state)
    }

    fun isClaudeCallback(rawUrl: String): Boolean {
        val actual = rawUrl.toHttpUrlOrNull() ?: return false
        val expected = CLAUDE_CALLBACK.toHttpUrl()
        return actual.scheme == expected.scheme && actual.host == expected.host &&
            actual.port == expected.port && actual.encodedPath == expected.encodedPath
    }

    fun callbackCode(rawUrl: String, challenge: ClaudeOAuthChallenge): String {
        require(isClaudeCallback(rawUrl)) { "授权回调地址不匹配，请重新授权" }
        val url = rawUrl.toHttpUrl()
        require(url.queryParameter("error") == null) { "授权未完成，请重试" }
        val state = url.queryParameter("state") ?: error("授权回调缺少校验信息，请重新授权")
        require(MessageDigest.isEqual(state.toByteArray(), challenge.state.toByteArray())) {
            "授权校验失败，请重新授权"
        }
        return url.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: error("授权回调缺少授权码，请重试")
    }

    fun parseCredential(
        platform: Platform,
        json: JsonObject,
        now: Long,
        previous: OAuthCredential? = null
    ): OAuthCredential {
        val accessToken = json.text("access_token") ?: error("授权响应缺少令牌")
        val idClaims = jwtClaims(json.text("id_token"))
        val accessClaims = jwtClaims(accessToken)
        val providerId = when (platform) {
            Platform.CLAUDE -> json.obj("organization")?.text("uuid")
            Platform.CHATGPT -> json.text("account_id")
                ?: idClaims?.obj("https://api.openai.com/auth")?.text("chatgpt_account_id")
                ?: accessClaims?.obj("https://api.openai.com/auth")?.text("chatgpt_account_id")
            else -> null
        } ?: previous?.providerAccountId
        require(providerId != null) { "无法核验授权账号，请使用网页登录" }
        require(previous?.providerAccountId == null || providerId == previous.providerAccountId) {
            "续期返回了不同账号，请重新登录"
        }
        val expiresIn = json.number("expires_in")?.takeIf { it > 0 }
        val expiry = expiresIn?.coerceAtMost(31_536_000)?.let { now + it * 1000 }
            ?: accessClaims?.number("exp")?.takeIf { it > 0 && it < Long.MAX_VALUE / 1000 }?.times(1000)
        return OAuthCredential(
            accessToken = accessToken,
            refreshToken = json.text("refresh_token") ?: previous?.refreshToken,
            expiresAt = expiry,
            providerAccountId = providerId,
            displayName = json.obj("account")?.text("email_address")
                ?: json.text("email") ?: idClaims?.text("email") ?: previous?.displayName
        )
    }

    private fun jwtClaims(jwt: String?): JsonObject? = runCatching {
        val parts = jwt?.split('.')?.takeIf { it.size == 3 } ?: return null
        JsonParser.parseString(String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)).asJsonObject
    }.getOrNull()
}

class ClaudeOAuthChallenge internal constructor(
    val authorizationUrl: String,
    internal val verifier: String,
    internal val state: String
) {
    override fun toString(): String = "ClaudeOAuthChallenge([REDACTED])"
}

class DeviceCodeChallenge internal constructor(
    val userCode: String,
    internal val deviceAuthId: String,
    val verificationUrl: String = OAuthProtocol.CODEX_VERIFY,
    internal val intervalSeconds: Long,
    val expiresAt: Long
) {
    override fun toString(): String = "DeviceCodeChallenge([REDACTED])"
}

internal fun JsonObject.text(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }
    ?.asJsonPrimitive?.takeIf { it.isString }?.asString?.takeIf { it.isNotBlank() }
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.number(key: String): Long? = get(key)?.takeIf { it.isJsonPrimitive }
    ?.asJsonPrimitive?.asString?.toLongOrNull()
