package funapp.ctrlcv.zhiyu.core.data.cache

import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.data.testing.MemoryPreferences
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import org.junit.Assert.*
import org.junit.Test

class UsageCacheTest {
    private val platform = Platform.CLAUDE
    private var time = 10_000_000L
    private var accounts = listOf(account("a"), account("b"))
    private val prefs = MemoryPreferences()
    private val gson = Gson()
    private val cache = createCache()
    private fun createCache() = UsageCache(prefs, gson, { accounts }, { time })
    private fun account(id: String) = Account(id, platform, id)
    private fun usage(percent: Float) = UsageInfo(platform, listOf(UsageItem("5h", percent)), updatedAt = time)

    @Test fun isolatesAccountsAndPersistsFailureWithoutReplacingLastGood() {
        cache.save(platform, "a", usage(12f))
        cache.save(platform, "b", usage(89f))
        val firstUpdatedAt = time
        time += 1000
        val failure = UsageFailure(UsageFailureKind.NETWORK, occurredAt = time)
        cache.saveFailure(platform, "a", failure)

        val reopened = createCache()
        val a = reopened.get(platform, "a")!!
        assertEquals(12f, a.items.single().percent)
        assertEquals(firstUpdatedAt, a.updatedAt)
        assertEquals(failure, a.refreshFailure)
        assertTrue(a.stale)
        assertEquals(89f, reopened.get(platform, "b")!!.items.single().percent)
        assertNull(reopened.get(platform, "b")!!.refreshFailure)
        assertNull(reopened.get(platform))
        assertEquals(setOf("a", "b"), reopened.getAll().map { it.accountId }.toSet())

        reopened.save(platform, "a", usage(20f))
        assertNull(reopened.get(platform, "a")!!.refreshFailure)
        assertFalse(reopened.get(platform, "a")!!.stale)
        assertEquals(89f, reopened.get(platform, "b")!!.items.single().percent)
    }

    @Test fun firstFailureIsVisibleWithoutInventingAUsageReading() {
        val failure = UsageFailure(UsageFailureKind.AUTH_REQUIRED, occurredAt = time)
        cache.saveFailure(platform, "a", failure)
        val cached = createCache().get(platform, "a")!!
        assertEquals("a", cached.accountId)
        assertTrue(cached.items.isEmpty())
        assertEquals(0L, cached.updatedAt)
        assertTrue(cached.stale)
        assertEquals(failure, cached.refreshFailure)
    }

    @Test fun migratesLegacyOnlyForOneKnownAccountAndKeepsDemoSaveCompatible() {
        accounts = emptyList()
        cache.save(platform, usage(37f))
        assertTrue(prefs.contains(platform.key))
        assertTrue(cache.getAll().isEmpty())
        accounts = listOf(account("a"))
        assertEquals(37f, cache.get(platform, "a")!!.items.single().percent)
        assertFalse(prefs.contains(platform.key))
        assertEquals("a", createCache().getAll().single().accountId)
    }

    @Test fun ambiguousLegacyIsDiscardedAndCannotMigrateAfterAnAccountIsRemoved() {
        prefs.edit().putString(platform.key, gson.toJson(usage(91f))).apply()
        assertNull(cache.get(platform, "a"))
        accounts = listOf(account("b"))
        assertNull(cache.get(platform, "b"))
    }

    @Test fun explicitOldOwnerCannotBeReboundToAnotherAccount() {
        accounts = listOf(account("b"))
        prefs.edit().putString(platform.key, gson.toJson(usage(91f).copy(accountId = "a"))).apply()
        assertNull(cache.get(platform, "b"))
    }

