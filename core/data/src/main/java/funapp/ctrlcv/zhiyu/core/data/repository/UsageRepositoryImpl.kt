package funapp.ctrlcv.zhiyu.core.data.repository

import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.data.notification.UsageAlertManager
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.NoCookieException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import funapp.ctrlcv.zhiyu.core.domain.model.toUsageFailure
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthSessionManager
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl internal constructor(
    private val fetchUsage: suspend (Platform, String) -> UsageInfo,
    private val accounts: () -> List<Account>,
    private val cache: UsageCache,
    private val onSessionExpired: (Platform) -> Unit,
    private val onFreshUsage: (UsageInfo) -> Unit,
    private val onRefreshCompleted: (Platform, Boolean) -> Unit = { _, _ -> },
    private val now: () -> Long = System::currentTimeMillis,
    private val flights: UsageRefreshFlights<Pair<Platform, String>, Result<UsageInfo>> = UsageRefreshFlights(),
    private val shouldRefresh: (Platform, String) -> Boolean = { _, _ -> true },
) : UsageRepository {
    // Account creation must not race a backup's snapshot/rollback across the two encrypted stores.
    // Ordinary quota reads still run concurrently per account.
    private val accountUpdates = Mutex()

    @Inject
    constructor(
        api: UsageApiService,
        tokenStore: SecureTokenStore,
        accountStore: AccountStore,
        cache: UsageCache,
        sessionEventBus: SessionEventBus,
        alertManager: UsageAlertManager,
        oauth: OAuthSessionManager,
    ) : this(
        fetchUsage = { platform, accountId ->
            if (oauth.hasSession(platform, accountId)) oauth.getUsage(platform, accountId)
            else fetchLegacyUsage(api, tokenStore, platform, accountId)
        },
        accounts = accountStore::getAllAccounts,
        cache = cache,
        onSessionExpired = { sessionEventBus.emit(SessionEvent.SessionExpired(it)) },
        onFreshUsage = alertManager::onUsageUpdated,
        onRefreshCompleted = { platform, success ->
            sessionEventBus.emit(SessionEvent.RefreshCompleted(platform, success))
        },
        shouldRefresh = { platform, id -> accountStore.getAccounts(platform).firstOrNull { it.id == id }?.monitoringEnabled != false },
    )

    override suspend fun getClaudeUsage(accountId: String) = getUsage(Platform.CLAUDE, accountId)
    override suspend fun getChatGptUsage(accountId: String) = getUsage(Platform.CHATGPT, accountId)
    override suspend fun getCursorUsage(accountId: String) = getUsage(Platform.CURSOR, accountId)
    override suspend fun getZenUsage(accountId: String) = getUsage(Platform.ZEN, accountId)
    override suspend fun getMiniMaxUsage(accountId: String) = getUsage(Platform.MINIMAX, accountId)
    override suspend fun getAiHubMixUsage(accountId: String) = getUsage(Platform.AIHUBMIX, accountId)
    override suspend fun getDeepSeekUsage(accountId: String) = getUsage(Platform.DEEPSEEK, accountId)

    override suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo> =
        flights.run(platform to accountId) {
            if (!shouldRefresh(platform, accountId)) {
                return@run Result.success(cache.get(platform, accountId)
                    ?: UsageInfo(platform, emptyList(), accountId = accountId, updatedAt = 0))
            }
            val previousFailure = cache.getFailure(platform, accountId)
            if (previousFailure?.kind == UsageFailureKind.RATE_LIMITED &&
                (previousFailure.retryAt ?: 0L) > now()) {
                return@run Result.success(checkNotNull(cache.get(platform, accountId)))
            }
            val usage = try {
                fetchUsage(platform, accountId)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                val failure = error.toUsageFailure(now()).let {
                    if (it.kind == UsageFailureKind.RATE_LIMITED && it.retryAt == null) {
                        it.copy(retryAt = now() + DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS)
                    } else it
                }
                // A previous successful snapshot must not hide a revoked credential.
                if (failure.requiresLogin && !platform.requiresApiKey) onSessionExpired(platform)
                cache.saveFailure(platform, accountId, failure)
                onRefreshCompleted(platform, false)
                return@run Result.success(checkNotNull(cache.get(platform, accountId)))
            }
            currentCoroutineContext().ensureActive()
            val fresh = usage.copy(accountId = accountId, stale = false, refreshFailure = null)
            cache.save(platform, accountId, fresh)
            onFreshUsage(fresh)
            onRefreshCompleted(platform, true)
            Result.success(fresh.atTime(now()))
        }

    override suspend fun getAllUsage(): List<UsageInfo> = coroutineScope {
        accounts().distinctBy { it.platform to it.id }.map { account ->
            async { getUsage(account.platform, account.id).getOrThrow() }
        }.awaitAll()
    }

    override fun getCachedUsage(): List<UsageInfo> = cache.getAll()

    override suspend fun invalidateCache(platform: Platform, accountId: String) = accountUpdates.withLock {
        flights.invalidate(platform to accountId) { cache.clear(platform, accountId) }
    }

    override suspend fun updateAccount(platform: Platform, accountId: String, validatedUsage: UsageInfo?, commit: () -> Unit) = accountUpdates.withLock {
        flights.invalidate(platform to accountId) {
            require(validatedUsage == null || validatedUsage.platform == platform)
            commitAccountChanges(listOf(platform to accountId), commit)
            cache.clear(platform, accountId)
            validatedUsage?.let { usage ->
                val fresh = usage.copy(accountId = accountId, stale = false, refreshFailure = null)
                cache.save(platform, accountId, fresh)
                onFreshUsage(fresh)
            }
        }
        onRefreshCompleted(platform, validatedUsage != null)
    }

    override suspend fun updateAccounts(accounts: Collection<Pair<Platform, String>>, commit: () -> Unit) = accountUpdates.withLock {
        val keys = accounts.distinct().sortedWith(compareBy({ it.first.key }, { it.second }))
        flights.invalidateAll(keys) {
            commitAccountChanges(keys, commit)
            keys.forEach { (platform, accountId) -> cache.clear(platform, accountId) }
        }
        keys.map { it.first }.distinct().forEach { onRefreshCompleted(it, false) }
    }

    private fun commitAccountChanges(keys: List<Pair<Platform, String>>, commit: () -> Unit) {
        try {
            commit()
        } catch (cancelled: CancellationException) {
            throw cancelled // A pre-write login cancellation leaves the current cache intact.
        } catch (error: Exception) {
            // Two encrypted stores may have partially committed; do not retain old identity data.
            keys.forEach { (platform, accountId) -> cache.clear(platform, accountId) }
            keys.map { it.first }.distinct().forEach { onRefreshCompleted(it, false) }
            throw error
        }
    }

    private companion object {
        const val DEFAULT_RATE_LIMIT_COOLDOWN_MILLIS = 60_000L
    }
}

private suspend fun fetchLegacyUsage(
    api: UsageApiService,
    tokenStore: SecureTokenStore,
    platform: Platform,
    accountId: String,
): UsageInfo {
    val credential = tokenStore.get(platform, accountId) ?: throw NoCookieException(platform)
    return when (platform) {
        Platform.CLAUDE -> api.getClaudeUsage(credential, api.getClaudeOrgInfo(credential))
        Platform.CHATGPT -> api.getChatGptUsage(credential)
        Platform.CURSOR -> api.getCursorUsage(credential)
        Platform.ZEN -> api.getZenUsage(
            credential,
            tokenStore.getExtra(platform, accountId, SecureTokenStore.EXTRA_ZEN_WORKSPACE_ID),
        )
        Platform.MINIMAX -> api.getMiniMaxUsage(credential)
        Platform.AIHUBMIX -> api.getAiHubMixUsage(credential)
        Platform.DEEPSEEK -> api.getDeepSeekUsage(credential)
    }
}
