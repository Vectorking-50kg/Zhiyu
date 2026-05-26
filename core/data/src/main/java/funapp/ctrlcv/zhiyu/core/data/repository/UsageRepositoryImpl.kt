package funapp.ctrlcv.zhiyu.core.data.repository

import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.domain.model.NoCookieException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepositoryImpl @Inject constructor(
    private val api: UsageApiService,
    private val tokenStore: SecureTokenStore,
    private val accountStore: AccountStore,
    private val cache: UsageCache
) : UsageRepository {

    override suspend fun getClaudeUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CLAUDE, accountId)
            ?: throw NoCookieException(Platform.CLAUDE)
        val orgId = api.getClaudeOrganizationId(cookie)
        val usage = api.getClaudeUsage(cookie, orgId)
        cache.save(Platform.CLAUDE, usage)
        usage
    }.recoverCatching { e ->
        cache.get(Platform.CLAUDE)?.copy(stale = true) ?: throw e
    }

    override suspend fun getChatGptUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CHATGPT, accountId)
            ?: throw NoCookieException(Platform.CHATGPT)
        val usage = api.getChatGptUsage(cookie)
        cache.save(Platform.CHATGPT, usage)
        usage
    }.recoverCatching { e ->
        cache.get(Platform.CHATGPT)?.copy(stale = true) ?: throw e
    }

    override suspend fun getCursorUsage(accountId: String): Result<UsageInfo> = runCatching {
        val cookie = tokenStore.get(Platform.CURSOR, accountId)
            ?: throw NoCookieException(Platform.CURSOR)
        val usage = api.getCursorUsage(cookie)
        cache.save(Platform.CURSOR, usage)
        usage
    }.recoverCatching { e ->
        cache.get(Platform.CURSOR)?.copy(stale = true) ?: throw e
    }

    override suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo> {
        return when (platform) {
            Platform.CLAUDE -> getClaudeUsage(accountId)
            Platform.CHATGPT -> getChatGptUsage(accountId)
            Platform.CURSOR -> getCursorUsage(accountId)
        }
    }

    override suspend fun getAllUsage(): List<UsageInfo> {
        val results = mutableListOf<UsageInfo>()
        for (account in accountStore.getAllAccounts()) {
            getUsage(account.platform, account.id).getOrNull()?.let { results.add(it) }
        }
        return results.ifEmpty { cache.getAll() }
    }
}
