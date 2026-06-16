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

    fun onLoginSuccess(cookie: String, url: String? = null) {
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
                // Zen：登录成功瞬间 WebView 正停在 /workspace/{id}，把 id 存下来，
                // 取余额时直接精确抓取该仪表盘页，避免靠营销/文档根域名盲猜入口。
                if (platform == Platform.ZEN && url != null) {
                    ZEN_WORKSPACE_ID_REGEX.find(url)?.groupValues?.getOrNull(1)?.let { wid ->
                        tokenStore.saveExtra(platform, accountId, SecureTokenStore.EXTRA_ZEN_WORKSPACE_ID, wid)
                    }
                }
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
        // OpenCode Zen 的会话 Cookie 是 Hapi/Iron 令牌，服务端按原名读取（auth 或 __Host-auth，
        // https 下还可能两者并存）。仅保留名字精确为 auth / __Host-auth 的会话段，原样作为完整
        // Cookie 头存储 / 回传：既避免 __Host- 前缀被裁掉导致鉴权失败，也避免把登录页的
        // csrf/state（如 auth_state）误当成会话，从而在登录页就「自动确认」并退出。
        if (platform == Platform.ZEN) {
            return rawCookie.split(";")
                .map { it.trim() }
                .filter {
                    val name = it.substringBefore("=").trim()
                    name == "auth" || name == "__Host-auth"
                }
                .joinToString("; ")
                .ifBlank { null }
        }

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

    companion object {
        // 从 opencode.ai/workspace/{id} 的登录后 URL 中提取 workspace id
        private val ZEN_WORKSPACE_ID_REGEX = Regex("/workspace/([A-Za-z0-9_-]{4,})")
    }
}
