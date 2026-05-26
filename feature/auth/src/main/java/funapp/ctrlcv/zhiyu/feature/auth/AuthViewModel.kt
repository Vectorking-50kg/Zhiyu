package funapp.ctrlcv.zhiyu.feature.auth

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class AuthUiState(
    val platform: Platform = Platform.CLAUDE,
    val isLoading: Boolean = true,
    val isSuccess: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tokenStore: SecureTokenStore,
    private val accountStore: AccountStore
) : ViewModel() {

    private val platformKey: String = savedStateHandle["platform"] ?: Platform.CLAUDE.key

    private val _uiState = MutableStateFlow(
        AuthUiState(platform = Platform.entries.first { it.key == platformKey })
    )
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private var loginHandled = false

    fun onLoginSuccess(cookie: String) {
        if (loginHandled) return
        viewModelScope.launch {
            val platform = _uiState.value.platform
            val cookieValue = extractCookieValue(platform, cookie)
            if (cookieValue != null) {
                loginHandled = true
                val existingAccounts = accountStore.getAccounts(platform)
                val accountId = existingAccounts.firstOrNull()?.id
                    ?: UUID.randomUUID().toString().take(8)
                tokenStore.save(platform, accountId, cookieValue)
                if (existingAccounts.isEmpty()) {
                    accountStore.saveAccount(
                        Account(
                            id = accountId,
                            platform = platform,
                            displayName = platform.displayName,
                            planType = "Pro"
                        )
                    )
                }
                _uiState.update { it.copy(isSuccess = true, isLoading = false) }
            } else {
                _uiState.update { it.copy(error = "未找到 ${platform.getCookieName()} Cookie，请确认已完成登录", isLoading = false) }
            }
        }
    }

    fun onLoginError(message: String) {
        _uiState.update { it.copy(error = message, isLoading = false) }
    }

    private fun extractCookieValue(platform: Platform, rawCookie: String): String? {
        val cookieName = platform.getCookieName()
        val cookies = rawCookie.split(";").map { it.trim() }

        val exact = cookies.firstOrNull { it.startsWith("$cookieName=") }
            ?.substringAfter("$cookieName=")
        if (exact != null) return exact

        val chunked = cookies
            .filter { it.startsWith("$cookieName.") && it.contains("=") }
            .sortedBy { it.substringBefore("=").substringAfterLast(".").toIntOrNull() ?: 0 }
            .joinToString("") { it.substringAfter("=") }
        if (chunked.isNotBlank()) return chunked

        return null
    }
}
