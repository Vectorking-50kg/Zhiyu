package funapp.ctrlcv.zhiyu.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.BackupManager
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val THEME_PREFS = "zhiyu_theme_prefs"
private const val KEY_COLOR_MODE = "colorMode"
private const val KEY_THEME_ID = "themeId"
private const val DEFAULT_THEME_ID = "zhiyu"

data class ApiKeyDialogState(
    val platform: Platform,
    val apiKey: String = ""
)

data class SettingsUiState(
    val loggedInPlatforms: Set<Platform> = emptySet(),
    val configuredApiPlatforms: Set<Platform> = emptySet(),
    val apiKeyDialog: ApiKeyDialogState? = null,
    val exportJson: String? = null,
    val showImportConfirm: Boolean = false,
    val backupMessage: String? = null,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val themeId: String = DEFAULT_THEME_ID,
    val showColorModeDialog: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val accountStore: AccountStore,
    private val tokenStore: SecureTokenStore,
    private val backupManager: BackupManager
) : ViewModel() {

    private val themePrefs by lazy {
        ctx.getSharedPreferences(THEME_PREFS, Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
        loadThemePrefs()
    }

    private fun loadThemePrefs() {
        val colorModeStr = themePrefs.getString(KEY_COLOR_MODE, ColorMode.SYSTEM.name) ?: ColorMode.SYSTEM.name
        val colorMode = ColorMode.entries.firstOrNull { it.name == colorModeStr } ?: ColorMode.SYSTEM
        val themeId = themePrefs.getString(KEY_THEME_ID, DEFAULT_THEME_ID) ?: DEFAULT_THEME_ID
        _uiState.update { it.copy(colorMode = colorMode, themeId = themeId) }
    }

    fun setColorMode(mode: ColorMode) {
        themePrefs.edit().putString(KEY_COLOR_MODE, mode.name).apply()
        _uiState.update { it.copy(colorMode = mode, showColorModeDialog = false) }
    }

    fun setThemeId(id: String) {
        themePrefs.edit().putString(KEY_THEME_ID, id).apply()
        _uiState.update { it.copy(themeId = id) }
    }

    fun showColorModeDialog() {
        _uiState.update { it.copy(showColorModeDialog = true) }
    }

    fun dismissColorModeDialog() {
        _uiState.update { it.copy(showColorModeDialog = false) }
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
                planType = "API"
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

    fun prepareExport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = backupManager.export()
                _uiState.update { it.copy(exportJson = json) }
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "导出失败：${e.message}") }
            }
        }
    }

    fun onExportHandled() {
        _uiState.update { it.copy(exportJson = null) }
    }

    fun onExportWritten() {
        _uiState.update { it.copy(backupMessage = "备份已导出") }
    }

    fun showImportConfirm() {
        _uiState.update { it.copy(showImportConfirm = true) }
    }

    fun dismissImportConfirm() {
        _uiState.update { it.copy(showImportConfirm = false) }
    }

    fun importData(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                backupManager.import(json)
                loadAccounts()
                _uiState.update { it.copy(backupMessage = "备份已成功导入") }
            } catch (e: Exception) {
                _uiState.update { it.copy(backupMessage = "导入失败：${e.message}") }
            }
        }
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }
}
