package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.RateLimitException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.toUsageFailure
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AntigravityApiClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: AntigravityApiClient
    private val summary = """{"groups":[{"buckets":[{"bucketId":"gemini-5h","remainingFraction":0.6}]}]}"""
    private val models = """{"models":{"gemini-pro":{"displayName":"Gemini Pro","quotaInfo":{"remainingFraction":0.2}}}}"""

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        api = AntigravityApiClient(OkHttpClient(), AntigravityEndpoints(
            server.url("/summary").toString(), server.url("/models").toString(), server.url("/plan").toString(),
        ), now = { 1_000L })
    }
    @After fun tearDown() { server.shutdown() }

    @Test fun `reads summary with bearer and plan metadata in separate read-only requests`() = runBlocking {
        server.enqueue(MockResponse().setBody(summary))
        server.enqueue(MockResponse().setBody("""{"paidTier":{"name":"Google AI Pro"}}"""))
        val usage = api.getUsage("access")
        assertEquals("Google AI Pro", usage.planLabel)
        assertEquals(40f, usage.items.single().percent, 0.01f)
        val quota = server.takeRequest()
        assertEquals("/summary", quota.path)
        assertEquals("POST", quota.method)
        assertEquals("Bearer access", quota.getHeader("Authorization"))
        assertEquals("{}", quota.body.readUtf8())
        val plan = server.takeRequest()
        assertEquals("/plan", plan.path)
        assertTrue(plan.body.readUtf8().contains("ANTIGRAVITY"))
    }

    @Test fun `free-tier subscription-required response falls back to actual model quotas`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"details":[{"reason":"SUBSCRIPTION_REQUIRED"}]}}"""))
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody("{}"))
        val usage = api.getUsage("access")
        assertEquals("Gemini Pro", usage.items.single().label)
        assertEquals(80f, usage.items.single().percent, 0.01f)
        assertNull(usage.items.single().windowDurationSeconds)
        assertEquals(listOf("/summary", "/models", "/plan"), (1..3).map { server.takeRequest().path })
    }

    @Test fun `unknown summary shape falls back rather than showing a blank card`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"groups":[{"buckets":[{"bucketId":"new-window"}]}]}"""))
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setResponseCode(503))
        val usage = api.getUsage("access")
        assertEquals("Gemini Pro", usage.items.single().label)
        assertNull(usage.planLabel)
    }

    @Test fun `unavailable summary endpoint can use models`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody(models))
        server.enqueue(MockResponse().setBody("{}"))
        assertEquals(1, api.getUsage("access").items.size)
    }

    @Test fun `401 and generic 403 do not trigger fallback or hide credential errors`() {
        for ((status, type) in listOf(401 to SessionExpiredException::class.java, 403 to PermissionDeniedException::class.java)) {
            server.enqueue(MockResponse().setResponseCode(status).setBody("{}"))
            assertThrows(type) { runBlocking { api.getUsage("access") } }
            assertEquals("/summary", server.takeRequest().path)
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `rate limit and server errors retain their error classification`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60"))
        val limit = assertThrows(RateLimitException::class.java) { runBlocking { api.getUsage("access") } }
        assertEquals(60L, limit.retryAfterSeconds)
        server.enqueue(MockResponse().setResponseCode(503))
        val failure = runCatching { runBlocking { api.getUsage("access") } }.exceptionOrNull()!!.toUsageFailure()
        assertEquals(UsageFailureKind.SERVER, failure.kind)
        assertEquals(2, server.requestCount)
    }

    @Test fun `redirect never forwards bearer to the destination`() {
        server.enqueue(MockResponse().setResponseCode(307).setHeader("Location", server.url("/unexpected")))
        assertTrue(runCatching { runBlocking { api.getUsage("access") } }.isFailure)
        assertEquals(1, server.requestCount)
    }

    @Test fun `invalid summary and models cannot validate a login`() {
        server.enqueue(MockResponse().setBody("{}"))
        server.enqueue(MockResponse().setBody("{}"))
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { api.getUsage("access") } }
        assertEquals(2, server.requestCount)
    }
}
