package funapp.ctrlcv.zhiyu.ui

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.data.notification.BalanceNotificationManager
import funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences
import funapp.ctrlcv.zhiyu.core.data.worker.RefreshWorker
import funapp.ctrlcv.zhiyu.core.domain.model.*
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import funapp.ctrlcv.zhiyu.core.storage.*
import funapp.ctrlcv.zhiyu.core.ui.theme.KEY_COLOR_MODE
import funapp.ctrlcv.zhiyu.core.ui.theme.THEME_PREFS_NAME
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.UUID
import javax.inject.Inject

enum class MonitorPage { OVERVIEW, ACCOUNTS, SETTINGS }
enum class PanelKind { PROVIDERS, DETAIL, APPEARANCE, NOTIFICATIONS, REFRESH, PRIVACY, ABOUT, ACCOUNT_MENU, REMOVE, SUCCESS, IMPORT }
data class MonitorPanel(val kind: PanelKind, val platform: Platform? = null, val accountId: String? = null)

data class MonitoredAccount(val account: Account, val usage: UsageInfo?, val visible: Boolean, val pinned: Boolean) {
    val platform get() = account.platform
    val key get() = "${platform.key}/${account.id}"
    val attention get() = usage?.refreshFailure != null
    val needsLogin get() = usage?.refreshFailure?.requiresLogin == true
    val paused get() = !account.monitoringEnabled
    val status get() = when { needsLogin -> if (platform.requiresApiKey) "需更新密钥" else "需重新登录"; attention -> "待更新"; paused -> "已暂停"; else -> "已连接" }
}

data class AccountDraft(
    val platform: Platform, val accountId: String?, val name: String,
    val visible: Boolean = true, val monitoring: Boolean = true, val alerts: Boolean = true,
    val pinned: Boolean = false, val apiKey: String = "",
) {
    override fun toString() = "AccountDraft(${platform.key}, [REDACTED])"
}

data class MonitorState(
    val page: MonitorPage = MonitorPage.OVERVIEW,
    val accounts: List<MonitoredAccount> = emptyList(),
    val homeFilter: Int = 0, val accountTab: Int = 0, val search: String = "",
    val providerSearch: String = "", val providerFilter: Int = 0,
    val editor: AccountDraft? = null, val panel: MonitorPanel? = null,
    val refreshing: Boolean = false, val saving: Boolean = false, val formError: String? = null,
    val lastUpdated: Long = 0, val now: Long = System.currentTimeMillis(),
    val colorMode: ColorMode = ColorMode.SYSTEM, val notifications: Boolean = true,
    val resetAlerts: Boolean = true, val sessionAlerts: Boolean = true,
    val refreshMinutes: Long = 15, val exportJson: String? = null,
) {
    override fun toString() = "MonitorState(page=$page, accounts=${accounts.size}, editor=${editor != null})"
}

sealed interface MonitorEvent {
    data class Message(val text: String, val undo: Boolean = false) : MonitorEvent
    data class Authorize(val platform: Platform, val accountId: String?) : MonitorEvent
}

