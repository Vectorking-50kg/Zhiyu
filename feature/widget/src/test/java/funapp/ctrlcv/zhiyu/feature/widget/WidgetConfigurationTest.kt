package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WidgetConfigurationTest {
    private val now = 1_000_000L
    private val personal = Account("personal", Platform.CHATGPT, "个人", pinned = true)
    private val work = Account("work/second", Platform.CHATGPT, "工作", pinned = false)
    private val go = Account("personal", Platform.OPENCODE_GO, "Go", monitoringEnabled = false)
    private fun snapshot(account: Account, percent: Float) = UsageInfo(account.platform,
        listOf(UsageItem("周限额", percent)), accountId = account.id, updatedAt = now)
    private fun data() = widgetUsageData(listOf(personal, work, go),
        listOf(snapshot(work, 80f), snapshot(personal, 20f)), setOf(Platform.CHATGPT, Platform.OPENCODE_GO), now)

    @Test fun `catalogue joins by account identity and preserves stable account order`() {
        val result = data()
        assertEquals(listOf("个人", "工作", "Go"), result.items.map { it.accountName })
        assertEquals(listOf("20%", "80%", "--"), result.items.map { it.mainText })
        assertEquals(listOf("chatgpt/personal", "chatgpt/work/second", "opencode_go/personal"), result.items.map { it.accountKey })
        assertEquals("已暂停", result.items.last().statusLabel)
        assertEquals(now, result.lastUpdated)
    }

    @Test fun `new accounts without a snapshot show unknown instead of fabricated zero`() {
        val account = personal.copy(pinned = false)
        val item = widgetUsageData(listOf(account), emptyList(), now = now).items.single()
        assertEquals("--", item.mainText)
        assertEquals(-1f, item.mainPercent, 0f)
        assertEquals("待同步", item.statusLabel)
        assertEquals(0L, item.updatedAt)
    }

    @Test fun `a failed first fetch retains its recovery status instead of looking pending`() {
        val account = go.copy(monitoringEnabled = true)
        val failed = UsageInfo(account.platform, emptyList(), accountId = account.id, updatedAt = 0,
            refreshFailure = UsageFailure(UsageFailureKind.AUTH_REQUIRED))
        val item = widgetUsageData(listOf(account), listOf(failed), now = now).items.single()
        assertEquals("--", item.mainText)
        assertEquals("更新密钥", item.statusLabel)
    }

    @Test fun `notification pin following respects explicit account overrides`() {
        val result = data().selected(WidgetConfiguration(WidgetSelectionMode.PINNED), WidgetKind.OVERVIEW)
        assertEquals(listOf("个人", "Go"), result.map { it.accountName })
    }

    @Test fun `each widget chooses its own account even for two accounts from one provider`() {
        val first = data().selected(WidgetConfiguration(WidgetSelectionMode.CUSTOM, setOf("chatgpt/personal")), WidgetKind.COMPACT)
        val second = data().selected(WidgetConfiguration(WidgetSelectionMode.CUSTOM, setOf("chatgpt/work/second")), WidgetKind.DETAIL)
        assertEquals("20%", first.single().mainText)
        assertEquals("80%", second.single().mainText)
    }

    @Test fun `a deleted or unconfigured single account never falls back to another subscription`() {
        assertTrue(data().selected(WidgetConfiguration.defaultFor(WidgetKind.COMPACT), WidgetKind.COMPACT).isEmpty())
        assertTrue(data().selected(WidgetConfiguration(WidgetSelectionMode.CUSTOM, setOf("chatgpt/deleted")), WidgetKind.DETAIL).isEmpty())
        assertTrue(data().selected(WidgetConfiguration(WidgetSelectionMode.ALL), WidgetKind.DETAIL).isEmpty())
    }

    @Test fun `custom selection filters unavailable accounts and leaves other subscriptions untouched`() {
        val result = data().selected(WidgetConfiguration(WidgetSelectionMode.CUSTOM,
            setOf("chatgpt/work/second", "claude/deleted")), WidgetKind.OVERVIEW)
        assertEquals("工作", result.single().accountName)
    }

    @Test fun `account reference parsing preserves complete ids and rejects invalid providers`() {
        assertEquals(WidgetAccountReference(Platform.CHATGPT, "work/second"), WidgetAccountReference.parse("chatgpt/work/second"))
        assertEquals(WidgetAccountReference(Platform.OPENCODE_GO, "a_b"), WidgetAccountReference.parse("opencode_go/a_b"))
        listOf("chatgpt", "chatgpt/", "unknown/a", "/account").forEach { assertNull(WidgetAccountReference.parse(it)) }
    }

    @Test fun `small details always retain the highest quota window`() {
        val item = WidgetPlatformItem("Claude", 95f, metricLabel = "模型限额", details = listOf(
            UsageItem("5 小时限额", 20f), UsageItem("周限额", 50f), UsageItem("模型限额", 95f),
        ))
        assertEquals(listOf("5 小时限额", "模型限额"), item.visibleDetails(2).map { it.label })
        assertTrue(item.visibleDetails(0).isEmpty())
    }

    @Test fun `deleted accounts do not survive as orphaned widget rows`() {
        val result = widgetUsageData(listOf(personal), listOf(snapshot(personal, 20f), snapshot(work, 90f)), now = now)
        assertEquals(1, result.items.size)
        assertEquals("个人", result.items.single().accountName)
    }
}
