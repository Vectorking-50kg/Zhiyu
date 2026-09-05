package funapp.ctrlcv.zhiyu.core.network.oauth

import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class OAuthSessionManagerTest {
    private lateinit var server: MockWebServer
    private lateinit var tokens: OAuthTokenClient
    @Volatile private var stored: OAuthCredential? = null
    private var writes = 0
    private val provider = Platform.CLAUDE
    private val snapshot = UsageInfo(provider, emptyList(), providerAccountId = "org-one")

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        tokens = OAuthTokenClient(OkHttpClient(), OAuthEndpoints(claudeToken = server.url("/token").toString()), now = { 1_000L })
        stored = OAuthCredential("old-access", "old-refresh", 3_600_000, "org-one")
        writes = 0
    }
    @After fun tearDown() { server.shutdown() }

    private fun manager(fetch: suspend (Platform, OAuthCredential) -> UsageInfo): OAuthSessionManager =
        OAuthSessionManager(tokens, { _, _ -> stored }, { _, _, expected, replacement ->
            if (stored != expected) false else { stored = replacement; writes++; true }
        }, fetch, now = { 1_000L })

    @Test fun `fresh tokens fetch usage without a refresh request`() = runBlocking {
        val info = manager { _, credential -> assertEquals("old-access", credential.accessToken); snapshot }
            .getUsage(provider, "local-one")
        assertEquals("local-one", info.accountId)
        assertEquals(0, server.requestCount)
        assertEquals(0, writes)
    }

    @Test fun `expired token rotates once before usage and stores the new refresh token`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        server.enqueue(rotated())
        manager { _, credential ->
            assertEquals("new-access", credential.accessToken)
            assertEquals("new-refresh", stored?.refreshToken)
            snapshot
        }.getUsage(provider, "local-one")
        val request = server.takeRequest()
        assertTrue(request.body.readUtf8().contains("old-refresh"))
        assertEquals(1, writes)
    }

    @Test fun `401 causes only one refresh and one retry`() = runBlocking {
        server.enqueue(rotated())
        var fetches = 0
        val result = runCatching { manager { _, _ -> fetches++; throw SessionExpiredException(provider) }.getUsage(provider, "one") }
        assertTrue(result.exceptionOrNull() is SessionExpiredException)
        assertEquals(2, fetches)
        assertEquals(1, server.requestCount)
    }

    @Test fun `403 does not refresh or erase a valid credential`() = runBlocking {
        val before = stored
        val result = runCatching { manager { _, _ -> throw PermissionDeniedException(provider) }.getUsage(provider, "one") }
        assertTrue(result.exceptionOrNull() is PermissionDeniedException)
        assertEquals(before, stored)
        assertEquals(0, server.requestCount)
    }

    @Test fun `invalid grant is auth required and never retries the old refresh token`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        val before = stored
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"invalid_grant"}"""))
        val result = runCatching { manager { _, _ -> fail("Usage must not run after invalid grant"); snapshot }.getUsage(provider, "one") }
        assertTrue(result.exceptionOrNull() is SessionExpiredException)
        assertEquals(1, server.requestCount)
        assertEquals(before, stored)
        assertEquals(0, writes)
    }

    @Test fun `rotated refresh token is kept when the following usage request fails`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        server.enqueue(rotated())
        val result = runCatching { manager { _, _ -> throw IOException("offline") }.getUsage(provider, "one") }
        assertTrue(result.exceptionOrNull() is IOException)
        assertEquals("new-refresh", stored?.refreshToken)
        assertEquals(1, writes)
    }

    @Test fun `disk persistence failure stops before fetching usage with a rotated token`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        server.enqueue(rotated())
        var fetched = false
        val manager = OAuthSessionManager(tokens, { _, _ -> stored }, { _, _, _, _ ->
            throw IOException("Cannot persist session")
        }, { _, _ -> fetched = true; snapshot }, now = { 1_000L })
        val result = runCatching { manager.getUsage(provider, "one") }
        assertTrue(result.exceptionOrNull() is IOException)
        assertFalse(fetched)
    }

    @Test fun `removed session cannot be restored by an in-flight refresh`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        server.enqueue(rotated().setBodyDelay(200, TimeUnit.MILLISECONDS))
        var reachedUsage = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            manager { _, _ -> reachedUsage = true; snapshot }.getUsage(provider, "one")
        }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        stored = null
        job.join()
        assertTrue(job.isCancelled)
        assertFalse(reachedUsage)
        assertNull(stored)
        assertEquals(0, writes)
    }

    @Test fun `cancellation after refresh was sent preserves rotation before stopping usage`() = runBlocking {
        stored = stored!!.copy(expiresAt = 0)
        server.enqueue(rotated().setBodyDelay(200, TimeUnit.MILLISECONDS))
        var fetched = false
        val job = launch(start = CoroutineStart.UNDISPATCHED) { manager { _, _ -> fetched = true; snapshot }.getUsage(provider, "one") }
        assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
        job.cancel()
        job.join()
        assertFalse(fetched)
        assertEquals(1, writes)
        assertEquals("new-refresh", stored?.refreshToken)
    }

    private fun rotated() = MockResponse().setBody("""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":3600}""")
}
