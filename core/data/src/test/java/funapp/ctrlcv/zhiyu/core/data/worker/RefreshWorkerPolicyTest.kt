package funapp.ctrlcv.zhiyu.core.data.worker

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class RefreshWorkerPolicyTest {
    private fun outcome(kind: UsageFailureKind?) = Result.success(UsageInfo(
        Platform.CLAUDE, emptyList(), stale = kind != null,
        refreshFailure = kind?.let { UsageFailure(it) },
    ))

    @Test fun workerRecognizesTransientFailureEvenWhenRepositoryReturnsCachedSuccess() {
        assertTrue(shouldRetryRefresh(listOf(outcome(null), outcome(UsageFailureKind.NETWORK))))
        assertTrue(shouldRetryRefresh(listOf(outcome(UsageFailureKind.SERVER))))
    }

    @Test fun authenticationAndRateLimitDoNotCauseWorkerRetryLoops() {
        assertFalse(shouldRetryRefresh(listOf(outcome(null))))
        for (kind in listOf(UsageFailureKind.AUTH_REQUIRED, UsageFailureKind.PERMISSION_DENIED,
            UsageFailureKind.RATE_LIMITED, UsageFailureKind.INVALID_RESPONSE, UsageFailureKind.UNKNOWN)) {
            assertFalse(kind.name, shouldRetryRefresh(listOf(outcome(kind))))
        }
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNeverConvertedToRetry() {
        shouldRetryRefresh(listOf(Result.failure(CancellationException())))
    }
}
