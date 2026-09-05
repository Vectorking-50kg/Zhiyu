package funapp.ctrlcv.zhiyu.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.toUsageFailure
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.network.oauth.ClaudeOAuthChallenge
import funapp.ctrlcv.zhiyu.core.network.oauth.DeviceCodeChallenge
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthLoginExpiredException
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthSessionManager
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthTokenClient
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

enum class AuthMode { COOKIE, OAUTH }

data class AuthUiState(
    val platform: Platform = Platform.CLAUDE,
    val mode: AuthMode = AuthMode.COOKIE,
    val isLoading: Boolean = false,
    val isSuccess: Boolean = false,
    val error: String? = null,
    val authorizationUrl: String? = null,
    val deviceChallenge: DeviceCodeChallenge? = null,
    val switchAccountName: String? = null,
    val switchNeedsConfirmation: Boolean = false,
    val pageGeneration: Long = 0
) {
    override fun toString(): String = "AuthUiState(platform=${platform.key}, mode=$mode, loading=$isLoading, success=$isSuccess)"
}

@HiltViewModel
class AuthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tokenStore: SecureTokenStore,
    private val accountStore: AccountStore,
    private val api: UsageApiService,
    private val oauthTokens: OAuthTokenClient,
    private val oauthSessions: OAuthSessionManager,
    private val repository: UsageRepository
) : ViewModel() {
    private val platform = Platform.entries.firstOrNull { it.key == savedStateHandle.get<String>("platform") }
        ?: Platform.CLAUDE
    private val _uiState = MutableStateFlow(AuthUiState(platform = platform))
    val uiState = _uiState.asStateFlow()
    private val attempts = AuthAttemptGate()
    private var loginJob: Job? = null
    private var claudeChallenge: ClaudeOAuthChallenge? = null
    private var pending: LoginCandidate? = null
    private var lastAutomaticCookie: String? = null

    fun onLoginSuccess(cookie: String, url: String? = null, manual: Boolean = false) {
        if (_uiState.value.mode != AuthMode.COOKIE || _uiState.value.isSuccess) return
        val value = extractCookieValue(platform, cookie) ?: run {
            if (manual) onLoginError("未找到登录 Cookie，请确认已完成登录")
            return
        }
        if (!manual && value == lastAutomaticCookie) return
        val attempt = attempts.begin() ?: return
        lastAutomaticCookie = value
        _uiState.update { it.copy(isLoading = true, error = null) }
        loginJob = viewModelScope.launch {
            try {
                val workspace = if (platform == Platform.ZEN) url?.let {
                    ZEN_WORKSPACE_ID_REGEX.find(it)?.groupValues?.getOrNull(1)
                } else null
                val validated = api.validateCookie(platform, value, workspace)
                offerCandidate(LoginCandidate(
                    attempt = attempt,
                    cookie = value,
                    workspaceId = workspace,
                    providerAccountId = validated.providerAccountId,
                    displayName = validated.displayName,
                    usage = validated.usage
                ))
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(attempt, e) }
        }
    }

    fun startOAuth() {
        if (platform !in setOf(Platform.CLAUDE, Platform.CHATGPT) || _uiState.value.isSuccess) return
        cancelCurrentAttempt()
        _uiState.update { AuthUiState(platform = platform, mode = AuthMode.OAUTH, pageGeneration = it.pageGeneration + 1) }
        if (platform == Platform.CLAUDE) {
            claudeChallenge = oauthTokens.newClaudeChallenge()
            _uiState.update { it.copy(authorizationUrl = claudeChallenge?.authorizationUrl) }
        } else {
            val attempt = attempts.begin() ?: return
            _uiState.update { it.copy(isLoading = true) }
            loginJob = viewModelScope.launch {
                try {
                    val challenge = oauthTokens.requestCodexDeviceCode()
                    if (!attempts.isCurrent(attempt)) return@launch
                    _uiState.update { it.copy(deviceChallenge = challenge, isLoading = false) }
                    val credential = oauthTokens.awaitCodexAuthorization(challenge)
                    if (!attempts.isCurrent(attempt)) return@launch
                    _uiState.update { it.copy(isLoading = true, deviceChallenge = null) }
                    validateOAuth(attempt, credential)
                } catch (e: CancellationException) { throw e }
                catch (e: Exception) { fail(attempt, e) }
            }
        }
    }

    /** A matching callback is consumed before the WebView can send the code anywhere else. */
    fun onOAuthNavigation(url: String): Boolean {
        if (_uiState.value.mode != AuthMode.OAUTH || !oauthTokens.isClaudeCallback(url)) return false
        val challenge = claudeChallenge ?: return true
        val attempt = attempts.begin() ?: return true
        claudeChallenge = null
        _uiState.update { it.copy(isLoading = true, error = null) }
        loginJob = viewModelScope.launch {
            try {
                val credential = oauthTokens.exchangeClaude(challenge, url)
                validateOAuth(attempt, credential)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(attempt, e) }
        }
        return true
    }

    private suspend fun validateOAuth(attempt: Long, credential: OAuthCredential) {
        val usage = oauthSessions.validate(platform, credential)
        offerCandidate(LoginCandidate(
            attempt = attempt, oauth = credential,
            providerAccountId = credential.providerAccountId,
            displayName = credential.displayName,
            usage = usage
        ))
    }

    private suspend fun offerCandidate(candidate: LoginCandidate) {
        currentCoroutineContext().ensureActive()
        if (!attempts.isCurrent(candidate.attempt)) return
        val existing = accountStore.getAccounts(platform).firstOrNull()
        if (requiresAccountSwitchConfirmation(existing != null, existing?.providerAccountId, candidate.providerAccountId)) {
            pending = candidate
            _uiState.update { it.copy(isLoading = false, switchNeedsConfirmation = true, switchAccountName = candidate.displayName) }
        } else {
            persist(candidate)
        }
    }

    fun confirmAccountSwitch() {
        val candidate = pending ?: return
        pending = null
        _uiState.update { it.copy(switchNeedsConfirmation = false, isLoading = true) }
        loginJob = viewModelScope.launch {
            try { persist(candidate) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { fail(candidate.attempt, e) }
        }
    }

    private suspend fun persist(candidate: LoginCandidate) {
        if (!attempts.isCurrent(candidate.attempt)) return
        val existing = accountStore.getAccounts(platform).firstOrNull()
        val accountId = existing?.id ?: UUID.randomUUID().toString()
        // Cancel previous reads and commit credentials under the same repository gate.
        // Rotation is durable, so encrypted disk writes stay off the UI thread.
        withContext(Dispatchers.IO) {
            val context = currentCoroutineContext()
            repository.updateAccount(platform, accountId, candidate.usage.copy(providerAccountId = candidate.providerAccountId)) {
                context.ensureActive()
                if (!attempts.isCurrent(candidate.attempt)) throw CancellationException("Login canceled")
                val credentialsBefore = tokenStore.snapshot(platform, accountId)
                val accountBefore = accountStore.getAccounts(platform).firstOrNull { it.id == accountId }
                commitLogin(commit = {
                    if (candidate.oauth != null) {
                        tokenStore.saveOAuth(platform, accountId, candidate.oauth)
                    } else {
                        tokenStore.save(platform, accountId, checkNotNull(candidate.cookie), durable = true)
                    }
                    if (platform == Platform.ZEN) {
                        tokenStore.clearExtra(platform, accountId, SecureTokenStore.EXTRA_ZEN_WORKSPACE_ID, durable = true)
                        candidate.workspaceId?.let {
                            tokenStore.saveExtra(platform, accountId, SecureTokenStore.EXTRA_ZEN_WORKSPACE_ID, it, durable = true)
                        }
                    }
                    accountStore.saveAccount(Account(
                        id = accountId, platform = platform,
                        displayName = candidate.displayName ?: platform.displayName,
                        planType = candidate.usage.planLabel ?: "",
                        providerAccountId = candidate.providerAccountId
                    ), durable = true)
                }, restore = {
                    try {
                        tokenStore.restore(platform, accountId, credentialsBefore)
                    } finally {
                        if (accountBefore != null) accountStore.saveAccount(accountBefore, durable = true)
                        else accountStore.removeAccount(platform, accountId, durable = true)
                    }
                }, quarantine = {
                    tokenStore.clear(platform, accountId, durable = true)
                })
            }
        }
        attempts.finish(candidate.attempt)
        _uiState.update { it.copy(isSuccess = true, isLoading = false, error = null, deviceChallenge = null) }
    }

    fun useWebLogin() {
        cancelCurrentAttempt()
        _uiState.update { AuthUiState(platform = platform, pageGeneration = it.pageGeneration + 1) }
    }

    fun cancel() { cancelCurrentAttempt() }

    private fun cancelCurrentAttempt() {
        attempts.cancel()
        loginJob?.cancel()
        loginJob = null
        pending = null
        claudeChallenge = null
        lastAutomaticCookie = null
    }

    fun onLoginError(message: String) {
        if (!attempts.busy) _uiState.update { it.copy(error = message, isLoading = false) }
    }

    private fun fail(attempt: Long, e: Exception) {
        if (!attempts.isCurrent(attempt)) return
        attempts.finish(attempt)
        val message = when (e) {
            is OAuthLoginExpiredException -> "授权码已过期，请重新获取"
            is AuthSaveException -> e.message ?: "登录状态保存失败，请重试"
            else -> e.toUsageFailure().message
        }
        _uiState.update { it.copy(error = message, isLoading = false, deviceChallenge = null) }
    }

    override fun onCleared() {
        cancelCurrentAttempt()
        super.onCleared()
    }

    private class LoginCandidate(
        val attempt: Long,
        val cookie: String? = null,
        val oauth: OAuthCredential? = null,
        val workspaceId: String? = null,
        val providerAccountId: String?,
        val displayName: String?,
        val usage: UsageInfo
    )

    companion object {
        private val ZEN_WORKSPACE_ID_REGEX = Regex("/workspace/([A-Za-z0-9_-]{4,})")

        internal fun extractCookieValue(platform: Platform, rawCookie: String): String? {
            val cookies = rawCookie.split(";").map { it.trim() }
            if (platform == Platform.ZEN) return cookies.filter {
                val name = it.substringBefore("=").trim()
                name == "auth" || name == "__Host-auth"
            }.joinToString("; ").ifBlank { null }
            val name = platform.getCookieName()
            cookies.firstOrNull { it.startsWith("$name=") }?.substringAfter("=")
                ?.takeIf { it.isNotBlank() }?.let { return it }
            val chunks = cookies.mapNotNull {
                val key = it.substringBefore("=")
                val index = key.removePrefix("$name.").toIntOrNull()
                if (key.startsWith("$name.") && index != null && it.contains("=")) index to it.substringAfter("=") else null
            }.sortedBy { it.first }
            if (chunks.isEmpty() || chunks.map { it.first } != chunks.indices.toList()) return null
            return chunks.joinToString("") { it.second }.ifBlank { null }
        }
    }
}
