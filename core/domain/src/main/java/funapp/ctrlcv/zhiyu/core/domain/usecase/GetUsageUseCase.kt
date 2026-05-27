package funapp.ctrlcv.zhiyu.core.domain.usecase

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo

interface UsageRepository {
    suspend fun getClaudeUsage(accountId: String): Result<UsageInfo>
    suspend fun getChatGptUsage(accountId: String): Result<UsageInfo>
    suspend fun getCursorUsage(accountId: String): Result<UsageInfo>
    suspend fun getMiniMaxUsage(accountId: String): Result<UsageInfo>
    suspend fun getAiHubMixUsage(accountId: String): Result<UsageInfo>
    suspend fun getDeepSeekUsage(accountId: String): Result<UsageInfo>
    suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo>
    suspend fun getAllUsage(): List<UsageInfo>
    fun getCachedUsage(): List<UsageInfo>
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
