package funapp.ctrlcv.zhiyu.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

data class SettingsUiState(
    val loggedInPlatforms: Set<Platform> = emptySet()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountStore: AccountStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        val platforms = Platform.entries.filter { platform ->
            accountStore.getAccounts(platform).isNotEmpty()
        }.toSet()
        _uiState.value = SettingsUiState(loggedInPlatforms = platforms)
    }
}
