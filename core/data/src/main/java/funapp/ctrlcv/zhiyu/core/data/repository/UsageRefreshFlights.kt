package funapp.ctrlcv.zhiyu.core.data.repository

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Shares only overlapping work; the last departing caller cancels the upstream request. */
internal class UsageRefreshFlights<K, V>(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    private class Flight<V>(val result: Deferred<V>, var waiters: Int = 0)
    private val lock = Any()
    private val flights = mutableMapOf<K, Flight<V>>()
    private val invalidations = mutableMapOf<K, CompletableDeferred<Unit>>()

    suspend fun run(key: K, fetch: suspend () -> V): V {
        while (true) {
            currentCoroutineContext().ensureActive()
            var invalidation: CompletableDeferred<Unit>? = null
            val flight = synchronized(lock) {
                invalidations[key]?.let {
                    invalidation = it
                    return@synchronized null
                }
                val current = flights[key]?.takeUnless { it.result.isCompleted || it.result.isCancelled }
                    ?: Flight(scope.async(start = CoroutineStart.LAZY) { fetch() }).also { created ->
                        flights[key] = created
                        created.result.invokeOnCompletion {
                            synchronized(lock) {
                                if (flights[key] === created) flights.remove(key)
                            }
                        }
                    }
                current.waiters++
                current
            }
            if (flight == null) {
                invalidation!!.await()
                continue
            }
            try {
                currentCoroutineContext().ensureActive()
                flight.result.start()
                return flight.result.await()
            } finally {
                synchronized(lock) {
                    flight.waiters--
                    if (flight.waiters == 0 && !flight.result.isCompleted) {
                        if (flights[key] === flight) flights.remove(key)
                        flight.result.cancel()
                    }
                }
            }
        }
    }

    /** Blocks new refreshes until the old producer has stopped and its data has been cleared. */
    suspend fun invalidate(key: K, clear: () -> Unit) = invalidateAll(listOf(key), clear)

    /** Reserve the whole set at once, so overlapping imports never hold partial locks. */
    suspend fun invalidateAll(keys: Collection<K>, clear: () -> Unit) {
        val distinctKeys = keys.distinct()
        while (true) {
            val callerContext = currentCoroutineContext()
            callerContext.ensureActive()
            val gate = CompletableDeferred<Unit>()
            var previousGate: CompletableDeferred<Unit>? = null
            val previousFlights = synchronized(lock) {
                distinctKeys.firstNotNullOfOrNull { invalidations[it] }?.let {
                    previousGate = it
                    return@synchronized emptyList()
                }
                distinctKeys.forEach { invalidations[it] = gate }
                distinctKeys.mapNotNull { flights.remove(it) }
            }
            if (previousGate != null) {
                previousGate!!.await()
                continue
            }
            try {
                // Do not release the gate with a live old writer if the login screen closes.
                withContext(NonCancellable) {
                    previousFlights.forEach { it.result.cancel() }
                    previousFlights.forEach { it.result.join() }
                    callerContext.ensureActive()
                    clear()
                }
            } finally {
                synchronized(lock) {
                    distinctKeys.forEach { if (invalidations[it] === gate) invalidations.remove(it) }
                    gate.complete(Unit)
                }
            }
            return
        }
    }
}
