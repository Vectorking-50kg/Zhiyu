package funapp.ctrlcv.zhiyu.core.domain.model

import org.junit.Assert.*
import org.junit.Test

class UsageTimeTest {
    private val now = 1_800_000_000_000L

    @Test fun `cached time is recomputed without changing measured usage`() {
        val item = UsageItem("5 小时限额", 62.5f, resetCountdown = "2小时后重置",
            resetAt = now + 7_200_000, windowDurationSeconds = 18_000)
        val later = item.atTime(now + 3_600_000)
        assertEquals("1小时后重置", later.resetCountdown)
        assertEquals(80f, later.elapsedPercent!!, 0.001f)
        assertEquals(62.5f, later.percent, 0f)
    }

    @Test fun `passing reset time never fabricates a fresh quota`() {
        val info = UsageInfo(Platform.CLAUDE, listOf(UsageItem("5h", 95f,
            resetAt = now - 1000, windowDurationSeconds = 18_000)), updatedAt = now - 3_600_000)
        val display = info.atTime(now)
        assertTrue(display.stale)
        assertEquals(95f, display.items.single().percent, 0f)
        assertEquals("重置时间已到，等待更新", display.items.single().resetCountdown)
        assertEquals(100f, display.items.single().elapsedPercent!!, 0f)
        assertEquals(info.updatedAt, display.updatedAt)
    }

    @Test fun `legacy cache stays readable until a new snapshot supplies timestamps`() {
        val item = UsageItem("周限额", 42f, resetCountdown = "3天后重置", elapsedPercent = 12f)
        assertEquals(item, item.atTime(now))
    }

    @Test fun `time proportion remains bounded before the start of a window`() {
        val item = UsageItem("5h", 20f, resetAt = now + 36_000_000, windowDurationSeconds = 18_000)
        assertEquals(0f, item.atTime(now).elapsedPercent!!, 0f)
    }

    @Test fun `invalid window duration does not fabricate a time proportion`() {
        val item = UsageItem("配额", 20f, resetAt = now + 3600000, windowDurationSeconds = 0)
        assertNull(item.atTime(now).elapsedPercent)
    }
}
