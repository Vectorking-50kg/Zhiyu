package funapp.ctrlcv.zhiyu.feature.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionEvent
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import funapp.ctrlcv.zhiyu.core.domain.model.toUsageFailure
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import funapp.ctrlcv.zhiyu.core.storage.HomePlatformPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

data class DashboardUiState(
    val usageList: List<UsageInfo> = emptyList(),
    val visiblePlatforms: Set<Platform> = Platform.entries.toSet(),
    val isRefreshing: Boolean = false,
    val lastUpdated: Long = 0L,
    val authRequired: Platform? = null,
    val error: String? = null,
    val currentTime: Long = System.currentTimeMillis()
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val application: Application,
    private val repository: UsageRepository,
    private val sessionEventBus: SessionEventBus,
    private val homePlatformPreferences: HomePlatformPreferences,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()
    private var refreshJob: Job? = null

    init {
        observeVisiblePlatforms()
        viewModelScope.launch {
            // 先把缓存数据立刻渲染，避免首次打开空白屏
            val cached = repository.getCachedUsage()
            if (cached.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        usageList = cached,
                        lastUpdated = cached.maxOf { info -> info.updatedAt }
                    )
                }
            }
            // 缓存展示后再触发网络刷新
            loadUsage()
        }
        observeSessionEvents()
    }

    fun loadUsage() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            try {
                val usageList = repository.getAllUsage()
                _uiState.update {
                    it.copy(
                        usageList = usageList.map { info -> info.atTime() },
                        isRefreshing = false,
                        lastUpdated = usageList.filter { info -> info.items.isNotEmpty() }
                            .maxOfOrNull { info -> info.updatedAt } ?: 0L,
                        currentTime = System.currentTimeMillis()
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.toUsageFailure().message) }
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun refresh() {
        // The visible screen owns this refresh; scheduling a second worker here duplicates it.
        loadUsage()
    }

    fun updateClock() {
        val now = System.currentTimeMillis()
        _uiState.update { state -> state.copy(
            currentTime = now,
            usageList = state.usageList.map { it.atTime(now) }
        ) }
    }

    fun dismissAuthRequired() {
        _uiState.update { it.copy(authRequired = null) }
    }

    private fun observeSessionEvents() {
        viewModelScope.launch {
            sessionEventBus.events.collect { event ->
                when (event) {
                    is SessionEvent.SessionExpired -> {
                        if (event.platform !in _uiState.value.visiblePlatforms) {
                            return@collect
                        }
                        if (!event.platform.requiresApiKey) {
                            _uiState.update { it.copy(authRequired = event.platform) }
                        } else {
                            _uiState.update {
                                it.copy(error = "${event.platform.displayName} API 密钥无效，请在设置中重新配置")
                            }
                        }
                    }
                    is SessionEvent.RefreshCompleted -> {
                        // A worker has already fetched; use its cache instead of fetching again.
                        val cached = repository.getCachedUsage()
                        _uiState.update { state -> state.copy(
                            usageList = cached,
                            lastUpdated = cached.filter { it.items.isNotEmpty() }.maxOfOrNull { it.updatedAt } ?: 0L,
                            currentTime = System.currentTimeMillis()
                        ) }
                    }
                }
            }
        }
    }

    private fun observeVisiblePlatforms() {
        viewModelScope.launch {
            homePlatformPreferences.visiblePlatforms.collect { visiblePlatforms ->
                _uiState.update {
                    it.copy(
                        visiblePlatforms = visiblePlatforms,
                        authRequired = it.authRequired?.takeIf { platform ->
                            platform in visiblePlatforms
                        },
                    )
                }
            }
        }
    }
}
