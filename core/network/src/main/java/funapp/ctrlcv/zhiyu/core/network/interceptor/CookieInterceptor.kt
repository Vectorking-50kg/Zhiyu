package funapp.ctrlcv.zhiyu.core.network.interceptor

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
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
    private val eventBus: SessionEventBus
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        when (response.code) {
            401, 403 -> {
                val platform = chain.request().tag(Platform::class.java)
                platform?.let { eventBus.emit(SessionEvent.SessionExpired(it)) }
            }
        }
        return response
    }
}
