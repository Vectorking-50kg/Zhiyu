package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WidgetUsageDataTest {
    @Test fun `Go shows its highest quota next to a separate Zen balance`() {
        val data = listOf(
            UsageInfo(Platform.ZEN, listOf(UsageItem("账户余额", -1f, valueText = "$18.42")), updatedAt = 1000),
            UsageInfo(Platform.OPENCODE_GO, listOf(
                UsageItem("5 小时限额", 20f), UsageItem("周限额", 40f), UsageItem("月限额", 65f, resetAt = 5000),
            ), updatedAt = 1000, refreshFailure = UsageFailure(UsageFailureKind.SUBSCRIPTION_REQUIRED)),
        ).toWidgetUsageData(6000)
        assertEquals(listOf("OpenCode Zen", "OpenCode Go"), data.items.map { it.name })
        assertEquals("$18.42", data.items[0].mainText)
        assertEquals("65%", data.items[1].mainText)
        assertEquals("等待额度更新", data.items[1].resetInfo)
        assertEquals("未开通 OpenCode Go 订阅，请检查密钥对应的账户", data.items[1].status)
    }

    @Test fun `balance stays money and is not turned into a quota percentage`() {
        val data = listOf(UsageInfo(Platform.DEEPSEEK,
            listOf(UsageItem("账户余额", -1f, valueText = "¥86.32")), updatedAt = 1000)).toWidgetUsageData(1000)
        assertEquals("¥86.32", data.items.single().mainText)
        assertEquals(-1f, data.items.single().mainPercent, 0f)
    }

    @Test fun `failed refresh shows last good quota and safe failure reason`() {
        val data = listOf(UsageInfo(Platform.CLAUDE,
            listOf(UsageItem("5h", 93f, resetAt = 5000)), updatedAt = 1000,
            refreshFailure = UsageFailure(UsageFailureKind.NETWORK))).toWidgetUsageData(6000)
        assertEquals("93%", data.items.single().mainText)
        assertEquals("等待额度更新", data.items.single().resetInfo)
        assertEquals("网络连接失败，请检查网络后重试", data.items.single().status)
        assertEquals(1000L, data.lastUpdated)
    }

    @Test fun `failed first fetch is unknown rather than zero percent`() {
        val data = listOf(UsageInfo(Platform.CHATGPT, emptyList(), updatedAt = 0,
            refreshFailure = UsageFailure(UsageFailureKind.AUTH_REQUIRED))).toWidgetUsageData(6000)
        assertEquals("--", data.items.single().mainText)
        assertEquals(0L, data.lastUpdated)
    }
}
