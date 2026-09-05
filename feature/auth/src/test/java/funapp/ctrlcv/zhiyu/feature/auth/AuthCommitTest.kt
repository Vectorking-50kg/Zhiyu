package funapp.ctrlcv.zhiyu.feature.auth

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class AuthCommitTest {
    @Test fun `metadata failure after token save restores the original login`() {
        var token = "old-token"
        var account = "old-account"
        var revoked = false
        val failure = runCatching {
            commitLogin(commit = {
                token = "new-token"
                throw IOException("metadata write failed")
            }, restore = {
                token = "old-token"
                account = "old-account"
            }, quarantine = { revoked = true; token = "" })
        }.exceptionOrNull()
        assertTrue(failure is AuthSaveException)
        assertEquals("old-token", token)
        assertEquals("old-account", account)
        assertFalse(revoked)
    }

    @Test fun `rollback failure revokes a partially saved credential instead of mixing identities`() {
        var token: String? = "old-token"
        val failure = runCatching {
            commitLogin(commit = {
                token = "new-token"
                throw IOException("metadata failure")
            }, restore = { throw IOException("rollback disk failure") }, quarantine = { token = null })
        }.exceptionOrNull()
        assertNull(token)
        assertEquals("登录状态保存失败，请重新登录", failure?.message)
    }

    @Test fun `successful commit keeps both new account and credential`() {
        var token = "old-token"
        var account = "old-account"
        commitLogin(commit = { token = "new-token"; account = "new-account" },
            restore = { fail("Successful login must not rollback") },
            quarantine = { fail("Successful login must not revoke") })
        assertEquals("new-token", token)
        assertEquals("new-account", account)
    }
}
