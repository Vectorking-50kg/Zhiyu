package funapp.ctrlcv.zhiyu.feature.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class ApiKeyDialogState(
    val platform: Platform,
    val apiKey: String = ""
)

data class SettingsUiState(
    val loggedInPlatforms: Set<Platform> = emptySet(),
    val configuredApiPlatforms: Set<Platform> = emptySet(),
    val apiKeyDialog: ApiKeyDialogState? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val tokenStore: SecureTokenStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        val loggedIn = Platform.entries
            .filter { !it.requiresApiKey && accountStore.getAccounts(it).isNotEmpty() }
            .toSet()

        val configured = Platform.entries
            .filter { it.requiresApiKey && tokenStore.hasToken(it, "default") }
            .toSet()

        _uiState.update { it.copy(loggedInPlatforms = loggedIn, configuredApiPlatforms = configured) }
    }

    fun showApiKeyDialog(platform: Platform) {
        val existingKey = tokenStore.get(platform, "default") ?: ""
        _uiState.update {
            it.copy(apiKeyDialog = ApiKeyDialogState(platform, existingKey))
        }
    }

    fun dismissApiKeyDialog() {
        _uiState.update { it.copy(apiKeyDialog = null) }
    }

    fun updateApiKey(apiKey: String) {
        _uiState.update { state ->
            val dialog = state.apiKeyDialog ?: return@update state
            state.copy(apiKeyDialog = dialog.copy(apiKey = apiKey))
        }
    }

    fun saveApiKey() {
        val dialog = _uiState.value.apiKeyDialog ?: return
        val platform = dialog.platform
        val apiKey = dialog.apiKey.trim()

        if (apiKey.isEmpty()) return

        tokenStore.save(platform, "default", apiKey)

        accountStore.saveAccount(
            Account(
                id = "default",
                platform = platform,
                displayName = platform.displayName,
                planType = when (platform) {
                    Platform.MINIMAX -> "Token Plan"
                    else -> "API"
                }
            )
        )

        loadAccounts()
        _uiState.update { it.copy(apiKeyDialog = null) }
    }

    fun clearApiKey(platform: Platform) {
        tokenStore.clear(platform, "default")
        accountStore.removeAccount(platform, "default")
        loadAccounts()
    }
}
