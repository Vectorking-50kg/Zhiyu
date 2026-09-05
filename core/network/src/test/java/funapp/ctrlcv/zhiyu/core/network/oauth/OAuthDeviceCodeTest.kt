package funapp.ctrlcv.zhiyu.core.network.oauth

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class OAuthDeviceCodeTest {
    private lateinit var server: MockWebServer
    private var currentTime = 1_000L
    private val pauses = mutableListOf<Long>()
    private lateinit var client: OAuthTokenClient

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        currentTime = 1_000L
        pauses.clear()
        client = OAuthTokenClient(OkHttpClient(), OAuthEndpoints(
            codexDevice = server.url("/device").toString(),
            codexPoll = server.url("/poll").toString(),
            codexToken = server.url("/token").toString()
        ), now = { currentTime }, pause = { pauses += it; currentTime += it })
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `official device response without expires_in still has a bounded timeout`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_code":"ABCD-1234","device_auth_id":"challenge","interval":"5"}"""))
        val challenge = client.requestCodexDeviceCode()
        assertEquals("https://auth.openai.com/codex/device", challenge.verificationUrl)
        assertEquals(901_000L, challenge.expiresAt)
        assertFalse(challenge.toString().contains("ABCD"))
    }

    @Test fun `server-supplied verification link cannot send the user to another origin`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"user_code":"ABCD-1234","device_auth_id":"challenge","verification_uri":"https://evil.example/"}"""))
        val result = runCatching { client.requestCodexDeviceCode() }
        assertTrue(result.exceptionOrNull() is ApiStructureChangedException)
    }

    @Test fun `pending and slow_down delay polling then exchange code with server verifier`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"slow_down"}"""))
        server.enqueue(MockResponse().setBody("""{"authorization_code":"code","code_verifier":"verifier"}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"access","refresh_token":"refresh","account_id":"account","expires_in":3600}"""))
        val result = client.awaitCodexAuthorization(DeviceCodeChallenge("ABCD", "challenge", intervalSeconds = 5, expiresAt = 900_000))
        assertEquals(listOf(5_000L, 5_000L, 10_000L), pauses)
        assertEquals("account", result.providerAccountId)
        repeat(3) { assertEquals("/poll", server.takeRequest().path) }
        val exchange = server.takeRequest()
        assertEquals("/token", exchange.path)
        val form = exchange.body.readUtf8()
        assertTrue(form.contains("code_verifier=verifier"))
        assertTrue(form.contains("grant_type=authorization_code"))
    }

    @Test fun `expired device challenge stops without sending a token request`() = runBlocking {
        val result = runCatching { client.awaitCodexAuthorization(DeviceCodeChallenge("ABCD", "challenge", intervalSeconds = 5, expiresAt = 2_000)) }
        assertTrue(result.exceptionOrNull() is OAuthLoginExpiredException)
        assertEquals(0, server.requestCount)
    }

    @Test fun `Retry-After beyond a minute is respected up to challenge expiry`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "99999"))
        val result = runCatching { client.awaitCodexAuthorization(
            DeviceCodeChallenge("ABCD", "challenge", intervalSeconds = 5, expiresAt = 301_000)
        ) }
        assertTrue(result.exceptionOrNull() is OAuthLoginExpiredException)
        assertEquals(listOf(5_000L, 120_000L, 175_000L), pauses)
        assertEquals(2, server.requestCount)
    }

    @Test fun `explicit access denied is terminal even with a pending status code`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":"access_denied"}"""))
        val result = runCatching { client.awaitCodexAuthorization(DeviceCodeChallenge("ABCD", "challenge", intervalSeconds = 5, expiresAt = 900_000)) }
        assertTrue(result.exceptionOrNull() is SessionExpiredException)
        assertEquals(1, server.requestCount)
    }

    @Test fun `canceling an initial device request returns without an authorization candidate`() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        var completed = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            client.requestCodexDeviceCode()
            completed = true
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(completed)
    }
}
