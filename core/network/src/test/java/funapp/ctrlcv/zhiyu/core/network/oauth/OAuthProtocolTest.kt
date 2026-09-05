package funapp.ctrlcv.zhiyu.core.network.oauth

import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
import java.util.Base64

class OAuthProtocolTest {
    @Test fun `PKCE uses S256 and asks only for quota profile scope`() {
        val challenge = OAuthProtocol.newClaudeChallenge()
        val url = challenge.authorizationUrl.toHttpUrl()
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(
            MessageDigest.getInstance("SHA-256").digest(challenge.verifier.toByteArray()))
        assertEquals(expected, url.queryParameter("code_challenge"))
        assertEquals("S256", url.queryParameter("code_challenge_method"))
        assertEquals("user:profile", url.queryParameter("scope"))
        assertNotEquals(challenge.state, OAuthProtocol.newClaudeChallenge().state)
    }

    @Test fun `callback requires exact origin path and state`() {
        val challenge = OAuthProtocol.newClaudeChallenge()
        val valid = "${OAuthProtocol.CLAUDE_CALLBACK}?code=test-code&state=${challenge.state}"
        assertEquals("test-code", OAuthProtocol.callbackCode(valid, challenge))
        assertFalse(OAuthProtocol.isClaudeCallback(valid.replace("console.anthropic.com", "console.anthropic.com.evil.example")))
        assertFalse(OAuthProtocol.isClaudeCallback(valid.replace("https:", "http:")))
        assertFalse(OAuthProtocol.isClaudeCallback(valid.replace("/callback?", "/callback/other?")))
        assertThrows(IllegalArgumentException::class.java) {
            OAuthProtocol.callbackCode(valid.replace(challenge.state, "wrong-state"), challenge)
        }
    }

    @Test fun `token rotation retains old refresh token when omitted and uses expiry`() {
        val previous = OAuthCredential("old", "old-refresh", 0, "org-one", "user")
        val payload = JsonParser.parseString("""{"access_token":"new","expires_in":3600}""").asJsonObject
        val next = OAuthProtocol.parseCredential(Platform.CLAUDE, payload, 10_000, previous)
        assertEquals("old-refresh", next.refreshToken)
        assertEquals(3_610_000L, next.expiresAt)
        assertEquals("org-one", next.providerAccountId)
        assertFalse(next.needsRefresh(3_500_000))
        assertTrue(next.needsRefresh(3_550_000))
    }

    @Test fun `refresh cannot silently switch the provider account`() {
        val old = OAuthCredential("old", "refresh", 0, "org-one")
        val json = JsonParser.parseString("""{"access_token":"new","organization":{"uuid":"org-two"}}""").asJsonObject
        assertThrows(IllegalArgumentException::class.java) { OAuthProtocol.parseCredential(Platform.CLAUDE, json, 0, old) }
    }

    @Test fun `Codex account ID and expiry use token claims not user ID`() {
        val claims = """{"sub":"user-not-workspace","exp":1000,"https://api.openai.com/auth":{"chatgpt_account_id":"workspace-one"}}"""
        val jwt = "header.${Base64.getUrlEncoder().withoutPadding().encodeToString(claims.toByteArray())}.sig"
        val body = JsonParser.parseString("""{"access_token":"$jwt","refresh_token":"refresh"}""").asJsonObject
        val parsed = OAuthProtocol.parseCredential(Platform.CHATGPT, body, 0)
        assertEquals("workspace-one", parsed.providerAccountId)
        assertEquals(1_000_000L, parsed.expiresAt)
        assertEquals("OAuthCredential([REDACTED])", parsed.toString())
    }

    @Test fun `missing identity cannot be saved as a successful OAuth login`() {
        val body = JsonParser.parseString("""{"access_token":"opaque-token"}""").asJsonObject
        assertThrows(IllegalArgumentException::class.java) { OAuthProtocol.parseCredential(Platform.CLAUDE, body, 0) }
    }
}
