package funapp.ctrlcv.zhiyu.core.network.interceptor

import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionEventBus @Inject constructor() {
    val events = MutableSharedFlow<SessionEvent>(extraBufferCapacity = 10)

    fun emit(event: SessionEvent) {
        events.tryEmit(event)
    }
}

class CookieInterceptor(
    @Suppress("UNUSED_PARAMETER") eventBus: SessionEventBus
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // A first 401 may be recovered by token refresh, or belong to a login validation.
        // Only the repository knows when an established account truly needs a new login.
        return chain.proceed(chain.request())
    }
}
