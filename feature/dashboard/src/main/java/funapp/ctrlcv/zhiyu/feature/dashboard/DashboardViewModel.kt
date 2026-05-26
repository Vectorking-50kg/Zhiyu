package funapp.ctrlcv.zhiyu.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.data.worker.RefreshWorker
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DashboardUiState(
    val usageList: List<UsageInfo> = emptyList(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long = 0L,
    val authRequired: Platform? = null,
    val error: String? = null
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val repository: UsageRepository,
    private val sessionEventBus: SessionEventBus
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        loadUsage()
        observeSessionEvents()
    }

    fun loadUsage() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val usageList = repository.getAllUsage()
                _uiState.update {
                    it.copy(
                        usageList = usageList,
                        isRefreshing = false,
                        lastUpdated = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isRefreshing = false, error = e.message) }
            }
        }
    }

    fun refresh() {
        RefreshWorker.refreshNow(application)
        loadUsage()
    }

    fun dismissAuthRequired() {
        _uiState.update { it.copy(authRequired = null) }
    }

    private fun observeSessionEvents() {
        viewModelScope.launch {
            sessionEventBus.events.collect { event ->
                when (event) {
                    is SessionEvent.SessionExpired -> {
                        if (!event.platform.requiresApiKey) {
                            _uiState.update { it.copy(authRequired = event.platform) }
                        } else {
                            _uiState.update {
                                it.copy(error = "${event.platform.displayName} API 密钥无效，请在设置中重新配置")
                            }
                        }
                    }
                    is SessionEvent.RefreshCompleted -> {
                        if (event.success) loadUsage()
                    }
                }
            }
        }
    }
}
