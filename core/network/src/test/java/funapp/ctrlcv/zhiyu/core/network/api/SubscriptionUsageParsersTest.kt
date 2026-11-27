package funapp.ctrlcv.zhiyu.core.network.api

import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.primaryMetric
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant

class SubscriptionUsageParsersTest {
    private val now = Instant.parse("2026-11-18T00:00:00Z").toEpochMilli()

    @Test fun `Devin reads daily and weekly usage with independent reset times`() {
        val usage = DevinUsageParser.parse(
            """{
                "plan_name":"pro",
                "daily_percentage":0.12,
                "weekly_percentage":42,
                "daily_reset_at":"2026-11-18T00:00:00-08:00",
                "weekly_reset_at":1795334400,
                "overage_balance_cents":1250
            }""", "org_test", now
        )

        assertEquals("Pro", usage.planLabel)
        assertEquals("org_test", usage.providerAccountId)
        val (daily, weekly, balance) = usage.items
        assertEquals(12f, daily.percent, 0.01f)
        assertEquals(Instant.parse("2026-11-18T08:00:00Z").toEpochMilli(), daily.resetAt)
        assertEquals(42f, weekly.percent, 0.01f)
        assertEquals(1795334400000L, weekly.resetAt)
        assertEquals("$12.50", balance.valueText)
    }

    @Test fun `Devin hides daily quota even if valid counts are present`() {
        val usage = DevinUsageParser.parse(
            """{"hide_daily_quota":true,"daily_percentage":100,"quota_usage":{
                "daily_quota":{"used":8,"limit":10},
                "weekly_quota":{"remaining_percent":0.75,"next_reset_at":1795334400000}
            }}""", "org_test", now
        )

        val weekly = usage.items.single()
        assertEquals("devin.weekly", weekly.windowId)
        assertEquals(25f, weekly.percent, 0.01f)
        assertEquals(1795334400000L, weekly.resetAt)
    }

    @Test fun `Devin renders a reported zero balance even without an allocation`() {
        val usage = DevinUsageParser.parse(
            """{"has_quota_allocation":false,"daily_percentage":0,"weekly_percentage":0,"overage_balance":0}""",
            "org_test", now,
        )

        assertEquals("$0.00", usage.primaryMetric().text)
        assertNull(usage.primaryMetric().percent)
        assertEquals(1, usage.items.size)
    }

    @Test fun `Copilot uses fractional quota remaining and the snapshot reset`() {
        val usage = CopilotUsageParser.parse(
            """{
                "copilot_plan":"individual_pro", "login":"octocat", "token_based_billing":true,
                "quota_reset_date_utc":"2026-12-01T00:00:00Z",
                "quota_snapshots":{
                    "premium_interactions":{
                        "entitlement":"2000", "remaining":0, "quota_remaining":1750.5,
                        "quota_reset_at":1796169600, "has_quota":false
                    },
                    "chat":{"entitlement":0,"unlimited":true,"percent_remaining":100}
                }
            }""", now
        )

        val credits = usage.items.single()
        assertEquals("Pro+", usage.planLabel)
        assertEquals("octocat", usage.providerAccountId)
        assertEquals("AI 积分", credits.label)
        assertEquals(12.475f, credits.percent, 0.001f)
        assertEquals("剩余 1,750.5 / 2,000", credits.valueText)
        assertEquals(1796169600000L, credits.resetAt)
        assertNull(credits.windowDurationSeconds)
    }

