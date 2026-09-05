package funapp.ctrlcv.zhiyu.core.domain.usecase

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo

interface UsageRepository {
    suspend fun getClaudeUsage(accountId: String): Result<UsageInfo>
    suspend fun getChatGptUsage(accountId: String): Result<UsageInfo>
    suspend fun getCursorUsage(accountId: String): Result<UsageInfo>
    suspend fun getZenUsage(accountId: String): Result<UsageInfo>
    suspend fun getMiniMaxUsage(accountId: String): Result<UsageInfo>
    suspend fun getAiHubMixUsage(accountId: String): Result<UsageInfo>
    suspend fun getDeepSeekUsage(accountId: String): Result<UsageInfo>
    suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo>
    suspend fun getAllUsage(): List<UsageInfo>
    fun getCachedUsage(): List<UsageInfo>
    /** Invalidate data and any in-flight refresh before replacing an account's credentials. */
    suspend fun invalidateCache(platform: Platform, accountId: String)
    /** Keep refreshes blocked while the caller commits a validated account session. */
    suspend fun updateAccount(platform: Platform, accountId: String, validatedUsage: UsageInfo? = null, commit: () -> Unit)
    /** Atomically block affected account refreshes for one backup/import commit. */
    suspend fun updateAccounts(accounts: Collection<Pair<Platform, String>>, commit: () -> Unit)
}

class GetUsageUseCase(private val repository: UsageRepository) {
    suspend operator fun invoke(platform: Platform, accountId: String): Result<UsageInfo> {
        return repository.getUsage(platform, accountId)
    }
}

class GetAllUsageUseCase(private val repository: UsageRepository) {
    suspend operator fun invoke(): List<UsageInfo> {
        return repository.getAllUsage()
    }
}
