package funapp.ctrlcv.zhiyu.core.network.oauth

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/** Protocol for Antigravity's installed-app registration; values arrive through build configuration. */
internal object AntigravityOAuth {
    const val TOKEN_URL = "https://oauth2.googleapis.com/token"
    const val USER_INFO_URL = "https://www.googleapis.com/oauth2/v2/userinfo"
    // Intercepted by WebView before network access; no process-local HTTP listener is needed.
    const val EMBEDDED_CALLBACK = "http://127.0.0.1:8089/oauth2callback"

    fun challenge(redirectUri: String, clientId: String): AntigravityOAuthChallenge {
        require(clientId.isNotBlank())
        val redirect = redirectUri.toHttpUrl()
        require(redirect.scheme == "http" && redirect.host == "127.0.0.1" && redirect.port > 0 &&
            redirect.encodedPath == "/oauth2callback" && redirect.query == null && redirect.fragment == null &&
            redirect.username.isEmpty() && redirect.password.isEmpty())
        fun random() = ByteArray(32).also { SecureRandom().nextBytes(it) }
            .let { Base64.getUrlEncoder().withoutPadding().encodeToString(it) }
        val verifier = random()
        val state = random()
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        val url = "https://accounts.google.com/o/oauth2/v2/auth".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", clientId)
            .addQueryParameter("redirect_uri", redirectUri)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("scope", listOf(
                "openid", "https://www.googleapis.com/auth/cloud-platform",
                "https://www.googleapis.com/auth/userinfo.email", "https://www.googleapis.com/auth/userinfo.profile",
                "https://www.googleapis.com/auth/aicode",
            ).joinToString(" "))
            .addQueryParameter("access_type", "offline")
            .addQueryParameter("prompt", "consent")
            .addQueryParameter("state", state)
            .addQueryParameter("code_challenge", proof)
            .addQueryParameter("code_challenge_method", "S256")
            .build().toString()
        return AntigravityOAuthChallenge(url, redirectUri, verifier, state)
    }

    fun isCallback(rawUrl: String, redirectUri: String): Boolean {
        val url = rawUrl.toHttpUrlOrNull() ?: return false
        val expected = redirectUri.toHttpUrl()
        return url.scheme == expected.scheme && url.host == expected.host && url.port == expected.port &&
            url.encodedPath == expected.encodedPath
    }

    fun matchesCallback(rawUrl: String, challenge: AntigravityOAuthChallenge): Boolean {
        val url = rawUrl.toHttpUrlOrNull() ?: return false
        val state = url.queryParameter("state") ?: return false
        return isCallback(rawUrl, challenge.redirectUri) && url.username.isEmpty() && url.password.isEmpty() &&
            url.fragment == null && url.queryParameterValues("state").size == 1 &&
            MessageDigest.isEqual(state.toByteArray(), challenge.state.toByteArray()) &&
            ((url.queryParameterValues("code").size == 1 && url.queryParameterValues("error").isEmpty()) ||
                (url.queryParameterValues("error").size == 1 && url.queryParameterValues("code").isEmpty()))
    }

    fun callbackCode(rawUrl: String, challenge: AntigravityOAuthChallenge): String {
        if (!matchesCallback(rawUrl, challenge)) throw AntigravityAuthorizationException("Google 授权校验失败，请重新连接")
        val url = rawUrl.toHttpUrl()
        if (url.queryParameter("error") != null) throw AntigravityAuthorizationException("Google 授权未完成，请重新连接")
        return url.queryParameter("code")?.takeIf { it.isNotBlank() }
            ?: throw AntigravityAuthorizationException("Google 未返回授权码，请重新连接")
    }
}

class AntigravityOAuthChallenge internal constructor(
    val authorizationUrl: String,
    val redirectUri: String,
    internal val verifier: String,
    internal val state: String,
) {
    override fun toString(): String = "AntigravityOAuthChallenge([REDACTED])"
}

class AntigravityAuthorizationException(message: String) : Exception(message)
