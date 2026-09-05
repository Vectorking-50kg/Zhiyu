package funapp.ctrlcv.zhiyu.core.storage

import org.junit.Assert.assertEquals
import org.junit.Test

class TokenImportTest {
    @Test fun `legacy cookie backup removes local OAuth only for the matching account`() {
        assertEquals(setOf("claude_one_oauth"), authCounterpartKeysToRemove(setOf("claude_one_cookie", "zen_two_extra_workspace_id")))
    }
    @Test fun `OAuth backup removes the prior cookie`() {
        assertEquals(setOf("chatgpt_one_cookie"), authCounterpartKeysToRemove(setOf("chatgpt_one_oauth")))
    }
    @Test fun `mixed backup has deterministic OAuth precedence`() {
        assertEquals(setOf("claude_one_cookie"), authCounterpartKeysToRemove(setOf("claude_one_oauth", "claude_one_cookie")))
    }
}