    @Test fun clockProjectionAgesDataWithoutChangingQuotaOrFetchTime() {
        accounts = listOf(account("a"))
        val fetchedAt = time
        val resetAt = time + 3_600_000L
        cache.save(platform, "a", usage(37f).copy(items = listOf(
            UsageItem("5h", 37f, resetAt = resetAt, windowDurationSeconds = 18_000),
        )))
        time += 1_800_000L
        val aged = cache.getAll().single()
        assertTrue(aged.stale)
        assertEquals("30分钟后重置", aged.items.single().resetCountdown)
        assertEquals(37f, aged.items.single().percent)
        assertEquals(fetchedAt, aged.updatedAt)
        time = resetAt + 1
        assertEquals("重置时间已到，等待更新", cache.getAll().single().items.single().resetCountdown)
    }

    @Test fun removedAccountsAreNotReturnedAndClearingOneDoesNotEraseAnother() {
        cache.save(platform, "a", usage(20f))
        cache.save(platform, "b", usage(40f))
        cache.saveFailure(platform, "a", UsageFailure(UsageFailureKind.NETWORK))
        cache.clear(platform, "a")
        assertNull(cache.get(platform, "a"))
        assertNull(cache.getFailure(platform, "a"))
        assertNotNull(cache.get(platform, "b"))
        accounts = emptyList()
        assertTrue(cache.getAll().isEmpty())
    }

    @Test fun malformedNestedItemsCannotCrashCacheOrBecomeAZeroReading() {
        accounts = listOf(account("a"))
        val invalidItems = listOf(
            "null", "{}", "[null]", "[{}]", "[[]]",
            "[{\"label\":null,\"percent\":12}]",
            "[{\"label\":\"5h\"}]",
            "[{\"label\":\"5h\",\"percent\":\"NaN\"}]",
            "[{\"label\":\"5h\",\"percent\":1e999}]",
            "[{\"label\":\"5h\",\"percent\":101}]",
            "[{\"label\":\"5h\",\"percent\":-2}]",
            "[{\"label\":\"5h\",\"percent\":12,\"resetAt\":\"invalid\"}]",
            "[{\"label\":\"5h\",\"percent\":12,\"elapsedPercent\":1e999}]",
        )
        invalidItems.forEach { items ->
            prefs.edit().putString("v2:claude:1:a:snapshot", """{
                "platform":"CLAUDE","accountId":"a","items":$items,"updatedAt":$time
            }""").apply()
            assertNull("Must reject $items", cache.get(platform, "a"))
            assertTrue("Widget/dashboard getAll must remain safe for $items", cache.getAll().isEmpty())
        }
    }

    @Test fun malformedResetCardsAreRejectedBeforeTheyReachTheDashboard() {
        accounts = listOf(account("a"))
        listOf(
            "{}", "{\"availableCount\":2}",
            "{\"availableCount\":2,\"credits\":null}",
            "{\"availableCount\":2,\"credits\":[null]}",
            "{\"availableCount\":2,\"credits\":[{}]}",
        ).forEach { cards ->
            prefs.edit().putString("v2:claude:1:a:snapshot", """{
                "platform":"CLAUDE","accountId":"a","items":[{"label":"5h","percent":20}],
                "updatedAt":$time,"resetCredits":$cards
            }""").apply()
            assertNull(cache.get(platform, "a"))
        }
    }

    @Test fun legacyItemsWithoutNewOptionalFieldsStillMigrate() {
        accounts = listOf(account("a"))
        prefs.edit().putString(platform.key, """{
            "platform":"CLAUDE","items":[{"label":"5h","percent":12}],"updatedAt":$time
        }""").apply()
        assertEquals(12f, cache.get(platform, "a")!!.items.single().percent)
    }

    @Test fun badSnapshotCanStillExposeItsSafeRefreshFailure() {
        accounts = listOf(account("a"))
        prefs.edit().putString("v2:claude:1:a:snapshot", """{
            "platform":"CLAUDE","accountId":"a","items":[null],"updatedAt":$time
        }""").apply()
        cache.saveFailure(platform, "a", UsageFailure(UsageFailureKind.NETWORK, occurredAt = time))
        val result = cache.getAll().single()
        assertTrue(result.items.isEmpty())
        assertEquals(0, result.updatedAt)
        assertEquals(UsageFailureKind.NETWORK, result.refreshFailure?.kind)
    }
}
