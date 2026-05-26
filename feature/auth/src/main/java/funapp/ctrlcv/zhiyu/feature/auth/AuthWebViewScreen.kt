package funapp.ctrlcv.zhiyu.feature.auth

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel

private const val TAG = "ZhiyuAuth"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    onBack: () -> Unit = {},
    onSuccess: () -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var isPageLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // Google OAuth popup WebView — needed so window.opener points to the main claude.ai window
    var popupWebView by remember { mutableStateOf<WebView?>(null) }

    if (uiState.isSuccess) {
        onSuccess()
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("登录 ${uiState.platform.displayName}") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
            windowInsets = WindowInsets(0)
        )

        if (isPageLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        uiState.error?.let { error ->
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(16.dp)
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.javaScriptCanOpenWindowsAutomatically = true
                        // Must be true so window.open() triggers onCreateWindow instead of
                        // being redirected into the main frame — which breaks window.opener.
                        settings.setSupportMultipleWindows(true)
                        settings.userAgentString =
                            "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/124.0.0.0 Mobile Safari/537.36"

                        val cookieManager = CookieManager.getInstance()
                        cookieManager.setAcceptCookie(true)
                        cookieManager.setAcceptThirdPartyCookies(this, true)
                        cookieManager.removeAllCookies(null)

                        // Handle Google's window.open() for the OAuth popup.
                        // Without this, the popup URL is loaded inside the main frame and
                        // gsi/transform ends up with no valid window.opener to postMessage to.
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(
                                view: WebView?,
                                isDialog: Boolean,
                                isUserGesture: Boolean,
                                resultMsg: Message?
                            ): Boolean {
                                Log.d(TAG, "[${uiState.platform.key}] onCreateWindow: opening OAuth popup")
                                val popup = WebView(context).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.javaScriptCanOpenWindowsAutomatically = true
                                    settings.setSupportMultipleWindows(true)
                                    settings.userAgentString =
                                        "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                                        "Chrome/124.0.0.0 Mobile Safari/537.36"
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            v: WebView?,
                                            req: WebResourceRequest?
                                        ): Boolean {
                                            Log.d(TAG, "[${uiState.platform.key}] popup url: ${req?.url}")
                                            return false
                                        }

                                        override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                                            Log.d(TAG, "[${uiState.platform.key}] popup onPageStarted: $url")
                                        }

                                        override fun onPageFinished(v: WebView?, url: String?) {
                                            Log.d(TAG, "[${uiState.platform.key}] popup onPageFinished: $url")
                                            // gsi/transform postMessages the auth code to window.opener
                                            // (the main WebView = claude.ai) and then calls window.close().
                                            // If window.close() succeeds, onCloseWindow fires and we null out
                                            // the popup. As a safety net we also dismiss after a short delay.
                                            if (url?.contains("accounts.google.com/gsi/transform") == true) {
                                                Handler(Looper.getMainLooper()).postDelayed({
                                                    if (popupWebView != null) {
                                                        Log.d(TAG, "[${uiState.platform.key}] closing popup after gsi/transform")
                                                        popupWebView = null
                                                    }
                                                }, 2000)
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) {
                                            Log.d(TAG, "[${uiState.platform.key}] popup: window.close() called")
                                            popupWebView = null
                                        }
                                    }
                                }

                                val transport = resultMsg?.obj as? WebView.WebViewTransport
                                transport?.webView = popup
                                resultMsg?.sendToTarget()
                                popupWebView = popup
                                return true
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): Boolean {
                                val url = request?.url?.toString() ?: return false
                                Log.d(TAG, "[${uiState.platform.key}] shouldOverrideUrlLoading: $url")
                                return false
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isPageLoading = true
                                url?.let {
                                    currentUrl = it
                                    Log.d(TAG, "[${uiState.platform.key}] onPageStarted: $it")
                                }
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPageLoading = false
                                url ?: return
                                currentUrl = url
                                Log.d(TAG, "[${uiState.platform.key}] onPageFinished: $url")

                                // Fallback: if gsi/transform somehow loaded in the main WebView,
                                // navigate back to claude.ai so the Google session cookies can be used.
                                if (url.contains("accounts.google.com/gsi/transform")) {
                                    Log.w(TAG, "[${uiState.platform.key}] gsi/transform in main frame — navigating back to login page")
                                    Handler(Looper.getMainLooper()).postDelayed({
                                        view?.loadUrl(uiState.platform.loginUrl)
                                    }, 500)
                                    return
                                }

                                val cm = CookieManager.getInstance()
                                val allDomains = (uiState.platform.getCookieDomains() + url).distinct()
                                val cookieMap = allDomains.associateWith { cm.getCookie(it) }
                                val cookie = cookieMap.values
                                    .filterNotNull()
                                    .filter { it.contains(uiState.platform.getCookieName()) }
                                    .maxByOrNull { it.length }
                                    ?: cookieMap.values.filterNotNull().maxByOrNull { it.length }

                                cookieMap.forEach { (domain, c) ->
                                    Log.d(TAG, "[${uiState.platform.key}] cookie($domain): len=${c?.length ?: 0}")
                                }
                                Log.d(TAG, "[${uiState.platform.key}] best cookie keys: ${cookie?.split(";")?.map { it.trim().substringBefore("=") }}")

                                if (uiState.platform.isLoggedIn(url)) {
                                    Log.d(TAG, "[${uiState.platform.key}] Login detected! Extracting cookie...")
                                    if (cookie != null) {
                                        viewModel.onLoginSuccess(cookie)
                                    } else {
                                        Log.w(TAG, "[${uiState.platform.key}] Login detected but no cookie found")
                                    }
                                } else if (cookie != null && cookie.contains(uiState.platform.getCookieName())) {
                                    Log.d(TAG, "[${uiState.platform.key}] Session cookie found in cookies! Auto-confirming...")
                                    viewModel.onLoginSuccess(cookie)
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                if (request?.isForMainFrame == true) {
                                    Log.e(TAG, "[${uiState.platform.key}] Error: ${error?.description}")
                                    viewModel.onLoginError(
                                        error?.description?.toString() ?: "加载失败"
                                    )
                                }
                            }
                        }

                        loadUrl(uiState.platform.loginUrl)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // OAuth popup overlay — rendered on top of the main WebView so the user can
            // interact with Google's sign-in pages.  Destroyed when Google closes it.
            val popup = popupWebView
            if (popup != null) {
                DisposableEffect(popup) {
                    onDispose { popup.destroy() }
                }
                AndroidView(
                    factory = { popup },
                    modifier = Modifier.fillMaxSize()
                )
            }

            ExtendedFloatingActionButton(
                onClick = {
                    Log.d(TAG, "[${uiState.platform.key}] Manual confirm at URL: $currentUrl")
                    val cm = CookieManager.getInstance()
                    val allDomains = (uiState.platform.getCookieDomains() + currentUrl).distinct()
                    val cookieMap = allDomains.associateWith { cm.getCookie(it) }
                    val cookie = cookieMap.values
                        .filterNotNull()
                        .filter { it.contains(uiState.platform.getCookieName()) }
                        .maxByOrNull { it.length }
                        ?: cookieMap.values.filterNotNull().maxByOrNull { it.length }
                    cookieMap.forEach { (domain, c) ->
                        Log.d(TAG, "[${uiState.platform.key}] Manual cookie($domain): len=${c?.length ?: 0}")
                    }
                    if (cookie != null) {
                        viewModel.onLoginSuccess(cookie)
                    } else {
                        viewModel.onLoginError("未检测到登录 Cookie，请确认已完成登录")
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                text = { Text("已登录，确认") }
            )
        }
    }
}
