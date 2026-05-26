package funapp.ctrlcv.zhiyu.core.domain.model

sealed class SessionEvent {
    data class SessionExpired(val platform: Platform) : SessionEvent()
    data class RefreshCompleted(val platform: Platform, val success: Boolean) : SessionEvent()
}
