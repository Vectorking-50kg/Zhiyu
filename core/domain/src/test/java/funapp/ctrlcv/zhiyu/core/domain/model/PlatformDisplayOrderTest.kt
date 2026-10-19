package funapp.ctrlcv.zhiyu.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        assertEquals("zen", Platform.ZEN.key)
        assertEquals(3, Platform.ZEN.ordinal)
        assertEquals(6, Platform.DEEPSEEK.ordinal)
    }

    @Test fun `Go is adjacent to Zen in display order without changing existing ordinals`() {
        val zenIndex = Platform.displayOrder.indexOf(Platform.ZEN)
        assertEquals(Platform.OPENCODE_GO, Platform.displayOrder[zenIndex + 1])
        assertEquals(7, Platform.OPENCODE_GO.ordinal)
        assertTrue(Platform.OPENCODE_GO.requiresApiKey)
        assertEquals("OpenCode Zen", Platform.ZEN.displayName)
        assertEquals("OpenCode Go", Platform.OPENCODE_GO.displayName)
    }
}
