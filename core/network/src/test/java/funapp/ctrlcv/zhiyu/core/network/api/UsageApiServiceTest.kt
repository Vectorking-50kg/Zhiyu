package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.Base64

class UsageApiServiceTest {
    private val server = MockWebServer()
    private val responses = mutableMapOf<String, Pair<Int, String>>()
    private lateinit var service: UsageApiService

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body) = responses[request.path] ?: (403 to "<html>Unavailable optional endpoint</html>")
                return MockResponse().setResponseCode(code).setBody(body)
            }
        }
        server.start()
        // Rewrite only in tests. Production endpoints remain fixed HTTPS provider addresses.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build()
        service = UsageApiService(client, Gson())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `session endpoint 500 never asks for a new login`() {
        responses["/api/auth/session"] = 500 to "{\"accessToken\":\"sensitive-body\"}"
        val error = assertThrows(UsageRequestException::class.java) { runBlocking { service.getChatGptUsage("fake-cookie") } }
        assertEquals(UsageFailureKind.SERVER, error.failure.kind)
        assertFalse(error.message.orEmpty().contains("sensitive-body"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `Claude validates real organization and usage before returning a session`() = runBlocking {
        responses["/api/organizations"] = 200 to """[
            {"uuid":"api-only","capabilities":["api"]},
            {"uuid":"chat-org","name":"Personal","capabilities":["chat","claude_pro"]}
        ]"""
        responses["/api/organizations/chat-org/usage"] = 200 to """{"five_hour":{"utilization":12}}"""
        val session = service.validateCookie(Platform.CLAUDE, "fake-cookie")
        assertEquals("chat-org", session.providerAccountId)
        assertEquals("chat-org", session.usage.providerAccountId)
        assertEquals("Pro", session.usage.planLabel)
        assertEquals(12f, session.usage.items.single().percent)
    }

    @Test fun `Claude empty quota cannot validate a cookie`() {
        responses["/api/organizations"] = 200 to """[{"uuid":"chat-org"}]"""
        responses["/api/organizations/chat-org/usage"] = 200 to "{}"
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { service.validateCookie(Platform.CLAUDE, "fake-cookie") } }
    }

    @Test fun `Codex OAuth sends workspace id and succeeds when optional website endpoints deny access`() = runBlocking {
        responses["/backend-api/wham/usage"] = 200 to """{"plan_type":"pro","rate_limit":{"primary_window":{"used_percent":18}}}"""
        val result = service.getCodexOAuthUsage("fake-oauth", "workspace-a")
        assertEquals("workspace-a", result.providerAccountId)
        assertEquals("Pro", result.planLabel)
        assertEquals(18f, result.items.single().percent)
        val usageRequest = server.takeRequest()
        assertEquals("/backend-api/wham/usage", usageRequest.path)
        assertEquals("Bearer fake-oauth", usageRequest.getHeader("Authorization"))
        assertEquals("workspace-a", usageRequest.getHeader("ChatGPT-Account-Id"))
        assertNull(usageRequest.getHeader("Cookie"))
    }

    @Test fun `Codex cookie validation uses account claim rather than user id`() = runBlocking {
        val claims = """{"https://api.openai.com/auth":{"chatgpt_account_id":"workspace-a"}}"""
        val token = "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())}.signature"
        responses["/api/auth/session"] = 200 to Gson().toJson(mapOf("accessToken" to token, "user" to mapOf("id" to "user-not-workspace", "email" to "example@example.invalid")))
        responses["/backend-api/wham/usage"] = 200 to """{"rate_limit":{"primary_window":{"used_percent":18}}}"""
        val result = service.validateCookie(Platform.CHATGPT, "fake-cookie")
        assertEquals("workspace-a", result.providerAccountId)
        assertEquals("example@example.invalid", result.displayName)
        server.takeRequest()
        assertEquals("workspace-a", server.takeRequest().getHeader("ChatGPT-Account-Id"))
    }

    @Test fun `Codex resolves unknown workspace before usage and applies same identity to snapshot`() = runBlocking {
        responses["/api/auth/session"] = 200 to """{"accessToken":"opaque-fake-token","user":{"id":"not-a-workspace"}}"""
        responses["/backend-api/accounts/check/v4-2023-04-27"] = 200 to """{
            "account_ordering":["workspace-selected"],
            "accounts":{"workspace-selected":{"account":{"account_id":"workspace-selected","plan_type":"plus"}}}
        }"""
        responses["/backend-api/wham/usage"] = 200 to """{"rate_limit":{"primary_window":{"used_percent":22}}}"""
        val result = service.validateCookie(Platform.CHATGPT, "fake-cookie")
        assertEquals("workspace-selected", result.providerAccountId)
        server.takeRequest() // session
        assertEquals("/backend-api/accounts/check/v4-2023-04-27", server.takeRequest().path)
        assertEquals("workspace-selected", server.takeRequest().getHeader("ChatGPT-Account-Id"))
    }

    @Test fun `Codex quota without verifiable identity cannot validate a cookie`() {
        responses["/api/auth/session"] = 200 to """{"accessToken":"opaque-fake-token","user":{"id":"not-a-workspace"}}"""
        responses["/backend-api/wham/usage"] = 200 to """{"rate_limit":{"primary_window":{"used_percent":22}}}"""
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { service.validateCookie(Platform.CHATGPT, "fake-cookie") } }
    }

    @Test fun `matching default account keeps renewal and reset card detail`() = runBlocking {
        responses["/backend-api/accounts/check/v4-2023-04-27"] = 200 to """{
            "accounts":{"default":{"account":{"account_id":"workspace-a","plan_type":"plus"},
            "entitlement":{"has_active_subscription":true,"renews_at":"2099-01-01T00:00:00Z"}}}
        }"""
        responses["/backend-api/wham/usage"] = 200 to """{"rate_limit":{"primary_window":{"used_percent":18}}}"""
        responses["/backend-api/wham/rate-limit-reset-credits"] = 200 to """{
            "available_count":1,"credits":[{"status":"available","expires_at":"2099-01-01T00:00:00Z"}]
        }"""
        val result = service.getCodexOAuthUsage("fake-oauth", "workspace-a")
        assertEquals("Plus", result.planLabel)
        assertEquals("续订时间", result.items.last().label)
        assertEquals(1, result.resetCredits?.credits?.size)
        assertEquals("workspace-a", result.providerAccountId)
    }

    @Test fun `subscription from a different default account is not attached to the requested account`() = runBlocking {
        responses["/backend-api/accounts/check/v4-2023-04-27"] = 200 to """{
            "accounts":{"default":{"account":{"account_id":"workspace-other","plan_type":"pro"}}}
        }"""
        responses["/backend-api/wham/usage"] = 200 to """{"plan_type":"plus","rate_limit":{"primary_window":{"used_percent":18}}}"""
        val result = service.getCodexOAuthUsage("fake-oauth", "workspace-a")
        assertEquals("Plus", result.planLabel)
        assertEquals("workspace-a", result.providerAccountId)
    }
}
