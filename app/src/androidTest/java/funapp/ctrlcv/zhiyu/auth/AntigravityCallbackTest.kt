package funapp.ctrlcv.zhiyu.auth

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.network.api.AntigravityApiClient
import funapp.ctrlcv.zhiyu.core.network.api.UsageApiService
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthSessionManager
import funapp.ctrlcv.zhiyu.core.network.oauth.OAuthTokenClient
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore
import funapp.ctrlcv.zhiyu.feature.auth.AuthViewModel
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Drives the production callback entry point without a real Google login or token request. */
@RunWith(AndroidJUnit4::class)
class AntigravityCallbackTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val models = ViewModelStore()
    private lateinit var viewModel: AuthViewModel
    private lateinit var accountStore: AccountStore
    private lateinit var originalAccounts: Set<String>

    @Before fun setUp() {
        val client = OkHttpClient()
        val tokens = SecureTokenStore(context)
        accountStore = AccountStore(context)
        originalAccounts = accountStore.getAccounts(Platform.ANTIGRAVITY).map { it.id }.toSet()
        val api = UsageApiService(client, Gson())
        val oauth = OAuthTokenClient(client)
        val sessions = OAuthSessionManager(oauth, tokens, api, AntigravityApiClient(client))
        instrumentation.runOnMainSync {
            viewModel = AuthViewModel(SavedStateHandle(mapOf("platform" to "antigravity")),
                tokens, accountStore, api, oauth, sessions, NoUsageLookup)
            models.put("auth", viewModel)
        }
        val deadline = System.nanoTime() + 3_000_000_000
        while (viewModel.uiState.value.authorizationUrl == null && System.nanoTime() < deadline) Thread.sleep(10)
        assertNotNull("The authorization attempt must be ready", viewModel.uiState.value.authorizationUrl)
    }

    @After fun tearDown() {
        instrumentation.runOnMainSync { models.clear() }
        assertEquals("Failed authorization must not change accounts", originalAccounts,
            accountStore.getAccounts(Platform.ANTIGRAVITY).map { it.id }.toSet())
    }

    private fun callback(state: String? = null): String {
        val auth = Uri.parse(viewModel.uiState.value.authorizationUrl)
        return Uri.parse(auth.getQueryParameter("redirect_uri")).buildUpon()
            .appendQueryParameter("state", state ?: auth.getQueryParameter("state"))
            .appendQueryParameter("error", "access_denied").build().toString()
    }

    @Test fun embeddedCallbackIsConsumedAndShowsDenialInsteadOfSilentlyWaiting() {
        val url = callback()
        instrumentation.runOnMainSync {
            assertTrue("Antigravity callback navigation must be consumed inside Zhiyu", viewModel.onOAuthNavigation(url))
            assertFalse(viewModel.uiState.value.isLoading)
            assertNotNull("A completed denial must leave waiting state with a recoverable error", viewModel.uiState.value.error)
        }
    }

    @Test fun wrongStateIsConsumedWithAnExplicitErrorWithoutAQuotaRequest() {
        val url = callback("wrong-state")
        instrumentation.runOnMainSync {
            assertTrue(viewModel.onOAuthNavigation(url))
            assertFalse(viewModel.uiState.value.isLoading)
            assertTrue(viewModel.uiState.value.error.orEmpty().contains("校验失败"))
        }
    }

    @Test fun duplicateCallbacksDoNotReopenValidation() {
        val url = callback()
        instrumentation.runOnMainSync {
            assertTrue(viewModel.onOAuthNavigation(url))
            val error = viewModel.uiState.value.error
            assertNotNull(error)
            assertTrue(viewModel.onOAuthNavigation(url))
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(error, viewModel.uiState.value.error)
        }
    }

    @Test fun retryIgnoresThePreviousPagesLateCallback() {
        val oldUrl = callback()
        val generation = viewModel.uiState.value.pageGeneration
        instrumentation.runOnMainSync {
            viewModel.startOAuth()
            assertTrue(viewModel.onOAuthNavigation(oldUrl, generation))
            assertNull(viewModel.uiState.value.error)
            assertNotNull(viewModel.uiState.value.authorizationUrl)
            assertFalse(viewModel.uiState.value.isLoading)
        }
        val current = callback()
        instrumentation.runOnMainSync {
            assertTrue(viewModel.onOAuthNavigation(current))
            assertNotNull(viewModel.uiState.value.error)
        }
    }

    @Test fun cancelledAttemptCannotBeReactivatedByTheWebView() {
        val url = callback()
        instrumentation.runOnMainSync {
            viewModel.cancel()
            assertTrue(viewModel.onOAuthNavigation(url))
            assertNull(viewModel.uiState.value.error)
            assertNull(viewModel.uiState.value.connectedAccountId)
            assertFalse(viewModel.uiState.value.isLoading)
        }
    }

    private object NoUsageLookup : UsageRepository {
        override suspend fun getClaudeUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getChatGptUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getCursorUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getZenUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getOpenCodeGoUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getMiniMaxUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getAiHubMixUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getDeepSeekUsage(accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getUsage(platform: Platform, accountId: String): Result<UsageInfo> = error("Unexpected quota request")
        override suspend fun getAllUsage(): List<UsageInfo> = error("Unexpected quota request")
        override fun getCachedUsage(): List<UsageInfo> = emptyList()
        override suspend fun invalidateCache(platform: Platform, accountId: String) = error("Unexpected cache change")
        override suspend fun updateAccount(platform: Platform, accountId: String, validatedUsage: UsageInfo?, commit: () -> Unit) = error("Unexpected account change")
        override suspend fun updateAccounts(accounts: Collection<Pair<Platform, String>>, commit: () -> Unit) = error("Unexpected account change")
    }
}
