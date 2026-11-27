package funapp.ctrlcv.zhiyu.core.network.oauth

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.PermissionDeniedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GitHubDeviceCodeTest {
    private lateinit var server: MockWebServer
    private lateinit var client: OAuthTokenClient
    private var clock = 1000L
    private val pauses = mutableListOf<Long>()

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        client = OAuthTokenClient(OkHttpClient(), OAuthEndpoints(
            gitHubDevice = server.url("/login/device/code").toString(),
            gitHubToken = server.url("/login/oauth/access_token").toString(),
            gitHubUser = server.url("/user").toString(),
            gitHubClientId = "project-client",
        ), now = { clock }, pause = { pauses += it; clock += it })
    }

    @After fun tearDown() { server.shutdown() }

    @Test fun `receipt uses the original GitHub verification site and a project client`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{
            "user_code":"EXAM-PLE1","device_code":"sample-device",
            "verification_uri":"https://github.com/login/device","expires_in":899,"interval":5
        }"""))
        val challenge = client.requestGitHubDeviceCode()
        assertEquals(OAuthProtocol.GITHUB_VERIFY, challenge.verificationUrl)
        assertEquals(900_000L, challenge.expiresAt)
        assertFalse(challenge.toString().contains("sample-device"))
        val request = server.takeRequest()
        assertEquals("/login/device/code", request.path)
        assertEquals("application/json", request.getHeader("Accept"))
        assertEquals("client_id=project-client&scope=read%3Auser", request.body.readUtf8())
    }

    @Test fun `receipt from another verification origin is rejected`() {
        server.enqueue(MockResponse().setBody("""{
            "user_code":"EXAM-PLE1","device_code":"sample-device","verification_uri":"https://example.invalid/login/device"
        }"""))
        assertThrows(ApiStructureChangedException::class.java) {
            runBlocking { client.requestGitHubDeviceCode() }
        }
    }

    @Test fun `authorization respects wait instructions and verifies the signed in GitHub user`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"error":"authorization_pending"}"""))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"slow_down","interval":15}"""))
        server.enqueue(MockResponse().setBody("""{"access_token":"access","refresh_token":"refresh","expires_in":28800}"""))
        server.enqueue(MockResponse().setBody("""{"id":123456,"login":"octocat"}"""))

        val credential = client.awaitGitHubAuthorization(challenge())

        assertEquals(listOf(5000L, 5000L, 15_000L), pauses)
        assertEquals("123456", credential.providerAccountId)
        assertEquals("octocat", credential.displayName)
        assertEquals("refresh", credential.refreshToken)
        assertEquals(clock + 28_800_000L, credential.expiresAt)
        repeat(3) {
            val request = server.takeRequest()
            assertEquals("/login/oauth/access_token", request.path)
            assertEquals(
                "client_id=project-client&device_code=sample-device&grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code",
                request.body.readUtf8(),
            )
        }
        val identity = server.takeRequest()
        assertEquals("/user", identity.path)
        assertEquals("Bearer access", identity.getHeader("Authorization"))
    }

    @Test fun `403 without an explicit protocol error stops instead of continuing to poll`() {
        server.enqueue(MockResponse().setResponseCode(403).setBody("{}"))
        assertThrows(PermissionDeniedException::class.java) {
            runBlocking { client.awaitGitHubAuthorization(challenge()) }
        }
        assertEquals(1, server.requestCount)
    }

    @Test fun `explicit denial or expiry ends the device flow`() {
        server.enqueue(MockResponse().setBody("""{"error":"access_denied"}"""))
        assertThrows(GitHubDeviceAuthorizationException::class.java) {
            runBlocking { client.awaitGitHubAuthorization(challenge()) }
        }
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":"expired_token"}"""))
        assertThrows(OAuthLoginExpiredException::class.java) {
            runBlocking { client.awaitGitHubAuthorization(challenge()) }
        }
        assertEquals(2, server.requestCount)
    }

    @Test fun `token rotation keeps the verified account identity and omits the app secret`() = runBlocking {
        server.enqueue(MockResponse().setBody("""{"access_token":"new-access","refresh_token":"new-refresh","expires_in":28800}"""))
        val result = client.refresh(Platform.GITHUB_COPILOT, stored())
        assertEquals("new-access", result.accessToken)
        assertEquals("new-refresh", result.refreshToken)
        assertEquals(stored().providerAccountId, result.providerAccountId)
        assertEquals(stored().displayName, result.displayName)
        assertEquals(
            "client_id=project-client&grant_type=refresh_token&refresh_token=old-refresh",
            server.takeRequest().body.readUtf8(),
        )
    }

    @Test fun `revoked refresh token becomes a reconnectable session failure`() {
        server.enqueue(MockResponse().setBody("""{"error":"bad_refresh_token"}"""))
        assertThrows(SessionExpiredException::class.java) {
            runBlocking { client.refresh(Platform.GITHUB_COPILOT, stored()) }
        }
        assertEquals(1, server.requestCount)
    }

    private fun challenge() = DeviceCodeChallenge(
        "EXAM-PLE1", "sample-device", OAuthProtocol.GITHUB_VERIFY, 5, 900_000L,
    )

    private fun stored() = OAuthCredential("old-access", "old-refresh", clock + 10, "123456", "octocat")
}
