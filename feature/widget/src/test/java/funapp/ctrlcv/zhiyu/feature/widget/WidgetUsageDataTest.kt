package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class WidgetUsageDataTest {
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
