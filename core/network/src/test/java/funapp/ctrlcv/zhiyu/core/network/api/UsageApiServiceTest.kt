package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.RateLimitException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import funapp.ctrlcv.zhiyu.core.domain.model.messageFor
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
    private val responseHeaders = mutableMapOf<String, Map<String, String>>()
    private lateinit var service: UsageApiService

    @Before fun setUp() {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val (code, body) = responses[request.path] ?: (403 to "<html>Unavailable optional endpoint</html>")
                return MockResponse().setResponseCode(code).setBody(body).apply {
                    responseHeaders[request.path]?.forEach { (name, value) -> setHeader(name, value) }
                }
            }
        }
        server.start()
        // Rewrite only in tests. Production endpoints remain fixed HTTPS provider addresses.
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val original = chain.request()
            val url = server.url(original.url.encodedPath).newBuilder().encodedQuery(original.url.encodedQuery).build()
            chain.proceed(original.newBuilder().url(url).build())
        }.build()
        service = UsageApiService(client, Gson())
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `Devin homepage uses its only membership before requesting quota`() = runBlocking {
        responses["/api/organizations?membership_only=true"] =
            200 to """[{"org_id":"org-test","name":"Personal"}]"""
        responses["/api/org-test/billing/quota/usage"] = 200 to """{"weekly_percentage":34,"plan_name":"Pro"}"""
        val session = checkNotNull(DevinWebSession.fromWebViewResult("""{"accessToken":"auth1_fixture","organizationId":null}"""))

        val usage = service.getDevinWebUsage(session)

        assertEquals("org-test", usage.providerAccountId)
        assertEquals(34f, usage.items.single().percent, 0.01f)
        val memberships = server.takeRequest()
        assertEquals("GET", memberships.method)
        assertEquals("/api/organizations?membership_only=true", memberships.path)
        assertEquals("Bearer auth1_fixture", memberships.getHeader("Authorization"))
        assertNull(memberships.getHeader("x-cog-org-id"))
        val quota = server.takeRequest()
        assertEquals("GET", quota.method)
        assertEquals("/api/org-test/billing/quota/usage", quota.path)
        assertEquals("org-test", quota.getHeader("x-cog-org-id"))
        assertEquals(2, server.requestCount)
    }

    @Test fun `Devin homepage does not choose among multiple memberships`() {
        responses["/api/organizations?membership_only=true"] =
            200 to """[{"org_id":"org-one"},{"org_id":"org-two"}]"""
        val session = checkNotNull(DevinWebSession.fromWebViewResult("""{"accessToken":"auth1_fixture"}"""))

        assertThrows(DevinOrganizationSelectionRequiredException::class.java) {
            runBlocking { service.getDevinWebUsage(session) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `Devin keeps the webpage organization when already selected`() = runBlocking {
        responses["/api/org_selected/billing/quota/usage"] = 200 to """{"weekly_percentage":42,"plan_name":"Pro"}"""
        val session = checkNotNull(DevinWebSession.fromWebViewResult("""{"accessToken":"auth1_fixture","organizationId":"org_selected"}"""))

        val usage = service.getDevinWebUsage(session)

        assertEquals("org_selected", usage.providerAccountId)
        assertEquals("/api/org_selected/billing/quota/usage", server.takeRequest().path)
        assertEquals(1, server.requestCount)
    }

    @Test fun `Devin requests quota and optional plan in the same organization`() = runBlocking {
        responses["/api/org-test/billing/quota/usage"] = 200 to """{"weekly_percentage":34}"""
        responses["/api/billing/subscription"] = 200 to """{"slug":"teams-v2"}"""

        val usage = service.getManualUsage(Platform.DEVIN, "Authorization: Bearer fake-token", " org-test ")

        assertEquals("org-test", usage.providerAccountId)
        assertEquals("Teams", usage.planLabel)
        assertEquals(34f, usage.items.single().percent, 0.01f)
        val requests = listOf(server.takeRequest(), server.takeRequest())
        assertEquals(listOf("/api/org-test/billing/quota/usage", "/api/billing/subscription"), requests.map { it.path })
        requests.forEach { request ->
            assertEquals("GET", request.method)
            assertEquals("Bearer fake-token", request.getHeader("Authorization"))
            assertEquals("org-test", request.getHeader("x-cog-org-id"))
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test fun `Devin keeps quota when the optional plan request fails`() = runBlocking {
        responses["/api/org-test/billing/quota/usage"] = 200 to """{"weekly_percentage":34}"""
        responses["/api/billing/subscription"] = 503 to "{}"

        val usage = service.getManualUsage(Platform.DEVIN, "fake-token", "org-test")

        assertEquals(34f, usage.items.single().percent, 0.01f)
        assertNull(usage.planLabel)
    }

    @Test fun `invalid Devin organization is rejected before any network request`() {
        assertThrows(PermissionDeniedException::class.java) {
            runBlocking { service.getManualUsage(Platform.DEVIN, "fake-token", "org-test/other") }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun `Copilot queries the GitHub account using a read-only token request`() = runBlocking {
        responses["/copilot_internal/user"] = 200 to """{"copilot_plan":"individual","quota_snapshots":{
            "premium_interactions":{"entitlement":300,"remaining":275}
        }}"""

        val usage = service.getManualUsage(Platform.GITHUB_COPILOT, "gho_example")

        assertEquals("Pro", usage.planLabel)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/copilot_internal/user", request.path)
        assertEquals("token gho_example", request.getHeader("Authorization"))
        assertNull(request.getHeader("Cookie"))
        assertEquals(1, server.requestCount)
    }

    @Test fun `Copilot distinguishes an invalid token from a GitHub rate limit`() {
        val path = "/copilot_internal/user"
        responses[path] = 401 to "{}"
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { service.getManualUsage(Platform.GITHUB_COPILOT, "gho_example") }
        }
        responses[path] = 403 to "{}"
        assertThrows(PermissionDeniedException::class.java) {
            runBlocking { service.getManualUsage(Platform.GITHUB_COPILOT, "gho_example") }
        }
        responseHeaders[path] = mapOf("Retry-After" to "60")
        val limited = assertThrows(RateLimitException::class.java) {
            runBlocking { service.getManualUsage(Platform.GITHUB_COPILOT, "gho_example") }
        }
        assertEquals(60L, limited.retryAfterSeconds)
        assertEquals(403, limited.httpStatus)
    }

    @Test fun `Devin and Copilot requests do not follow redirects`() {
        val requests = mapOf(
            Platform.DEVIN to "/api/org-test/billing/quota/usage",
            Platform.GITHUB_COPILOT to "/copilot_internal/user",
        )
        requests.forEach { (platform, path) ->
            responses[path] = 302 to ""
            responseHeaders[path] = mapOf("Location" to "/unexpected")
            assertThrows(UsageRequestException::class.java) {
                runBlocking { service.getManualUsage(platform, "fake-token", "org-test") }
            }
            assertEquals(path, server.takeRequest().path)
        }
        assertEquals(requests.size, server.requestCount)
    }

    @Test fun `Go validates a subscription using one read only Bearer request`() = runBlocking {
        responses["/zen/go/v1/usage"] = 200 to """{"usage":{
            "rolling":{"status":"ok","percent":20},
            "weekly":{"status":"ok","percent":35},
            "monthly":{"status":"ok","percent":50}
        }}"""
        val session = service.validateCookie(Platform.OPENCODE_GO, "fake-api-key")
        assertEquals(Platform.OPENCODE_GO, session.usage.platform)
        assertEquals("Go", session.usage.planLabel)
        assertEquals(3, session.usage.items.size)
        assertEquals(1, server.requestCount)
        val request = server.takeRequest()
        assertEquals("GET", request.method)
        assertEquals("/zen/go/v1/usage", request.path)
        assertEquals("Bearer fake-api-key", request.getHeader("Authorization"))
        assertEquals("application/json", request.getHeader("Accept"))
        assertNull(request.getHeader("Cookie"))
    }

    @Test fun `Go empty quota cannot validate an API key`() {
        responses["/zen/go/v1/usage"] = 200 to "{}"
        assertThrows(ApiStructureChangedException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
    }

    @Test fun `Go invalid API key is an authentication failure`() {
        responses["/zen/go/v1/usage"] = 401 to """{"type":"error","error":{"type":"AuthError","message":"Unauthorized"}}"""
        val error = assertThrows(SessionExpiredException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
        assertEquals(Platform.OPENCODE_GO, error.platform)
    }

    @Test fun `Go missing subscription is distinct from a revoked API key`() {
        responses["/zen/go/v1/usage"] = 403 to """{"type":"error","error":{"type":"EntitlementError","message":"secret-body"}}"""
        val error = assertThrows(UsageRequestException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
        assertEquals(UsageFailureKind.SUBSCRIPTION_REQUIRED, error.failure.kind)
        assertEquals(403, error.failure.httpStatus)
        assertFalse(error.failure.requiresLogin)
        assertFalse(error.failure.retryable)
        assertTrue(error.failure.messageFor(Platform.OPENCODE_GO).contains("Go 订阅"))
        assertFalse(error.message.orEmpty().contains("secret-body"))
    }

    @Test fun `Go generic forbidden and server errors do not claim a missing subscription`() {
        responses["/zen/go/v1/usage"] = 403 to "<html>Access denied</html>"
        assertThrows(PermissionDeniedException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
        responses["/zen/go/v1/usage"] = 503 to """{"error":{"type":"EntitlementError"}}"""
        val error = assertThrows(UsageRequestException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
        assertEquals(UsageFailureKind.SERVER, error.failure.kind)
    }

    @Test fun `Go request throttling is distinct from quota exhaustion`() {
        responses["/zen/go/v1/usage"] = 429 to "{}"
        assertThrows(RateLimitException::class.java) { runBlocking { service.getOpenCodeGoUsage("fake-key") } }
    }

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