@HiltViewModel
class MonitorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedState: SavedStateHandle,
    private val accountStore: AccountStore, private val tokenStore: SecureTokenStore,
    private val homePreferences: HomePlatformPreferences,
    private val notifications: NotificationPreferences, private val balanceNotifier: BalanceNotificationManager,
    private val refreshPreferences: RefreshPreferences, private val backup: BackupManager,
    private val repository: UsageRepository, private val cache: UsageCache,
    private val api: UsageApiService,
    sessionEvents: SessionEventBus,
) : ViewModel() {
    private val themePreferences = context.getSharedPreferences(THEME_PREFS_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(MonitorState(page = savedState.get<String>("monitor_page")
        ?.let { runCatching { MonitorPage.valueOf(it) }.getOrNull() } ?: MonitorPage.OVERVIEW))
    val state = _state.asStateFlow()
    private val eventChannel = Channel<MonitorEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var editorJob: Job? = null
    private var removed: RemovedAccount? = null
    private data class RemovedAccount(val account: Account, val tokens: TokenSnapshot, val usage: UsageInfo?) {
        override fun toString() = "RemovedAccount([REDACTED])"
    }

    init {
        reload()
        viewModelScope.launch {
            sessionEvents.events.filterIsInstance<SessionEvent.RefreshCompleted>().collect { reload() }
        }
        viewModelScope.launch { homePreferences.visiblePlatforms.collect { reload() } }
        viewModelScope.launch { while (isActive) { delay(30_000); reload() } }
        refresh()
    }

    fun reload() {
        val now = System.currentTimeMillis()
        val cached = repository.getCachedUsage().associateBy { it.platform to it.accountId }
        val visible = homePreferences.visiblePlatforms.value
        val pinned = notifications.pinnedPlatforms()
        val rows = accountStore.getAllAccounts().map { account ->
            MonitoredAccount(account, cached[account.platform to account.id]?.atTime(now),
                account.showOnOverview ?: (account.platform in visible), account.pinned ?: (account.platform in pinned))
        }
        _state.update { it.copy(accounts = rows, now = now,
            lastUpdated = rows.mapNotNull { row -> row.usage?.takeIf { data -> data.items.isNotEmpty() }?.updatedAt }.maxOrNull() ?: 0,
            colorMode = runCatching { ColorMode.valueOf(themePreferences.getString(KEY_COLOR_MODE, "SYSTEM")!!) }.getOrDefault(ColorMode.SYSTEM),
            notifications = notifications.notificationsEnabled, resetAlerts = notifications.resetReminderEnabled,
            sessionAlerts = notifications.sessionExpiredAlertEnabled, refreshMinutes = refreshPreferences.intervalMinutes) }
    }

    fun selectPage(page: MonitorPage) {
        savedState["monitor_page"] = page.name
        _state.update { it.copy(page = page, panel = null) }
        reload()
    }
    fun setHomeFilter(index: Int) = _state.update { it.copy(homeFilter = index) }
    fun setAccountTab(index: Int) = _state.update { it.copy(accountTab = index) }
    fun setSearch(value: String) = _state.update { it.copy(search = value) }
    fun setProviderSearch(value: String) = _state.update { it.copy(providerSearch = value) }
    fun setProviderFilter(index: Int) = _state.update { it.copy(providerFilter = index) }
    fun showProviders() = _state.update { it.copy(panel = MonitorPanel(PanelKind.PROVIDERS), providerSearch = "", providerFilter = 0) }
    fun showPanel(kind: PanelKind, platform: Platform? = null, accountId: String? = null) =
        _state.update { it.copy(panel = MonitorPanel(kind, platform, accountId)) }
    fun dismissPanel() {
        if (_state.value.saving) editorJob?.cancel()
        _state.update { it.copy(panel = null, saving = false) }
    }
    fun configure(platform: Platform, accountId: String? = null) {
        editorJob?.cancel()
        val row = _state.value.accounts.firstOrNull { it.platform == platform && (accountId == null || it.account.id == accountId) }
        _state.update { it.copy(panel = null, saving = false, formError = null, editor = AccountDraft(platform,
            row?.account?.id, row?.account?.displayName ?: "${platform.displayName} ${if (platform.requiresApiKey) "API" else "账户"}",
            row?.visible ?: true, row?.account?.monitoringEnabled ?: true, row?.account?.usageAlertEnabled ?: true, row?.pinned ?: false)) }
    }
    fun closeEditor() { editorJob?.cancel(); _state.update { it.copy(editor = null, panel = null, saving = false, formError = null) } }
    fun updateDraft(transform: (AccountDraft) -> AccountDraft) = _state.update { state ->
        state.editor?.let { state.copy(editor = transform(it), formError = null) } ?: state
    }

    fun refresh() {
        if (_state.value.refreshing) return
        _state.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.getAllUsage()
                }
                reload()
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { message("暂时无法同步，已保留上次数据") }
            finally { _state.update { it.copy(refreshing = false) } }
        }
    }

    fun saveEditor() {
        val draft = _state.value.editor ?: return
        if (draft.name.isBlank()) { _state.update { it.copy(formError = "请填写账户名称。") }; return }
        if (draft.accountId == null && !draft.platform.requiresApiKey) { authorize(); return }
        if (draft.platform.requiresApiKey && draft.accountId == null && draft.apiKey.isBlank()) {
            _state.update { it.copy(formError = "请填写 API Key。") }; return
        }
        editorJob?.cancel()
        editorJob = viewModelScope.launch {
            _state.update { it.copy(saving = true, formError = null) }
            try {
                val adding = draft.accountId == null
                val id = draft.accountId ?: UUID.randomUUID().toString()
                withContext(Dispatchers.IO) {
                    val before = accountStore.getAccounts(draft.platform).firstOrNull { it.id == id }
                    val validated = if (draft.apiKey.isNotBlank()) {
                        validateApi(draft.platform, draft.apiKey.trim())
                    } else null
                    currentCoroutineContext().ensureActive()
                    val account = makeAccount(draft, id, before, validated)
                    if (validated != null) repository.updateAccount(draft.platform, id, validated) {
                        commitCredentials(account) { tokenStore.save(draft.platform, id, draft.apiKey.trim(), durable = true) }
                    } else accountStore.saveAccount(account, durable = true)
                    if (draft.pinned) notifications.persistentEnabled = true
                }
                reload(); balanceNotifier.refresh()
                _state.update { it.copy(editor = null, saving = false, page = MonitorPage.ACCOUNTS,
                    panel = if (adding) MonitorPanel(PanelKind.SUCCESS, draft.platform, id) else null) }
                if (!adding) message("账户设置已保存")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                _state.update { it.copy(saving = false, formError = error.toUsageFailure().messageFor(draft.platform)) }
            }
        }
    }

    private suspend fun validateApi(platform: Platform, key: String): UsageInfo = when (platform) {
        Platform.MINIMAX -> api.getMiniMaxUsage(key)
        Platform.AIHUBMIX -> api.getAiHubMixUsage(key)
        Platform.DEEPSEEK -> api.getDeepSeekUsage(key)
        else -> error("该平台需要网页登录")
    }
    private fun makeAccount(draft: AccountDraft, id: String, before: Account?, usage: UsageInfo?) = Account(
        id, draft.platform, draft.name.trim(), usage?.planLabel ?: before?.planType ?: if (draft.platform.requiresApiKey) "API" else "",
        usage?.providerAccountId ?: before?.providerAccountId, draft.monitoring, draft.visible, draft.alerts, draft.pinned,
    )
    /** Caller holds the repository account gate. Roll back both encrypted stores together. */
    private fun commitCredentials(next: Account, saveToken: () -> Unit) {
        val tokens = tokenStore.snapshot(next.platform, next.id)
        val before = accountStore.getAccounts(next.platform).firstOrNull { it.id == next.id }
        try { saveToken(); accountStore.saveAccount(next, durable = true) }
        catch (failure: Exception) {
            val restoredToken = runCatching { tokenStore.restore(next.platform, next.id, tokens) }.isSuccess
            val restoredAccount = runCatching {
                if (before != null) accountStore.saveAccount(before, durable = true) else accountStore.removeAccount(next.platform, next.id, durable = true)
            }.isSuccess
            if (!restoredToken || !restoredAccount) runCatching { tokenStore.clear(next.platform, next.id, durable = true) }
            throw failure
        }
    }

    fun authorize() {
        val draft = _state.value.editor ?: return
        if (draft.name.isBlank()) { _state.update { it.copy(formError = "请填写账户名称。") }; return }
        viewModelScope.launch { eventChannel.send(MonitorEvent.Authorize(draft.platform, draft.accountId)) }
    }
    fun onAuthorizationSuccess() {
        val draft = _state.value.editor ?: return
        _state.update { it.copy(saving = true) }
        editorJob = viewModelScope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    val account = accountStore.getAccounts(draft.platform).firstOrNull { draft.accountId == null || it.id == draft.accountId }
                        ?: error("未找到已连接账户")
                    account.copy(displayName = draft.name.trim(), monitoringEnabled = draft.monitoring,
                        showOnOverview = draft.visible, usageAlertEnabled = draft.alerts, pinned = draft.pinned)
                        .also { accountStore.saveAccount(it, durable = true) }
                }
                if (draft.pinned) notifications.persistentEnabled = true
                reload(); balanceNotifier.refresh(); _state.update { it.copy(editor = null, saving = false, page = MonitorPage.ACCOUNTS,
                    panel = MonitorPanel(PanelKind.SUCCESS, account.platform, account.id)) }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { _state.update { it.copy(saving = false) }; reload(); message("已连接账户，部分显示设置未保存，请重试") }
        }
    }

    fun removeAccount() {
        val draft = _state.value.editor ?: return
        val id = draft.accountId ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateAccount(draft.platform, id) {
                        val account = accountStore.getAccounts(draft.platform).first { it.id == id }
                        val snapshot = RemovedAccount(account, tokenStore.snapshot(draft.platform, id), cache.get(draft.platform, id))
                        try {
                            tokenStore.clear(draft.platform, id, durable = true)
                            accountStore.removeAccount(draft.platform, id, durable = true)
                            removed = snapshot
                        } catch (error: Exception) {
                            tokenStore.restore(draft.platform, id, snapshot.tokens)
                            accountStore.saveAccount(account, durable = true)
                            throw error
                        }
                    }
                }
                reload(); balanceNotifier.refresh(); _state.update { it.copy(editor = null, panel = null, page = MonitorPage.ACCOUNTS) }
                message("已移除账户监控", undo = true)
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reload(); message("账户移除失败，请重试") }
        }
    }
    fun undoRemove() {
        val snapshot = removed ?: return
        removed = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    repository.updateAccount(snapshot.account.platform, snapshot.account.id) {
                        check(accountStore.getAccounts(snapshot.account.platform).none { it.id == snapshot.account.id })
                        commitCredentials(snapshot.account) { tokenStore.restore(snapshot.account.platform, snapshot.account.id, snapshot.tokens) }
                    }
                    snapshot.usage?.let { usage ->
                        cache.save(usage.platform, snapshot.account.id, usage)
                        usage.refreshFailure?.let { cache.saveFailure(usage.platform, snapshot.account.id, it) }
                    }
                }
                reload(); balanceNotifier.refresh(); message("账户监控已恢复")
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { reload(); message("恢复失败，请重新添加账户") }
        }
    }

    fun setColorMode(mode: ColorMode) { themePreferences.edit().putString(KEY_COLOR_MODE, mode.name).apply(); reload(); dismissPanel() }
    fun setNotifications(enabled: Boolean) {
        notifications.notificationsEnabled = enabled
        if (!enabled) NotificationManagerCompat.from(context).cancelAll() else balanceNotifier.refresh()
        reload()
    }
    fun setResetAlerts(enabled: Boolean) { notifications.resetReminderEnabled = enabled; reload() }
    fun setSessionAlerts(enabled: Boolean) { notifications.sessionExpiredAlertEnabled = enabled; reload() }
    fun setRefreshMinutes(minutes: Long) { refreshPreferences.intervalMinutes = minutes; RefreshWorker.schedule(context); reload(); dismissPanel() }
    fun prepareExport() = viewModelScope.launch {
        try { val json = withContext(Dispatchers.IO) { backup.export() }; _state.update { it.copy(exportJson = json) } }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { message("导出失败，请重试") }
    }
    fun clearExport() = _state.update { it.copy(exportJson = null) }
    fun importBackup(json: String) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) { val prepared = backup.prepareImport(json); repository.updateAccounts(prepared.affectedAccounts) { backup.import(prepared) } }
            reload(); selectPage(MonitorPage.ACCOUNTS); balanceNotifier.refresh(); refresh(); message("备份已成功导入")
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { reload(); message("导入失败，请检查备份文件后重试") }
    }
    fun message(text: String, undo: Boolean = false) { eventChannel.trySend(MonitorEvent.Message(text, undo)) }
}
