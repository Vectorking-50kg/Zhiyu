package funapp.ctrlcv.zhiyu.core.data.repository

import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.data.testing.MemoryPreferences
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class UsageRepositoryImplTest {
    private val platform = Platform.CLAUDE
    private var time = 10_000_000L
    private val accounts = listOf(Account("a", platform, "A"), Account("b", platform, "B"))
    private fun usage(percent: Float) = UsageInfo(platform, listOf(UsageItem("5h", percent)), updatedAt = time)

    private class Harness(
        val repository: UsageRepositoryImpl,
        val cache: UsageCache,
        val authEvents: MutableList<Platform>,
        val freshEvents: MutableList<UsageInfo>,
        val completed: MutableList<Pair<Platform, Boolean>>,
    )

    private fun TestScope.harness(fetch: suspend (Platform, String) -> UsageInfo): Harness {
        val cache = UsageCache(MemoryPreferences(), Gson(), { accounts }, { time })
        val authEvents = mutableListOf<Platform>()
        val freshEvents = mutableListOf<UsageInfo>()
        val completed = mutableListOf<Pair<Platform, Boolean>>()
        val repository = UsageRepositoryImpl(
            fetchUsage = fetch,
            accounts = { accounts },
            cache = cache,
            onSessionExpired = { authEvents.add(it) },
            onFreshUsage = { freshEvents.add(it) },
            onRefreshCompleted = { p, success -> completed.add(p to success) },
            now = { time },
            flights = UsageRefreshFlights(backgroundScope),
        )
        return Harness(repository, cache, authEvents, freshEvents, completed)
    }

    @Test fun overlappingEntrypointsShareOneResultAndCompletedWorkIsNotMemoized() = runTest {
        var calls = 0
        val release = CompletableDeferred<Unit>()
        val h = harness { _, _ -> calls++; release.await(); usage(32f) }
        val dashboard = async { h.repository.getUsage(platform, "a").getOrThrow() }
        val widget = async { h.repository.getClaudeUsage("a").getOrThrow() }
        runCurrent()
        assertEquals(1, calls)
        release.complete(Unit)
        assertSame(dashboard.await(), widget.await())
        assertEquals(1, h.freshEvents.size)
        assertEquals(listOf(platform to true), h.completed)
        h.repository.getUsage(platform, "a")
        assertEquals(2, calls)
    }

    @Test fun distinctAccountsRefreshIndependentlyAndNeverShareTheirCache() = runTest {
        val started = mutableSetOf<String>()
        val release = CompletableDeferred<Unit>()
        val h = harness { _, id ->
            started.add(id)
            release.await()
            usage(if (id == "a") 10f else 90f)
        }
        val all = async { h.repository.getAllUsage() }
        runCurrent()
        assertEquals(setOf("a", "b"), started)
        release.complete(Unit)
        val result = all.await().associateBy { it.accountId }
        assertEquals(10f, result["a"]!!.items.single().percent)
        assertEquals(90f, result["b"]!!.items.single().percent)
        assertEquals(setOf("a", "b"), h.repository.getCachedUsage().map { it.accountId }.toSet())
    }

    @Test fun cancellingOneWaiterDoesNotCancelTheOtherWaiter() = runTest {
        var upstreamCancelled = false
        val release = CompletableDeferred<Unit>()
        val h = harness { _, _ ->
            try { release.await(); usage(42f) }
            catch (e: CancellationException) { upstreamCancelled = true; throw e }
        }
        val first = async { h.repository.getUsage(platform, "a") }
        val second = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        first.cancelAndJoin()
        assertFalse(upstreamCancelled)
        release.complete(Unit)
        assertEquals(42f, second.await().getOrThrow().items.single().percent)
        assertNull(h.cache.getFailure(platform, "a"))
    }

    @Test fun cancellingLastWaiterCancelsUpstreamAndCleansFlightWithoutRecordingFailure() = runTest {
        var calls = 0
        var cancelled = false
        val h = harness { _, _ ->
            calls++
            if (calls == 1) {
                try { awaitCancellation() } finally { cancelled = true }
            }
            usage(25f)
        }
        val first = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        first.cancelAndJoin()
        runCurrent()
        assertTrue(cancelled)
        assertNull(h.cache.get(platform, "a"))
        assertTrue(h.completed.isEmpty())
        assertEquals(25f, h.repository.getUsage(platform, "a").getOrThrow().items.single().percent)
        assertEquals(2, calls)
    }

    @Test fun upstreamCancellationPropagatesAndLaterRefreshCanRecover() = runTest {
        var calls = 0
        val h = harness { _, _ ->
            if (++calls == 1) throw CancellationException("cancelled producer")
            usage(25f)
        }
        try {
            h.repository.getUsage(platform, "a")
            fail("Cancellation must not become a cached failure")
        } catch (_: CancellationException) { }
        assertNull(h.cache.get(platform, "a"))
        assertEquals(25f, h.repository.getUsage(platform, "a").getOrThrow().items.single().percent)
    }

    @Test fun authFailureKeepsLastGoodAndEmitsBeforeReturningFallback() = runTest {
        val h = harness { _, _ -> throw SessionExpiredException(platform) }
        val fetchedAt = time
        h.cache.save(platform, "a", usage(41f))
        time += 5000
        val result = h.repository.getClaudeUsage("a").getOrThrow()
        assertEquals(41f, result.items.single().percent)
        assertEquals(fetchedAt, result.updatedAt)
        assertTrue(result.stale)
        assertEquals(UsageFailureKind.AUTH_REQUIRED, result.refreshFailure!!.kind)
        assertEquals(listOf(platform), h.authEvents)
        assertEquals(listOf(platform to false), h.completed)
        assertTrue(h.freshEvents.isEmpty())
    }

    @Test fun firstFailureStillReturnsAVisibleCardAndDoesNotPreventOtherAccounts() = runTest {
        val h = harness { _, id -> if (id == "a") throw IOException("private upstream detail") else usage(18f) }
        val results = h.repository.getAllUsage().associateBy { it.accountId }
        val failed = results["a"]!!
        assertTrue(failed.items.isEmpty())
        assertTrue(failed.stale)
        assertEquals(0L, failed.updatedAt)
        assertEquals(UsageFailureKind.NETWORK, failed.refreshFailure!!.kind)
        assertFalse(failed.refreshFailure!!.message.contains("private"))
        assertEquals(18f, results["b"]!!.items.single().percent)
    }

    @Test fun retryAfterPreventsRequestsUntilDeadlineAndSuccessClearsFailure() = runTest {
        var calls = 0
        val retryAt = time + 60_000L
        val h = harness { _, _ ->
            if (++calls == 1) throw UsageRequestException(
                platform,
                UsageFailure(UsageFailureKind.RATE_LIMITED, 429, retryAt, time),
            )
            usage(64f)
        }
        val first = h.repository.getUsage(platform, "a").getOrThrow()
        time = retryAt - 1
        val blocked = h.repository.getUsage(platform, "a").getOrThrow()
        assertEquals(1, calls)
        assertEquals(first.refreshFailure, blocked.refreshFailure)
        assertTrue(blocked.items.isEmpty())
        time = retryAt
        val refreshed = h.repository.getUsage(platform, "a").getOrThrow()
        assertEquals(2, calls)
        assertNull(refreshed.refreshFailure)
        assertFalse(refreshed.stale)
        assertEquals(64f, h.cache.get(platform, "a")!!.items.single().percent)
    }

    @Test fun invalidationWaitsForOldProducerAndPreventsLateCacheWrites() = runTest {
        var calls = 0
        val releaseOld = CompletableDeferred<Unit>()
        val h = harness { _, _ ->
            if (++calls == 1) {
                // A blocking client may return after cancellation; repository must reject its result.
                withContext(NonCancellable) { releaseOld.await() }
                usage(91f)
            } else usage(7f)
        }
        h.cache.save(platform, "a", usage(80f))
        val old = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        val invalidation = async { h.repository.invalidateCache(platform, "a") }
        runCurrent()
        assertFalse(invalidation.isCompleted)
        val next = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        assertEquals(1, calls)
        releaseOld.complete(Unit)
        invalidation.await()
        assertTrue(old.isCancelled)
        assertEquals(7f, next.await().getOrThrow().items.single().percent)
        assertEquals(7f, h.cache.get(platform, "a")!!.items.single().percent)
        assertEquals(listOf(7f), h.freshEvents.map { it.items.single().percent })
    }

    @Test fun replacingCredentialsIsAtomicWithNewReadsAndCannotPublishTheOldIdentity() = runTest {
        var identity = "old"
        val releaseOld = CompletableDeferred<Unit>()
        val observedIdentities = mutableListOf<String>()
        val h = harness { _, _ ->
            val captured = identity
            observedIdentities.add(captured)
            if (captured == "old") withContext(NonCancellable) { releaseOld.await() }
            usage(if (captured == "old") 91f else 7f).copy(providerAccountId = captured)
        }
        h.cache.save(platform, "a", usage(80f).copy(providerAccountId = "old"))
        val old = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        val replacement = async {
            h.repository.updateAccount(platform, "a") { identity = "new" }
        }
        runCurrent()
        val newReader = async { h.repository.getUsage(platform, "a") }
        runCurrent()
        assertEquals(listOf("old"), observedIdentities)
        assertEquals("old", identity)
        releaseOld.complete(Unit)
        replacement.await()
        assertEquals("new", newReader.await().getOrThrow().providerAccountId)
        assertTrue(old.isCancelled)
        assertEquals(listOf("old", "new"), observedIdentities)
        assertEquals("new", h.cache.get(platform, "a")!!.providerAccountId)
        assertEquals(listOf("new"), h.freshEvents.map { it.providerAccountId })
    }

    @Test fun failedLoginCommitClearsPotentiallyMismatchedCacheAndReleasesTheGate() = runTest {
        val h = harness { _, _ -> usage(12f) }
        h.cache.save(platform, "a", usage(80f))
        try {
            h.repository.updateAccount(platform, "a") { throw IllegalStateException("stale attempt") }
            fail("Expected login rejection")
        } catch (_: IllegalStateException) { }
        assertNull(h.cache.get(platform, "a"))
        assertEquals(12f, h.repository.getUsage(platform, "a").getOrThrow().items.single().percent)
    }

    @Test fun cancelledLoginCheckPreservesExistingCache() = runTest {
        val h = harness { _, _ -> usage(12f) }
        h.cache.save(platform, "a", usage(80f))
        try {
            h.repository.updateAccount(platform, "a") { throw CancellationException("stale attempt") }
            fail("Expected cancellation")
        } catch (_: CancellationException) { }
        assertEquals(80f, h.cache.get(platform, "a")!!.items.single().percent)
    }

    @Test fun bulkImportWaitsForEveryOldReaderAndClearsEveryAccountCooldown() = runTest {
        var generation = "old"
        val releaseOld = CompletableDeferred<Unit>()
        val captured = mutableListOf<Pair<String, String>>()
        val h = harness { _, id ->
            val identity = generation
            captured.add(id to identity)
            if (identity == "old") withContext(NonCancellable) { releaseOld.await() }
            usage(if (identity == "old") 90f else 10f).copy(providerAccountId = "$identity-$id")
        }
        h.cache.save(platform, "a", usage(80f))
        h.cache.save(platform, "b", usage(70f))
        val oldA = async { h.repository.getUsage(platform, "a") }
        val oldB = async { h.repository.getUsage(platform, "b") }
        runCurrent()
        val change = async {
            // Unsorted and duplicate keys exercise the batch reservation boundary.
            h.repository.updateAccounts(listOf(platform to "b", platform to "a", platform to "a")) {
                generation = "new"
            }
        }
        runCurrent()
        val nextA = async { h.repository.getUsage(platform, "a") }
        val nextB = async { h.repository.getUsage(platform, "b") }
        runCurrent()
        assertFalse(change.isCompleted)
        assertEquals(2, captured.size)
        releaseOld.complete(Unit)
        change.await()
        assertEquals("new-a", nextA.await().getOrThrow().providerAccountId)
        assertEquals("new-b", nextB.await().getOrThrow().providerAccountId)
        assertTrue(oldA.isCancelled)
        assertTrue(oldB.isCancelled)

        for (id in listOf("a", "b")) h.cache.saveFailure(platform, id,
            UsageFailure(UsageFailureKind.RATE_LIMITED, 429, time + 600_000L, time))
        h.repository.updateAccounts(listOf(platform to "a", platform to "b")) { generation = "latest" }
        assertEquals("latest-a", h.repository.getUsage(platform, "a").getOrThrow().providerAccountId)
        assertEquals("latest-b", h.repository.getUsage(platform, "b").getOrThrow().providerAccountId)
    }

    @Test fun bulkImportFailureClearsAllAffectedCachesAndCanBeRetried() = runTest {
        val h = harness { _, _ -> usage(12f) }
        h.cache.save(platform, "a", usage(80f))
        h.cache.save(platform, "b", usage(70f))
        val keys = listOf(platform to "a", platform to "b")
        try {
            h.repository.updateAccounts(keys) { throw IOException("partial storage failure") }
            fail("Expected failure")
        } catch (_: IOException) { }
        assertNull(h.cache.get(platform, "a"))
        assertNull(h.cache.get(platform, "b"))
        h.repository.updateAccounts(keys.reversed()) { }
        assertEquals(12f, h.repository.getUsage(platform, "a").getOrThrow().items.single().percent)
    }
}
