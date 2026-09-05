package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.*
import funapp.ctrlcv.zhiyu.core.network.di.NetworkModule
import funapp.ctrlcv.zhiyu.core.network.interceptor.CookieInterceptor
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class UsageHttpResponseTest {
    private val server = MockWebServer()
    private val client = OkHttpClient()
    @Before fun setUp() { server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `401 means expired but Cloudflare 403 does not`() {
        assertTrue(failure(401) is SessionExpiredException)
        assertTrue(failure(403, "<html>Cloudflare challenge</html>") is PermissionDeniedException)
        assertTrue(failure(403, "{\"error\":{\"type\":\"authentication_error\"}}") is SessionExpiredException)
        assertTrue(failure(403, "{\"error\":{\"type\":\"insufficient_scope\"}}") is PermissionDeniedException)
    }

    @Test fun `rate limit preserves Retry After seconds and HTTP date`() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        val first = readFailure()
        assertEquals(120L, (first as RateLimitException).retryAfterSeconds)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "Sun, 6 Sep 2026 00:02:00 GMT"))
        val second = readFailure(Instant.parse("2026-09-06T00:00:00Z").toEpochMilli())
        assertEquals(120L, (second as RateLimitException).retryAfterSeconds)
    }

    @Test fun `server failure is retryable and never includes body credentials`() {
        val error = failure(503, "{\"access_token\":\"super-secret\"}") as UsageRequestException
        assertEquals(UsageFailureKind.SERVER, error.failure.kind)
        assertEquals(503, error.failure.httpStatus)
        assertTrue(error.failure.retryable)
        assertFalse(error.toString().contains("super-secret"))
    }

    @Test fun `empty successful response is a parse failure`() {
        assertTrue(failure(200) is ApiStructureChangedException)
    }

    @Test fun `shared production client does not install an HTTP logging interceptor`() {
        val production = NetworkModule.provideOkHttpClient()
        assertTrue(production.interceptors.isEmpty())
        assertTrue(production.networkInterceptors.isEmpty())
    }

    @Test fun `network interceptor does not emit login events before auth recovery`() = runBlocking {
        val bus = SessionEventBus()
        val emitted = async(start = CoroutineStart.UNDISPATCHED) { bus.events.first() }
        server.enqueue(MockResponse().setResponseCode(401))
        val isolated = OkHttpClient.Builder().addInterceptor(CookieInterceptor(bus)).build()
        withContext(Dispatchers.IO) {
            isolated.newCall(Request.Builder().url(server.url("/usage")).tag(Platform::class.java, Platform.CLAUDE).build())
                .execute().close()
        }
        yield()
        assertFalse(emitted.isCompleted)
        emitted.cancelAndJoin()
    }

    private fun failure(code: Int, body: String = ""): Throwable {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
        return readFailure()
    }

    private fun readFailure(nowMillis: Long = 0): Throwable = client.newCall(Request.Builder().url(server.url("/usage")).build())
        .execute().use { response ->
            assertThrows(Exception::class.java) { UsageHttpResponse.read(response, Platform.CHATGPT, nowMillis) }
        }
}