    @Test fun `Copilot shows the confirmed additional spend and budget separately from included credits`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"individual_pro","token_based_billing":true,"quota_snapshots":{
                "premium_interactions":{"entitlement":7000,"credits_used":8732,"quota_remaining":-1732.1,
                    "percent_remaining":0,"overage_count":1724,"overage_entitlement":2000,"overage_permitted":true},
                "chat":{"entitlement":0,"overage_count":0,"overage_entitlement":0}
            }}""", now,
        )

        val additional = usage.items.single { it.label == "额外用量" }
        assertEquals("$17.24 / $20", additional.valueText)
        assertEquals(-1f, additional.percent, 0f)
        assertEquals("100%", usage.primaryMetric().text)
    }

    @Test fun `Copilot shows additional spend without a budget, including past spend after disabling overage`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"individual_pro","token_based_billing":true,"quota_snapshots":{
                "premium_interactions":{"entitlement":7000,"percent_remaining":0,
                    "overage_count":"1724.5","overage_permitted":false}
            }}""", now,
        )

        assertEquals("$17.25", usage.items.single { it.label == "额外用量" }.valueText)
    }

    @Test fun `Copilot displays an unused additional budget and skips a disabled zero placeholder`() {
        val source = """{"copilot_plan":"individual","token_based_billing":true,"quota_snapshots":{
            "premium_interactions":{"entitlement":1000,"percent_remaining":50,"overage_count":0,
                "overage_entitlement":0,"overage_permitted":false}
        }}"""
        val disabled = CopilotUsageParser.parse(source, now)
        val budgeted = CopilotUsageParser.parse(source.replace("\"overage_entitlement\":0", "\"overage_entitlement\":2000"), now)

        assertEquals(listOf("AI 积分"), disabled.items.map { it.label })
        assertEquals("$0.00 / $20", budgeted.items.single { it.label == "额外用量" }.valueText)
    }

    @Test fun `Copilot does not invent spend when only the budget is returned`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"individual","token_based_billing":true,"quota_snapshots":{
                "premium_interactions":{"entitlement":1000,"percent_remaining":50,
                    "overage_entitlement":2000,"overage_permitted":true}
            }}""", now,
        )

        assertEquals(listOf("AI 积分"), usage.items.map { it.label })
    }

    @Test fun `Copilot keeps legacy chat and completion quotas distinct`() {
        val usage = CopilotUsageParser.parse(
            """{
                "copilot_plan":"free","monthly_quotas":{"chat":50,"completions":2000},
                "limited_user_quotas":{"chat":0,"completions":1990},
                "limited_user_reset_date":"2026-12-01"
            }""", now
        )

        assertEquals("Free", usage.planLabel)
        val (chat, completions) = usage.items
        assertEquals("聊天额度", chat.label)
        assertEquals(100f, chat.percent, 0.01f)
        assertEquals("代码补全额度", completions.label)
        assertEquals("剩余 1,990 / 2,000", completions.valueText)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z").toEpochMilli(), chat.resetAt)
    }

    @Test fun `Copilot displays legacy premium requests with a UTC date fallback`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"individual","quota_reset_date_utc":"invalid","quota_reset_date":"2026-12-01",
                "quota_snapshots":{"premium_interactions":{"entitlement":300,"remaining":150,"percent_remaining":50,
                    "overage_count":8,"overage_entitlement":50,"overage_permitted":true}}
            }""", now
        )

        assertEquals("Pro", usage.planLabel)
        assertEquals("高级请求", usage.items.single().label)
        assertEquals(50f, usage.items.single().percent, 0.01f)
        assertEquals(Instant.parse("2026-12-01T00:00:00Z").toEpochMilli(), usage.items.single().resetAt)
    }

    @Test fun `Copilot Business seat placeholders do not produce a percentage`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"business","token_based_billing":true,"quota_snapshots":{
                "premium_interactions":{"unlimited":true,"entitlement":0,"credits_used":46.25,"percent_remaining":100},
                "chat":{"unlimited":true,"entitlement":0,"percent_remaining":100}
            }}""", now,
        )

        assertEquals("Business", usage.planLabel)
        assertEquals("46.25", usage.primaryMetric().text)
        assertEquals("已用 AI 积分", usage.primaryMetric().label)
        assertNull(usage.primaryMetric().percent)
        assertEquals(1, usage.items.size)
    }

    @Test fun `Copilot seat without an exposed limit displays quota availability`() {
        val usage = CopilotUsageParser.parse(
            """{"copilot_plan":"business","token_based_billing":true,
                "quota_snapshots":{"premium_interactions":{"unlimited":true,"entitlement":0}}
            }""", now,
        )
        assertEquals("平台未提供", usage.primaryMetric().text)
        assertNull(usage.primaryMetric().percent)
    }

    @Test fun `responses without quota fields cannot validate a subscription`() {
        assertThrows(ApiStructureChangedException::class.java) {
            DevinUsageParser.parse("""{"plan_name":"pro","daily_reset_at":"2026-12-01"}""", "org_test", now)
        }
        assertThrows(ApiStructureChangedException::class.java) {
            CopilotUsageParser.parse("""{"copilot_plan":"individual","quota_snapshots":{"premium_interactions":{"remaining":0}}}""", now)
        }
        assertThrows(ApiStructureChangedException::class.java) {
            CopilotUsageParser.parse("""{"copilot_plan":"business","quota_snapshots":{}}""", now)
        }
    }
}
