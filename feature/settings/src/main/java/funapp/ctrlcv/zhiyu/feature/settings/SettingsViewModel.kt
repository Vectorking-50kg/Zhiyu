package funapp.ctrlcv.zhiyu.feature.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.data.notification.BalanceNotificationManager
import funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.BackupManager
import funapp.ctrlcv.zhiyu.core.storage.HomePlatformPreferences
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import funapp.ctrlcv.zhiyu.core.ui.theme.DEFAULT_THEME_ID
import funapp.ctrlcv.zhiyu.core.ui.theme.KEY_COLOR_MODE
import funapp.ctrlcv.zhiyu.core.ui.theme.KEY_THEME_ID
import funapp.ctrlcv.zhiyu.core.ui.theme.THEME_PREFS_NAME
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ApiKeyDialogState(
    val platform: Platform,
    val apiKey: String = ""
)

data class SettingsUiState(
    val loggedInPlatforms: Set<Platform> = emptySet(),
    val configuredApiPlatforms: Set<Platform> = emptySet(),
    val visibleHomePlatforms: Set<Platform> = Platform.entries.toSet(),
    val apiKeyDialog: ApiKeyDialogState? = null,
    val exportJson: String? = null,
    val showImportConfirm: Boolean = false,
    val backupMessage: String? = null,
    val colorMode: ColorMode = ColorMode.SYSTEM,
    val themeId: String = DEFAULT_THEME_ID,
    val showColorModeDialog: Boolean = false,
    val persistentNotificationEnabled: Boolean = false,
    val pinnedPlatforms: Set<Platform> = emptySet(),
    val usageAlertEnabled: Boolean = true,
    val resetReminderEnabled: Boolean = true,
    val sessionExpiredAlertEnabled: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val ctx: Context,
    private val accountStore: AccountStore,
    private val tokenStore: SecureTokenStore,
    private val backupManager: BackupManager,
    private val homePlatformPreferences: HomePlatformPreferences,
    private val notificationPrefs: NotificationPreferences,
    private val balanceNotifier: BalanceNotificationManager,
    private val repository: UsageRepository,
) : ViewModel() {

    private val themePrefs by lazy {
        ctx.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadAccounts()
        loadThemePrefs()
        loadNotificationPrefs()
        observeVisibleHomePlatforms()
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

    /** Re-read credentials after returning from a separate login destination. */
    fun onForeground() = loadAccounts()

    private fun loadNotificationPrefs() {
        _uiState.update {
            it.copy(
                persistentNotificationEnabled = notificationPrefs.persistentEnabled,
                pinnedPlatforms = notificationPrefs.pinnedPlatforms(),
                usageAlertEnabled = notificationPrefs.usageAlertEnabled,
                resetReminderEnabled = notificationPrefs.resetReminderEnabled,
                sessionExpiredAlertEnabled = notificationPrefs.sessionExpiredAlertEnabled,
            )
        }
    }

    private fun observeVisibleHomePlatforms() {
        viewModelScope.launch {
            homePlatformPreferences.visiblePlatforms.collect { visiblePlatforms ->
                _uiState.update { it.copy(visibleHomePlatforms = visiblePlatforms) }
            }
        }
    }

    fun toggleHomePlatform(platform: Platform) {
        homePlatformPreferences.setVisible(
            platform = platform,
            visible = platform !in _uiState.value.visibleHomePlatforms,
        )
    }

    fun setUsageAlertEnabled(enabled: Boolean) {
        notificationPrefs.usageAlertEnabled = enabled
        loadNotificationPrefs()
    }

    fun setResetReminderEnabled(enabled: Boolean) {
        notificationPrefs.resetReminderEnabled = enabled
        loadNotificationPrefs()
    }

    fun setSessionExpiredAlertEnabled(enabled: Boolean) {
        notificationPrefs.sessionExpiredAlertEnabled = enabled
        loadNotificationPrefs()
    }

    /** 已登录的网页平台 + 已配置密钥的 API 平台，即可固定到状态栏的候选平台。 */
    private fun configuredPlatforms(): Set<Platform> =
        _uiState.value.loggedInPlatforms + _uiState.value.configuredApiPlatforms

    /**
     * 切换常驻通知总开关。注意：调用方需先在 API 33+ 上确保已获得通知权限。
     * 首次开启且尚未选择平台时，默认固定所有已配置平台。
     */
    fun setPersistentNotificationEnabled(enabled: Boolean) {
        notificationPrefs.persistentEnabled = enabled
        if (enabled && notificationPrefs.pinnedPlatforms().isEmpty()) {
            configuredPlatforms().forEach { notificationPrefs.setPinned(it, true) }
        }
        balanceNotifier.refresh()
        loadNotificationPrefs()
    }

    fun togglePinnedPlatform(platform: Platform) {
        val pinned = notificationPrefs.pinnedPlatforms()
        notificationPrefs.setPinned(platform, platform !in pinned)
        balanceNotifier.refresh()
        loadNotificationPrefs()
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

        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateAccount(platform, "default") {
                    commitAccountMutation(platform, "default") {
                        tokenStore.save(platform, "default", apiKey, durable = true)
                        accountStore.saveAccount(
                            Account("default", platform, platform.displayName, "API"),
                            durable = true,
                        )
                    }
                }
                loadAccounts()
                _uiState.update {
                    if (it.apiKeyDialog == dialog) it.copy(apiKeyDialog = null) else it
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadAccounts()
                _uiState.update { it.copy(backupMessage = "密钥保存失败，请重试") }
            }
        }
    }

    fun clearApiKey(platform: Platform) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateAccount(platform, "default") {
                    commitAccountMutation(platform, "default") {
                        tokenStore.clear(platform, "default", durable = true)
                        accountStore.removeAccount(platform, "default", durable = true)
                    }
                }
                loadAccounts()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadAccounts()
                _uiState.update { it.copy(backupMessage = "密钥删除失败，请重试") }
            }
        }
    }

    /** Called only while repository holds the corresponding account gate. */
    private fun commitAccountMutation(platform: Platform, accountId: String, commit: () -> Unit) {
        val previousTokens = tokenStore.snapshot(platform, accountId)
        val previousAccount = accountStore.getAccounts(platform).firstOrNull { it.id == accountId }
        commitSettingsAccount(
            commit = commit,
            restore = {
                tokenStore.restore(platform, accountId, previousTokens)
                if (previousAccount != null) accountStore.saveAccount(previousAccount, durable = true)
                else accountStore.removeAccount(platform, accountId, durable = true)
            },
            quarantine = { tokenStore.clear(platform, accountId, durable = true) },
        )
    }

    fun prepareExport() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = backupManager.export()
                _uiState.update { it.copy(exportJson = json) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _uiState.update { it.copy(backupMessage = "导出失败，请重试") }
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
                val prepared = backupManager.prepareImport(json)
                repository.updateAccounts(prepared.affectedAccounts) { backupManager.import(prepared) }
                loadAccounts()
                _uiState.update { it.copy(backupMessage = "备份已成功导入") }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                loadAccounts()
                _uiState.update { it.copy(backupMessage = "导入失败，请检查备份文件后重试") }
            }
        }
    }

    fun clearBackupMessage() {
        _uiState.update { it.copy(backupMessage = null) }
    }
}
