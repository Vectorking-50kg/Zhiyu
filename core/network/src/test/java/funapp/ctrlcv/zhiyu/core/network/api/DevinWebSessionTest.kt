package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test

class DevinWebSessionTest {
    @Test fun `only the exact secure provider origin is eligible`() {
        assertTrue(DevinWebSession.isDevinPage("https://app.devin.ai/org/acme/settings/usage"))
        listOf(
            "http://app.devin.ai",
            "https://app.devin.ai:8443",
            "https://app.devin.ai.example.invalid",
            "https://app.devin.ai@example.invalid",
            "https://unexpected@app.devin.ai",
            "https://accounts.google.com",
            null,
        ).forEach { assertFalse(DevinWebSession.isDevinPage(it)) }
    }

    @Test fun `WebView JSON string yields the browser session and internal organization`() {
        val json = """{"accessToken":"auth1_example-token","organizationId":"org_acme"}"""
        val session = checkNotNull(DevinWebSession.fromWebViewResult(Gson().toJson(json)))
        assertEquals("auth1_example-token", session.accessToken)
        assertEquals("org_acme", session.organizationId)
        assertFalse(session.toString().contains("example-token"))
    }

    @Test fun `signed-in homepage can supply a session before any organization is saved`() {
        val json = """{"accessToken":"auth1_example-token","organizationId":null}"""
        val session = checkNotNull(DevinWebSession.fromWebViewResult(Gson().toJson(json)))
        assertEquals("auth1_example-token", session.accessToken)
        assertNull(session.organizationId)
    }

    @Test fun `unrecognizable browser results cannot become valid credentials`() {
        listOf(
            "null", "{}", "[true]", "\"not a session\"",
            """{"accessToken":"google-token","organizationId":"org_example"}""",
            """{"accessToken":"auth1_example-token","organizationId":"org_example/other"}""",
        ).forEach { assertNull(DevinWebSession.fromWebViewResult(it)) }
    }
}
