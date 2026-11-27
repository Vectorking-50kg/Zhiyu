package funapp.ctrlcv.zhiyu.core.network.oauth

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class AntigravityOAuthTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: OAuthTokenClient
    private val clientId = "test-google-client"
    private val challenge = AntigravityOAuth.challenge("http://127.0.0.1:52345/oauth2callback", clientId)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = OAuthTokenClient(OkHttpClient(), OAuthEndpoints(
            googleToken = server.url("/token").toString(), googleUser = server.url("/user").toString(),
            googleClientId = clientId, googleClientSecret = "test-client-secret",
        ), now = { 1000L })
    }
    @After fun tearDown() { server.shutdown() }

    private fun callback() = challenge.redirectUri.toHttpUrl().newBuilder()
        .addQueryParameter("state", challenge.state).addQueryParameter("code", "login-code").build().toString()
    private fun response(refresh: String = ",\"refresh_token\":\"refresh\"") =
        MockResponse().setBody("""{"access_token":"access","token_type":"Bearer","expires_in":3600$refresh}""")

    @Test fun `authorization requests offline grant with random state and PKCE`() {
        val url = challenge.authorizationUrl.toHttpUrl()
        assertEquals("accounts.google.com", url.host)
        assertEquals(clientId, url.queryParameter("client_id"))
        assertEquals(challenge.redirectUri, url.queryParameter("redirect_uri"))
        assertEquals("offline", url.queryParameter("access_type"))
        assertEquals("consent", url.queryParameter("prompt"))
        assertTrue(url.queryParameter("scope")!!.split(" ").contains("https://www.googleapis.com/auth/aicode"))
        val proof = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(challenge.verifier.toByteArray(Charsets.US_ASCII)))
        assertEquals(proof, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertNotEquals(challenge.state, tokens.newAntigravityChallenge(challenge.redirectUri).state)
        assertFalse(challenge.toString().contains(challenge.verifier))
    }

    @Test fun `embedded challenge uses an intercepted callback and keeps validation separate from routing`() {
        val embedded = tokens.newAntigravityChallenge()
        assertEquals("http://127.0.0.1:8089/oauth2callback", embedded.redirectUri)
        val invalid = embedded.redirectUri + "?code=code&state=wrong"
        assertTrue(tokens.isAntigravityCallback(invalid, embedded))
        assertThrows(AntigravityAuthorizationException::class.java) { AntigravityOAuth.callbackCode(invalid, embedded) }
        assertFalse(tokens.isAntigravityCallback(invalid.replace(":8089", ":8088"), embedded))
        assertFalse(tokens.isAntigravityCallback(invalid.replace("/oauth2callback", "/oauth2callback-other"), embedded))
    }

    @Test fun `callbacks require exact origin path state and a single code`() {
        assertEquals("login-code", AntigravityOAuth.callbackCode(callback(), challenge))
        for (url in listOf(
            callback().replace("127.0.0.1", "localhost"), callback().replace(":52345", ":52346"),
            callback().replace("/oauth2callback", "/other"), callback().replace(challenge.state, "wrong"),
            callback() + "&state=${challenge.state}", callback() + "&code=other", callback() + "&error=access_denied",
            callback() + "#fragment", callback().replace("http://", "https://"),
        )) {
            assertThrows(AntigravityAuthorizationException::class.java) { AntigravityOAuth.callbackCode(url, challenge) }
        }
    }

    @Test fun `denial consumes a matching callback but never exchanges it`() {
        val denied = challenge.redirectUri.toHttpUrl().newBuilder().addQueryParameter("state", challenge.state)
            .addQueryParameter("error", "access_denied").build().toString()
        assertTrue(AntigravityOAuth.matchesCallback(denied, challenge))
        assertThrows(AntigravityAuthorizationException::class.java) { runBlocking { tokens.exchangeAntigravity(challenge, denied) } }
        assertEquals(0, server.requestCount)
    }

    @Test fun `exchanges code then verifies stable Google identity before returning credentials`() = runBlocking {
        server.enqueue(response())
        server.enqueue(MockResponse().setBody("""{"id":"google-user-1","email":"user@example.com"}"""))
        val credential = tokens.exchangeAntigravity(challenge, callback())
        assertEquals("google-user-1", credential.providerAccountId)
        assertEquals("user@example.com", credential.displayName)
        assertEquals(3_601_000L, credential.expiresAt)
        val exchange = server.takeRequest()
        val form = ("http://localhost/?" + exchange.body.readUtf8()).toHttpUrl()
        assertEquals("authorization_code", form.queryParameter("grant_type"))
        assertEquals("login-code", form.queryParameter("code"))
        assertEquals(challenge.verifier, form.queryParameter("code_verifier"))
        assertEquals(challenge.redirectUri, form.queryParameter("redirect_uri"))
        assertEquals(clientId, form.queryParameter("client_id"))
        assertEquals("test-client-secret", form.queryParameter("client_secret"))
        assertEquals("/user", server.takeRequest().path)
    }

    @Test fun `missing identity or renewable credential cannot complete sign-in`() {
        server.enqueue(response())
        server.enqueue(MockResponse().setBody("""{"email":"unbound@example.com"}"""))
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { tokens.exchangeAntigravity(challenge, callback()) } }
        server.enqueue(response(refresh = ""))
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { tokens.exchangeAntigravity(challenge, callback()) } }
        assertEquals(3, server.requestCount)
    }

    @Test fun `refresh preserves identity and refresh token when Google omits rotation`() = runBlocking {
        val previous = OAuthCredential("old", "original-refresh", 0, "google-user-1", "user@example.com")
        server.enqueue(response(refresh = ""))
        val refreshed = tokens.refresh(Platform.ANTIGRAVITY, previous)
        assertEquals("original-refresh", refreshed.refreshToken)
        assertEquals(previous.providerAccountId, refreshed.providerAccountId)
        assertEquals(previous.displayName, refreshed.displayName)
        assertEquals(3_601_000L, refreshed.expiresAt)
        val form = ("http://localhost/?" + server.takeRequest().body.readUtf8()).toHttpUrl()
        assertEquals("original-refresh", form.queryParameter("refresh_token"))
        assertEquals("refresh_token", form.queryParameter("grant_type"))
    }

    @Test fun `session manager persists rotation and retries an expired access token only once`() = runBlocking {
        var stored = OAuthCredential("old", "original-refresh", 3_600_000, "google-user-1")
        var fetches = 0
        var writes = 0
        server.enqueue(response(refresh = ",\"refresh_token\":\"rotated-refresh\""))
        val manager = OAuthSessionManager(tokens, { _, _ -> stored }, { _, _, expected, replacement ->
            assertEquals(stored, expected)
            stored = replacement
            writes++
            true
        }, { platform, credential ->
            fetches++
            if (credential.accessToken == "old") throw SessionExpiredException(platform)
            UsageInfo(platform, emptyList())
        }, now = { 1000L })
        val usage = manager.getUsage(Platform.ANTIGRAVITY, "local-id")
        assertEquals("google-user-1", usage.providerAccountId)
        assertEquals("local-id", usage.accountId)
        assertEquals("rotated-refresh", stored.refreshToken)
        assertEquals(2, fetches)
        assertEquals(1, writes)
        assertEquals(1, server.requestCount)
    }

    @Test fun `revoked Google grant requests login without overwriting saved credential`() = runBlocking {
        val stored = OAuthCredential("old", "revoked", 0, "google-user-1")
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant","error_description":"sensitive text"}"""))
        val error = runCatching { tokens.refresh(Platform.ANTIGRAVITY, stored) }.exceptionOrNull()
        assertTrue(error is SessionExpiredException)
        assertFalse(error.toString().contains("sensitive"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `missing build configuration reports a safe error before opening Google or sending credentials`() {
        val missing = OAuthTokenClient(OkHttpClient(), OAuthEndpoints(googleClientId = "", googleClientSecret = ""))
        val error = assertThrows(AntigravityAuthorizationException::class.java) { missing.newAntigravityChallenge() }
        assertTrue(error.message.orEmpty().contains("构建未配置"))
        assertEquals(0, server.requestCount)
    }
}
