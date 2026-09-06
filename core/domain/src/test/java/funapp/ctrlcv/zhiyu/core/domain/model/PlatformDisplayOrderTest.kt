package funapp.ctrlcv.zhiyu.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PlatformDisplayOrderTest {
    @Test fun `ChatGPT leads the complete provider display list`() {
        assertEquals(Platform.CHATGPT, Platform.displayOrder.first())
        assertEquals("ChatGPT", Platform.CHATGPT.displayName)
        assertEquals(Platform.entries.toSet(), Platform.displayOrder.toSet())
        assertEquals(Platform.entries.size, Platform.displayOrder.size)
    }

    @Test fun `branding preserves existing account keys and notification IDs`() {
        assertEquals("chatgpt", Platform.CHATGPT.key)
        assertEquals(0, Platform.CLAUDE.ordinal)
        assertEquals(1, Platform.CHATGPT.ordinal)
    }
}
