package funapp.ctrlcv.zhiyu.core.storage

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import org.junit.Assert.*
import org.junit.Test

class CredentialKeysTest {
    @Test fun `restoring one account cannot clear another with the same prefix`() {
        val keys = listOf("claude_a_cookie", "claude_a_oauth", "claude_a_extra_workspace_id",
            "claude_a_b_cookie", "claude_a_b_oauth", "claude_a_extra_b_cookie",
            "claude_a_extra_b_extra_workspace_id", "chatgpt_a_cookie")
        assertEquals(listOf("claude_a_cookie", "claude_a_oauth", "claude_a_extra_workspace_id"),
            keys.filter { credentialKeyBelongsTo(it, Platform.CLAUDE, "a") })
        assertEquals(listOf("claude_a_extra_b_cookie", "claude_a_extra_b_extra_workspace_id"),
            keys.filter { credentialKeyBelongsTo(it, Platform.CLAUDE, "a_extra_b") })
    }
}
