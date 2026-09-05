package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class UsageParsersTest {
    private val now = Instant.parse("2026-09-06T00:00:00Z").toEpochMilli()

    @Test fun `Claude retains Design plan raw reset and elapsed percentage`() {
        val result = ClaudeUsageParser.parse("""{
            "five_hour":{"utilization":25,"resets_at":"2026-09-06T02:30:00Z"},
            "seven_day_omelette":{"utilization":40,"resets_at":"2026-09-10T00:00:00Z"},
            "seven_day_opus":null
        }""", "claude_max_20", now)
        assertEquals("Max 20×", result.planLabel)
        assertEquals(2, result.items.size)
        assertEquals(18000L, result.items.first().windowDurationSeconds)
        assertEquals(Instant.parse("2026-09-06T02:30:00Z").toEpochMilli(), result.items.first().resetAt)
        assertEquals(50f, result.items.first().elapsedPercent!!, 0.01f)
        assertEquals("周限额｜Claude Design", result.items.last().label)
    }

    @Test fun `Claude extra usage shows only explicit percentage without guessing currency`() {
        val result = ClaudeUsageParser.parse("""{
            "five_hour":{"utilization":0},
            "extra_usage":{"is_enabled":true,"utilization":20,"used_credits":200,"monthly_limit":1000}
        }""", null, now)
        val extra = result.items.last()
        assertEquals("额外用量", extra.label)
        assertEquals(20f, extra.percent)
        assertNull(extra.valueText)
    }

    @Test fun `Claude invalid optional bucket does not discard valid quota or invent zero`() {
        val result = ClaudeUsageParser.parse("""{
            "five_hour":{"utilization":"NaN"},
            "seven_day":{"utilization":45,"resets_at":"bad-date"},
            "seven_day_sonnet":"unexpected"
        }""", null, now)
        assertEquals(1, result.items.size)
        assertEquals(45f, result.items.single().percent)
        assertNull(result.items.single().resetAt)
        assertNull(result.items.single().elapsedPercent)
    }

    @Test fun `empty or wholly malformed quota is a parse failure with no body in exception`() {
        listOf("{}", "null", "{\"five_hour\":{\"utilization\":\"secret-token\"}}", "secret-token").forEach {
            val error = assertThrows(ApiStructureChangedException::class.java) { ClaudeUsageParser.parse(it, null, now) }
            assertFalse(error.message.orEmpty().contains("secret-token"))
        }
        assertThrows(ApiStructureChangedException::class.java) { CodexUsageParser.parse("{\"plan_type\":\"pro\"}", now) }
    }

    @Test fun `Codex single primary weekly window is labelled from its actual duration`() {
        val result = CodexUsageParser.parse("""{
            "rate_limit":{"primary_window":{"used_percent":10,"limit_window_seconds":604800,"reset_after_seconds":302400}}
        }""", now)
        val window = result.items.single()
        assertEquals("周限额", window.label)
        assertEquals(604800L, window.windowDurationSeconds)
        assertEquals(now + 302400000L, window.resetAt)
        assertEquals(50f, window.elapsedPercent!!, 0.01f)
    }

    @Test fun `Codex retains Code Review and additional model limits`() {
        val result = CodexUsageParser.parse("""{
            "plan_type":"chatgptgoplan",
            "rate_limit":{"primary_window":{"used_percent":15,"reset_at":"2026-09-06T05:00:00Z"}},
            "code_review_rate_limit":{"secondary_window":{"used_percent":40,"limit_window_seconds":604800}},
            "additional_rate_limits":[{"limit_name":"Model A","rate_limit":{"primary_window":{"used_percent":75,"limit_window_seconds":3600}}}]
        }""", now)
        assertEquals("Go", result.planLabel)
        assertEquals(listOf("5 小时限额", "Code Review｜周", "Model A｜1 小时"), result.items.map { it.label })
        assertEquals(3, result.items.map { it.windowId }.distinct().size)
        assertEquals(now + 18000000L, result.items.first().resetAt)
    }

    @Test fun `Codex credits remain separate from reset cards and expired cards are removed`() {
        val result = CodexUsageParser.parse("""{
            "credits":{"has_credits":true,"unlimited":false,"balance":"12.50"},
            "rate_limit_reset_credits":{"available_count":2,"credits":[
                {"status":"available","reset_type":"codex_rate_limits","expires_at":"2026-09-07T00:00:00Z"},
                {"status":"available","expires_at":"2026-09-06T00:00:00Z"},
                {"status":"used","expires_at":"2026-09-08T00:00:00Z"},
                {"status":"available","reset_type":"other","expires_at":"2026-09-08T00:00:00Z"}
            ]}
        }""", now)
        assertEquals("额外额度", result.items.single().label)
        assertEquals("12.5", result.items.single().valueText)
        assertEquals(2, result.resetCredits?.availableCount)
        assertEquals(1, result.resetCredits?.credits?.size)
    }

    @Test fun `Codex malformed values do not prevent surviving quota from rendering`() {
        val result = CodexUsageParser.parse("""{
            "rate_limit":{"primary_window":{"used_percent":"NaN"},"secondary_window":{"used_percent":150,"reset_at":9223372036854775807}},
            "credits":{"balance":"Infinity"},"code_review_rate_limit":null
        }""", now)
        assertEquals(1, result.items.size)
        assertEquals(100f, result.items.single().percent)
        assertNull(result.items.single().resetAt)
        assertNull(result.items.single().resetCountdown)
    }

    @Test fun `explicit invalid duration does not invent a five hour window`() {
        val result = CodexUsageParser.parse("""{
            "rate_limit":{"primary_window":{"used_percent":20,"limit_window_seconds":-1,"reset_after_seconds":900}}
        }""", now)
        assertEquals("主要限额", result.items.single().label)
        assertNull(result.items.single().windowDurationSeconds)
        assertNull(result.items.single().elapsedPercent)
        assertEquals(now + 900000L, result.items.single().resetAt)
    }
}
