package funapp.ctrlcv.zhiyu.core.data.repository

import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.data.notification.UsageAlertManager
import funapp.ctrlcv.zhiyu.core.domain.model.NoCookieException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val api: UsageApiService,
    private val tokenStore: SecureTokenStore,
    private val accountStore: AccountStore,
    private val cache: UsageCache,
    private val sessionEventBus: SessionEventBus,
    private val alertManager: UsageAlertManager
) : UsageRepository {

    /** 新鲜数据的统一落地点：写缓存并驱动阈值 / 重置提醒评估。 */
    private fun store(platform: Platform, usage: UsageInfo): UsageInfo {
        cache.save(platform, usage)
        alertManager.onUsageUpdated(usage)
        return usage
    }

    override suspend fun getClaudeUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CLAUDE, accountId)
            ?: throw NoCookieException(Platform.CLAUDE)
        val orgInfo = api.getClaudeOrgInfo(cookie)
        val usage = api.getClaudeUsage(cookie, orgInfo)
        store(Platform.CLAUDE, usage)
    }.recoverCatching { e ->
        cache.get(Platform.CLAUDE)?.copy(stale = true) ?: throw e
    }

    override suspend fun getChatGptUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CHATGPT, accountId)
            ?: throw NoCookieException(Platform.CHATGPT)
        val usage = api.getChatGptUsage(cookie)
        store(Platform.CHATGPT, usage)
    }.recoverCatching { e ->
        cache.get(Platform.CHATGPT)?.copy(stale = true) ?: throw e
    }

    override suspend fun getCursorUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CURSOR, accountId)
            ?: throw NoCookieException(Platform.CURSOR)
        val usage = api.getCursorUsage(cookie)
        store(Platform.CURSOR, usage)
    }.recoverCatching { e ->
        cache.get(Platform.CURSOR)?.copy(stale = true) ?: throw e
    }

    override suspend fun getZenUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.ZEN, accountId)
            ?: throw NoCookieException(Platform.ZEN)
        val workspaceId = tokenStore.getExtra(
            Platform.ZEN, accountId, SecureTokenStore.EXTRA_ZEN_WORKSPACE_ID
        )
        val usage = api.getZenUsage(cookie, workspaceId)
        store(Platform.ZEN, usage)
    }.recoverCatching { e ->
        cache.get(Platform.ZEN)?.copy(stale = true) ?: throw e
    }

    override suspend fun getMiniMaxUsage(accountId: String): Result<UsageInfo> = runCatching {
        val apiKey = tokenStore.get(Platform.MINIMAX, accountId)
            ?: throw NoCookieException(Platform.MINIMAX)
        val usage = api.getMiniMaxUsage(apiKey)
        store(Platform.MINIMAX, usage)
    }.recoverCatching { e ->
        cache.get(Platform.MINIMAX)?.copy(stale = true) ?: throw e
    }

    override suspend fun getAiHubMixUsage(accountId: String): Result<UsageInfo> = runCatching {
        val token = tokenStore.get(Platform.AIHUBMIX, accountId)
            ?: throw NoCookieException(Platform.AIHUBMIX)
        val usage = api.getAiHubMixUsage(token)
        store(Platform.AIHUBMIX, usage)
    }.recoverCatching { e ->
        cache.get(Platform.AIHUBMIX)?.copy(stale = true) ?: throw e
    }

    override suspend fun getDeepSeekUsage(accountId: String): Result<UsageInfo> = runCatching {
        val apiKey = tokenStore.get(Platform.DEEPSEEK, accountId)
            ?: throw NoCookieException(Platform.DEEPSEEK)
        val usage = api.getDeepSeekUsage(apiKey)
        store(Platform.DEEPSEEK, usage)
    }.recoverCatching { e ->
        cache.get(Platform.DEEPSEEK)?.copy(stale = true) ?: throw e
    }

    override suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo> {
        return when (platform) {
            Platform.CLAUDE -> getClaudeUsage(accountId)
            Platform.CHATGPT -> getChatGptUsage(accountId)
            Platform.CURSOR -> getCursorUsage(accountId)
            Platform.ZEN -> getZenUsage(accountId)
            Platform.MINIMAX -> getMiniMaxUsage(accountId)
            Platform.AIHUBMIX -> getAiHubMixUsage(accountId)
            Platform.DEEPSEEK -> getDeepSeekUsage(accountId)
        }
    }

    override suspend fun getAllUsage(): List<UsageInfo> {
        val results = mutableListOf<UsageInfo>()
        for (account in accountStore.getAllAccounts()) {
            val result = getUsage(account.platform, account.id)
            result.getOrNull()?.let { results.add(it) }
            // Surface auth failures: emit SessionExpired so the UI can prompt re-login.
            // getOrNull() would silently swallow these, leaving the user with no card and no hint.
            val error = result.exceptionOrNull()
            if (error is SessionExpiredException && !account.platform.requiresApiKey) {
                sessionEventBus.emit(SessionEvent.SessionExpired(account.platform))
            }
        }
        return results.ifEmpty { cache.getAll() }
    }

    override fun getCachedUsage(): List<UsageInfo> = cache.getAll()
}
