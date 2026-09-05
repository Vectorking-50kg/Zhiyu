package funapp.ctrlcv.zhiyu.core.network.oauth

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.storage.OAuthCredential
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OAuthSessionManager internal constructor(
    private val tokens: OAuthTokenClient,
    private val load: (Platform, String) -> OAuthCredential?,
    private val replace: (Platform, String, OAuthCredential, OAuthCredential) -> Boolean,
    private val fetch: suspend (Platform, OAuthCredential) -> UsageInfo,
    private val now: () -> Long = System::currentTimeMillis
) {
    @Inject constructor(tokens: OAuthTokenClient, store: SecureTokenStore, api: UsageApiService) : this(
        tokens = tokens,
        load = store::getOAuth,
        replace = store::replaceOAuthIfCurrent,
        fetch = { platform, credential ->
            when (platform) {
                Platform.CLAUDE -> api.getClaudeOAuthUsage(credential.accessToken)
                Platform.CHATGPT -> api.getCodexOAuthUsage(credential.accessToken, credential.providerAccountId)
                else -> throw SessionExpiredException(platform)
            }
        }
    )

    private val locks = ConcurrentHashMap<String, Mutex>()

    fun hasSession(platform: Platform, accountId: String): Boolean = load(platform, accountId) != null

    /** Login validation never persists or replaces an existing credential. */
    suspend fun validate(platform: Platform, credential: OAuthCredential): UsageInfo {
        val usage = fetch(platform, credential)
        if (usage.providerAccountId != null && usage.providerAccountId != credential.providerAccountId) {
            throw SessionExpiredException(platform)
        }
        return usage.copy(providerAccountId = credential.providerAccountId)
    }

    suspend fun getUsage(platform: Platform, accountId: String): UsageInfo = withContext(Dispatchers.IO) {
        locks.getOrPut("${platform.key}:$accountId") { Mutex() }.withLock {
            var credential = load(platform, accountId) ?: throw SessionExpiredException(platform)
            var refreshed = false
            if (credential.needsRefresh(now())) {
                credential = refreshAndPersist(platform, accountId, credential)
                refreshed = true
            }
            val usage = try {
                validate(platform, credential)
            } catch (e: SessionExpiredException) {
                // At most one refresh in this operation; 403/429/network failures never rotate tokens.
                if (refreshed) throw e
                credential = refreshAndPersist(platform, accountId, credential)
                validate(platform, credential)
            }
            currentCoroutineContext().ensureActive()
            if (load(platform, accountId) != credential) throw CancellationException("Login changed")
            usage.copy(accountId = accountId, providerAccountId = credential.providerAccountId)
        }
    }

    private suspend fun refreshAndPersist(
        platform: Platform,
        accountId: String,
        previous: OAuthCredential
    ): OAuthCredential {
        // Once the server consumes a refresh token, cancellation must not discard its replacement.
        // The token client's total call timeout bounds this non-cancellable phase to 30 seconds.
        val refreshed = withContext(NonCancellable) {
            val replacement = tokens.refresh(platform, previous)
            if (!replace(platform, accountId, previous, replacement)) throw CancellationException("Login changed")
            replacement
        }
        currentCoroutineContext().ensureActive()
        return refreshed
    }
}
