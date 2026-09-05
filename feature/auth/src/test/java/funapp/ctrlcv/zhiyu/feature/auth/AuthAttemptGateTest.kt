package funapp.ctrlcv.zhiyu.feature.auth

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import org.junit.Assert.*
import org.junit.Test

class AuthAttemptGateTest {
    @Test fun `duplicate callbacks cannot start concurrent validation`() {
        val gate = AuthAttemptGate()
        val attempt = gate.begin()!!
        assertNull(gate.begin())
        assertTrue(gate.isCurrent(attempt))
    }

    @Test fun `cancel and new login prevent old async validation from saving or finishing the new login`() {
        val gate = AuthAttemptGate()
        val old = gate.begin()!!
        gate.cancel()
        assertFalse(gate.isCurrent(old))
        val current = gate.begin()!!
        gate.finish(old)
        assertTrue(gate.isCurrent(current))
        assertFalse(gate.isCurrent(old))
    }

    @Test fun `switching or upgrading an unknown legacy identity requires confirmation`() {
        assertFalse(requiresAccountSwitchConfirmation(false, null, "new"))
        assertFalse(requiresAccountSwitchConfirmation(true, "same", "same"))
        assertTrue(requiresAccountSwitchConfirmation(true, "old", "new"))
        assertTrue(requiresAccountSwitchConfirmation(true, null, "new"))
        assertTrue(requiresAccountSwitchConfirmation(true, "old", null))
    }

    @Test fun `chunked cookie must contain a contiguous sequence starting at zero`() {
        assertEquals("abcdef", AuthViewModel.extractCookieValue(Platform.CHATGPT,
            "__Secure-next-auth.session-token.1=def; __Secure-next-auth.session-token.0=abc"))
        assertNull(AuthViewModel.extractCookieValue(Platform.CHATGPT,
            "__Secure-next-auth.session-token.1=def"))
        assertNull(AuthViewModel.extractCookieValue(Platform.CHATGPT,
            "__Secure-next-auth.session-token.0=abc; __Secure-next-auth.session-token.2=def"))
    }

    @Test fun `Zen preserves exact cookie name without treating handshake state as a session`() {
        assertEquals("__Host-auth=Fe26.2.token", AuthViewModel.extractCookieValue(Platform.ZEN,
            "auth_state=handshake; __Host-auth=Fe26.2.token; csrf=other"))
        assertNull(AuthViewModel.extractCookieValue(Platform.ZEN, "auth_state=handshake; csrf=other"))
    }
}
