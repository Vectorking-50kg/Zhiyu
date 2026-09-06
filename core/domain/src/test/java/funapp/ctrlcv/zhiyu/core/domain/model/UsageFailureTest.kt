package funapp.ctrlcv.zhiyu.core.domain.model

import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class UsageFailureTest {
    @Test fun `failures never expose exception or response text`() {
        val failure = IllegalStateException("Bearer secret-value").toUsageFailure(1234)
        assertEquals(UsageFailureKind.UNKNOWN, failure.kind)
        assertFalse(failure.message.contains("secret-value"))
        assertFalse(failure.requiresLogin)
    }

    @Test fun `rate limit retains its retry time`() {
        val failure = RateLimitException(Platform.CLAUDE, 120).toUsageFailure(1000)
        assertEquals(121000L, failure.retryAt)
        assertTrue(failure.retryable)
        assertFalse(failure.requiresLogin)
    }

    @Test fun `forbidden and temporary server failures do not request login`() {
        assertFalse(PermissionDeniedException(Platform.CHATGPT).toUsageFailure().requiresLogin)
        val error = UsageRequestException(Platform.CHATGPT, UsageFailure(UsageFailureKind.SERVER, 503))
        assertEquals(UsageFailureKind.SERVER, error.toUsageFailure().kind)
        assertFalse(error.toUsageFailure().requiresLogin)
    }

    @Test fun `long retry deadline is not shortened or overflowed`() {
        assertEquals(172801000L, RateLimitException(Platform.CLAUDE, 172800).toUsageFailure(1000).retryAt)
        assertEquals(Long.MAX_VALUE, RateLimitException(Platform.CLAUDE, Long.MAX_VALUE).toUsageFailure(1000).retryAt)
    }

    @Test fun `API key errors guide to configuration rather than web login`() {
        val failure = UsageFailure(UsageFailureKind.AUTH_REQUIRED)
        assertEquals("API 密钥无效，请检查后重试", failure.messageFor(Platform.DEEPSEEK))
        assertEquals("登录已失效，请重新登录", failure.messageFor(Platform.CLAUDE))
    }

    @Test(expected = CancellationException::class)
    fun `cancellation cannot be converted to a cached error`() {
        CancellationException("cancel").toUsageFailure()
    }
}
