package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class AntigravityUsageParserTest {
    private val now = Instant.parse("2026-11-28T00:00:00Z").toEpochMilli()

    @Test fun `maps explicit buckets across groups with absolute reset times`() {
        val usage = AntigravityUsageParser.summary("""{"groups":[
            {"buckets":[{"bucketId":"3p-weekly","remainingFraction":0.2,"resetTime":"2026-12-01T00:00:00Z"},
                {"bucketId":"gemini-5h","remainingFraction":0.75,"resetTime":"2026-11-28T01:00:00Z"}]},
            {"buckets":[{"bucketId":"3p-5h","remainingFraction":0},
                {"bucketId":"gemini-weekly","remainingFraction":1}]}]}""", now)
        assertEquals(Platform.ANTIGRAVITY, usage.platform)
        assertEquals(listOf(25f, 0f, 100f, 80f), usage.items.map { it.percent })
        assertEquals(listOf("Gemini · 5 小时", "Gemini · 每周", "Claude and GPT · 5 小时", "Claude and GPT · 每周"), usage.items.map { it.label })
        assertEquals(listOf(18_000L, 604_800L, 18_000L, 604_800L), usage.items.map { it.windowDurationSeconds })
        assertEquals(Instant.parse("2026-11-28T01:00:00Z").toEpochMilli(), usage.items.first().resetAt)
        assertEquals(80f, usage.items.first().elapsedPercent!!, 0.01f)
        assertEquals("1小时后重置", usage.items.first().resetCountdown)
        assertEquals(now, usage.updatedAt)
    }

    @Test fun `handles wrapped summary without inventing omitted buckets`() {
        val usage = AntigravityUsageParser.summary("""{"response":{"groups":[{"buckets":[
            {"bucketId":"gemini-weekly","remainingFraction":0.5,"resetTime":1795824000}]}]}}""", now)
        assertEquals(1, usage.items.size)
        assertEquals("antigravity.gemini-weekly", usage.items.single().windowId)
        assertNotNull(usage.items.single().resetAt)
    }

    @Test fun `unknown or invalid buckets are never presented as zero usage`() {
        for (buckets in listOf(
            """{"bucketId":"renamed","remainingFraction":1}""",
            """{"bucketId":"gemini-5h"}""",
            """{"bucketId":"gemini-5h","remainingFraction":-0.1}""",
            """{"bucketId":"gemini-5h","remainingFraction":1.1}""",
            """{"bucketId":"gemini-5h","remainingFraction":"NaN"}""",
        )) {
            assertThrows(ApiStructureChangedException::class.java) {
                AntigravityUsageParser.summary("""{"groups":[{"buckets":[$buckets]}]}""", now)
            }
        }
    }

    @Test fun `model fallback preserves models and does not infer window duration`() {
        val usage = AntigravityUsageParser.models("""{"models":{
            "gemini-pro":{"displayName":"Gemini Pro","quotaInfo":{"remainingFraction":0.9,"resetTime":"2026-11-28T02:00:00Z"}},
            "claude-sonnet":{"displayName":"Claude Sonnet","quotaInfo":{"remainingFraction":0.3}},
            "no-quota":{"displayName":"No quota"},
            "unknown":{"quotaInfo":{}},
            "invalid":{"quotaInfo":{"remainingFraction":"Infinity"}}
        }}""", now)
        assertEquals(listOf("Claude Sonnet", "Gemini Pro"), usage.items.map { it.label })
        assertEquals(70f, usage.items[0].percent, 0.01f)
        assertEquals(10f, usage.items[1].percent, 0.01f)
        assertTrue(usage.items.all { it.windowDurationSeconds == null && it.elapsedPercent == null })
        assertNotNull(usage.items[1].resetAt)
        assertNull(usage.planLabel)
    }

    @Test fun `empty models and malformed payloads fail closed`() {
        for (body in listOf("{}", "[]", "null", "not-json", """{"models":{}}""")) {
            assertThrows(ApiStructureChangedException::class.java) { AntigravityUsageParser.models(body, now) }
        }
    }

    @Test fun `prefers paid tier name and retains exact subscription name`() {
        assertEquals("Google AI Ultra", AntigravityUsageParser.plan("""{"paidTier":{"name":"Google AI Ultra"},"currentTier":{"name":"Free"}}"""))
        assertEquals("Google AI Pro", AntigravityUsageParser.plan("""{"currentTier":{"name":"Google AI Pro"}}"""))
        assertEquals("g1-pro-tier", AntigravityUsageParser.plan("""{"paidTier":{"id":"g1-pro-tier"}}"""))
        assertNull(AntigravityUsageParser.plan("{}"))
    }
}
