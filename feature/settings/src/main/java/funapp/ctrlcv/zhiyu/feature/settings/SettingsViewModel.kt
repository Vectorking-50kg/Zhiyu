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
    val apiKey: String = "",
    val groupId: String = ""   // 仅 MiniMax 需要
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
        val existingGroupId = if (platform == Platform.MINIMAX) {
            tokenStore.getExtra(platform, "default", "group_id") ?: ""
        } else ""
        _uiState.update {
            it.copy(apiKeyDialog = ApiKeyDialogState(platform, existingKey, existingGroupId))
        }
    }

    fun dismissApiKeyDialog() {
        _uiState.update { it.copy(apiKeyDialog = null) }
    }

    fun updateApiKeyDialogField(apiKey: String? = null, groupId: String? = null) {
        _uiState.update { state ->
            val dialog = state.apiKeyDialog ?: return@update state
            state.copy(
                apiKeyDialog = dialog.copy(
                    apiKey = apiKey ?: dialog.apiKey,
                    groupId = groupId ?: dialog.groupId
                )
            )
        }
    }

    fun saveApiKey() {
        val dialog = _uiState.value.apiKeyDialog ?: return
        val platform = dialog.platform
        val apiKey = dialog.apiKey.trim()
        val groupId = dialog.groupId.trim()

        if (apiKey.isEmpty()) return
        if (platform == Platform.MINIMAX && groupId.isEmpty()) return

        tokenStore.save(platform, "default", apiKey)
        if (platform == Platform.MINIMAX) {
            tokenStore.saveExtra(platform, "default", "group_id", groupId)
        }

        accountStore.saveAccount(
            Account(
                id = "default",
                platform = platform,
                displayName = platform.displayName,
                planType = "API"
            )
        )

        loadAccounts()
        _uiState.update { it.copy(apiKeyDialog = null) }
    }

    fun clearApiKey(platform: Platform) {
        tokenStore.clear(platform, "default")
        if (platform == Platform.MINIMAX) {
            tokenStore.clearExtra(platform, "default", "group_id")
        }
        accountStore.removeAccount(platform, "default")
        loadAccounts()
    }
}
