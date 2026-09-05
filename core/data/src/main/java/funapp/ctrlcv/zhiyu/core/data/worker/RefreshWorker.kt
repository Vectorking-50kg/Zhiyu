package funapp.ctrlcv.zhiyu.core.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import funapp.ctrlcv.zhiyu.core.data.notification.BalanceNotificationManager
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.toUsageFailure
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.TimeUnit

@HiltWorker
class RefreshWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val repository: UsageRepository,
    private val accountStore: AccountStore,
    private val balanceNotifier: BalanceNotificationManager
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = coroutineScope {
        val accounts = accountStore.getAllAccounts()
        if (accounts.isEmpty()) {
            balanceNotifier.refresh()
            return@coroutineScope Result.success()
        }

        val results = accounts.map { account ->
            async {
                repository.getUsage(account.platform, account.id)
            }
        }.awaitAll()

        // 缓存已在 repository 内更新，依据最新数据重建状态栏常驻通知（未开启时内部自动跳过）
        balanceNotifier.refresh()

        if (shouldRetryRefresh(results)) Result.retry() else Result.success()
    }

    companion object {
        private const val WORK_NAME = "usage_refresh"
        private const val IMMEDIATE_WORK_NAME = "usage_refresh_now"

        fun schedule(ctx: Context) {
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun refreshNow(ctx: Context) {
            val request = androidx.work.OneTimeWorkRequestBuilder<RefreshWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(ctx).enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
    }
}

/** Retry transient outages, but leave login, permission and Retry-After handling to the next trigger. */
internal fun shouldRetryRefresh(results: List<kotlin.Result<UsageInfo>>): Boolean = results.any { result ->
    val failure = result.exceptionOrNull()?.toUsageFailure() ?: result.getOrNull()?.refreshFailure
    failure?.kind == UsageFailureKind.NETWORK || failure?.kind == UsageFailureKind.SERVER
}
