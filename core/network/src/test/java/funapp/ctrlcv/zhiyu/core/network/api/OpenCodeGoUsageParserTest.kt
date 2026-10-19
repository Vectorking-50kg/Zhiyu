package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class OpenCodeGoUsageParserTest {
    private val now = Instant.parse("2026-10-03T00:00:00Z").toEpochMilli()

    @Test fun `official response retains three percentages and absolute reset times`() {
        val result = OpenCodeGoUsageParser.parse("""{
            "usage": {
                "rolling": {"status":"ok","percent":25.5,"resetsAt":"2026-10-03T02:30:00Z"},
                "weekly": {"status":"ok","percent":40,"resetsAt":"2026-10-06T12:00:00Z"},
                "monthly": {"status":"ok","percent":65,"resetsAt":"2026-10-28T00:00:00Z"}
            }
        }""", now)

        assertEquals(Platform.OPENCODE_GO, result.platform)
        assertEquals("Go", result.planLabel)
        assertEquals(now, result.updatedAt)
        assertEquals(listOf("5 小时限额", "周限额", "月限额"), result.items.map { it.label })
        assertEquals(listOf(25.5f, 40f, 65f), result.items.map { it.percent })
        assertEquals(listOf("opencode_go.rolling", "opencode_go.weekly", "opencode_go.monthly"), result.items.map { it.windowId })
        assertEquals(18_000L, result.items[0].windowDurationSeconds)
        assertEquals(604_800L, result.items[1].windowDurationSeconds)
        assertEquals(50f, result.items[0].elapsedPercent!!, 0.01f)
        assertEquals(50f, result.items[1].elapsedPercent!!, 0.01f)
        assertEquals(Instant.parse("2026-10-28T00:00:00Z").toEpochMilli(), result.items[2].resetAt)
        assertEquals("25天后重置", result.items[2].resetCountdown)
        assertNull(result.items[2].windowDurationSeconds)
        assertNull(result.items[2].elapsedPercent)
        assertTrue(result.items.all { it.valueText == null })
    }

    @Test fun `an unused subscription is valid and returns measured zero usage`() {
        val result = OpenCodeGoUsageParser.parse("""{
            "usage":{"rolling":{"percent":0},"weekly":{"percent":0},"monthly":{"percent":0}}
        }""", now)
        assertEquals(3, result.items.size)
        assertTrue(result.items.all { it.percent == 0f })
    }

    @Test fun `a rate limited quota is usage data and excessive percentages are bounded`() {
        val result = OpenCodeGoUsageParser.parse("""{
            "usage":{"rolling":{"status":"rate-limited","percent":125,"resetsAt":"2026-10-03T01:00:00Z"}}
        }""", now)
        assertEquals(100f, result.items.single().percent)
        assertEquals("1小时后重置", result.items.single().resetCountdown)
    }

    @Test fun `malformed windows do not invent zero or discard valid quota`() {
        val result = OpenCodeGoUsageParser.parse("""{
            "usage":{
                "rolling":{"percent":"NaN"},
                "weekly":{"percent":55,"resetsAt":"bad-date"},
                "monthly":{"percent":-5}
            }
        }""", now)
        assertEquals("周限额", result.items.single().label)
        assertEquals(55f, result.items.single().percent)
        assertNull(result.items.single().resetAt)
        assertNull(result.items.single().elapsedPercent)
    }

    @Test fun `empty malformed and nonfinite responses cannot validate a subscription`() {
        listOf(
            "{}", "null", "[]", "secret-token", "{\"usage\":{}}",
            """{"usage":{"rolling":null,"weekly":[],"monthly":{"percent":"Infinity"}}}""",
            """{"usage":{"rolling":{"percent":"secret-token"}}}""",
            """{"type":"error","error":{"type":"EntitlementError","message":"secret-token"}}""",
        ).forEach { body ->
            val error = assertThrows(ApiStructureChangedException::class.java) { OpenCodeGoUsageParser.parse(body, now) }
            assertEquals(Platform.OPENCODE_GO, error.platform)
            assertFalse(error.message.orEmpty().contains("secret-token"))
        }
    }

    @Test fun `elapsed cached resets never zero measured usage`() {
        val original = OpenCodeGoUsageParser.parse("""{
            "usage":{"rolling":{"percent":95,"resetsAt":"2026-10-03T00:01:00Z"}}
        }""", now)
        val projected = original.atTime(now + 120_000)
        assertEquals(95f, projected.items.single().percent)
        assertEquals(100f, projected.items.single().elapsedPercent!!, 0f)
        assertEquals("重置时间已到，等待更新", projected.items.single().resetCountdown)
        assertEquals(now, projected.updatedAt)
    }
}
