package funapp.ctrlcv.zhiyu.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UsageDisplayTest {
    @Test fun `time belongs to the highest usage window rather than the highest time window`() {
        val metric = usage(
            UsageItem("5 小时限额", 62f, elapsedPercent = 90f),
            UsageItem("周限额", 84f, elapsedPercent = 30f),
        ).primaryMetric()

        assertEquals("周限额", metric.label)
        assertEquals("84%", metric.text)
        assertEquals(84, metric.percent)
        assertEquals(30f, metric.elapsedPercent!!, 0f)
    }

    @Test fun `equal usage keeps the first window and its time`() {
        val metric = usage(
            UsageItem("5 小时限额", 60f, elapsedPercent = 20f),
            UsageItem("周限额", 60f, elapsedPercent = 80f),
        ).primaryMetric()

        assertEquals("5 小时限额", metric.label)
        assertEquals(20f, metric.elapsedPercent!!, 0f)
    }

    @Test fun `zero time is retained and finite time is bounded`() {
        listOf(0f to 0f, -5f to 0f, 150f to 100f).forEach { (time, expected) ->
            val metric = usage(UsageItem("配额", 45f, elapsedPercent = time)).primaryMetric()
            assertEquals(expected, metric.elapsedPercent!!, 0f)
        }
    }

    @Test fun `missing or nonfinite time never fabricates a time layer`() {
        listOf(null, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY).forEach { time ->
            val metric = usage(UsageItem("配额", 45f, elapsedPercent = time)).primaryMetric()
            assertEquals(45, metric.percent)
            assertNull(metric.elapsedPercent)
        }
    }

    @Test fun `finite usage above one hundred keeps its reading but bounds the bar`() {
        val metric = usage(UsageItem("配额", 125.9f, elapsedPercent = 70f)).primaryMetric()

        assertEquals("125%", metric.text)
        assertEquals(100, metric.percent)
        assertEquals(70f, metric.elapsedPercent!!, 0f)
    }

    @Test fun `balance providers prefer balance and expose neither progress layer`() {
        listOf(
            Platform.AIHUBMIX to "余额",
            Platform.DEEPSEEK to "账户余额",
            Platform.ZEN to "账户余额",
        ).forEach { (platform, label) ->
            val metric = UsageInfo(platform, listOf(
                UsageItem("配额", 90f, elapsedPercent = 70f),
                UsageItem(label, -1f, valueText = "¥12.30", elapsedPercent = 80f),
            )).primaryMetric()

            assertEquals(label, metric.label)
            assertTrue(metric.text.startsWith("¥"))
            assertNull(metric.percent)
            assertNull(metric.elapsedPercent)
        }
    }

    @Test fun `unlimited windows expose neither progress layer`() {
        val metric = usage(UsageItem("配额", 0f, unlimited = true, elapsedPercent = 80f)).primaryMetric()

        assertEquals("无限制", metric.text)
        assertNull(metric.percent)
        assertNull(metric.elapsedPercent)
    }

    @Test fun `nonfinite usage candidates cannot displace a valid window`() {
        val metric = usage(
            UsageItem("无效一", Float.POSITIVE_INFINITY, elapsedPercent = 90f),
            UsageItem("无效二", Float.NEGATIVE_INFINITY, elapsedPercent = 80f),
            UsageItem("无效三", Float.NaN, elapsedPercent = 70f),
            UsageItem("有效配额", 35f, elapsedPercent = 40f),
        ).primaryMetric()

        assertEquals("有效配额", metric.label)
        assertEquals(35, metric.percent)
        assertEquals(40f, metric.elapsedPercent!!, 0f)
    }

    @Test fun `no valid usage exposes neither progress layer`() {
        listOf(
            emptyList(),
            listOf(UsageItem("无效", Float.NaN, elapsedPercent = 80f)),
            listOf(UsageItem("无效", Float.POSITIVE_INFINITY, elapsedPercent = 80f)),
            listOf(UsageItem("信息", -1f, valueText = "即将续订", elapsedPercent = 80f)),
        ).forEach { items ->
            val metric = UsageInfo(Platform.CHATGPT, items).primaryMetric()
            assertEquals("--", metric.text)
            assertNull(metric.label)
            assertNull(metric.percent)
            assertNull(metric.elapsedPercent)
        }
    }

    @Test fun `expired cached window reaches full time without resetting measured usage`() {
        val now = 1_800_000_000_000L
        val original = UsageInfo(
            Platform.CHATGPT,
            listOf(UsageItem("5 小时限额", 95f,
                resetAt = now - 1_000, windowDurationSeconds = 18_000)),
            updatedAt = now - 3_600_000,
        )
        val display = original.atTime(now)
        val metric = display.primaryMetric()

        assertTrue(display.stale)
        assertEquals(original.updatedAt, display.updatedAt)
        assertEquals("95%", metric.text)
        assertEquals(95, metric.percent)
        assertEquals(100f, metric.elapsedPercent!!, 0f)
    }

    private fun usage(vararg items: UsageItem) = UsageInfo(Platform.CHATGPT, items.toList())
}
