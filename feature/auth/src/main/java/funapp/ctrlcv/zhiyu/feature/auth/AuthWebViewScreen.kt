package funapp.ctrlcv.zhiyu.feature.auth

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import funapp.ctrlcv.zhiyu.core.ui.components.ScaledAlertDialog as AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.network.api.DevinWebSession
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthWebViewScreen(
    onBack: () -> Unit = {},
    onSuccess: (String) -> Unit = {},
    viewModel: AuthViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val leave = { viewModel.cancel(); onBack() }
    BackHandler(onBack = leave)
    DisposableEffect(viewModel) { onDispose { viewModel.cancel() } }
    LaunchedEffect(state.connectedAccountId) { state.connectedAccountId?.let(onSuccess) }
    if (state.isSuccess) return

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("登录 ${state.platform.displayName}") },
            navigationIcon = { IconButton(onClick = leave) {
                AppIcon(AppIcons.ArrowBack, contentDescription = "返回")
            } },
            windowInsets = WindowInsets(0)
        )
        if (state.platform in setOf(Platform.CLAUDE, Platform.CHATGPT)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = viewModel::useWebLogin, enabled = state.mode != AuthMode.COOKIE) { Text("网页登录") }
                TextButton(onClick = viewModel::startOAuth, enabled = state.mode == AuthMode.COOKIE || state.error != null) {
                    Text(if (state.platform == Platform.CHATGPT) "设备码登录（可选）" else "授权登录（可选）")
                }
            }
        }
        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            if (state.mode == AuthMode.COOKIE) TextButton(onClick = viewModel::useWebLogin) { Text("重新加载登录页") }
        }
        if (state.mode == AuthMode.OAUTH && state.platform in setOf(Platform.CHATGPT, Platform.GITHUB_COPILOT)) {
            DeviceLogin(state, viewModel)
        } else {
            key(state.pageGeneration) {
                LoginBrowser(state, viewModel, Modifier.weight(1f))
            }
        }
    }
    if (state.switchNeedsConfirmation) {
        val dismiss = if (state.platform == Platform.GITHUB_COPILOT) leave else viewModel::useWebLogin
        AlertDialog(
            onDismissRequest = dismiss,
            title = { Text("替换当前登录？") },
            text = { Text(buildString {
                append("刚刚验证的登录")
                state.switchAccountName?.let { append("（$it）") }
                append("与原账号不同，或原账号尚未记录身份信息。确认后将替换当前登录并清除旧额度缓存。")
            }) },
            confirmButton = { TextButton(onClick = viewModel::confirmAccountSwitch) { Text("确认替换") } },
            dismissButton = { TextButton(onClick = dismiss) { Text("取消") } }
        )
    }
}

@Composable
private fun DeviceLogin(state: AuthUiState, viewModel: AuthViewModel) {
    val context = LocalContext.current
    var browserError by remember { mutableStateOf<String?>(null) }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("在浏览器中授权", style = MaterialTheme.typography.titleLarge)
        Text("打开授权页面，输入下方设备码并完成登录，然后回到知余。请只输入你自己发起的设备码。")
        state.deviceChallenge?.let { challenge ->
            Text(challenge.userCode, style = MaterialTheme.typography.headlineLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = {
                    (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                        .setPrimaryClip(ClipData.newPlainText("${state.platform.displayName} 设备码", challenge.userCode))
                }) { Text("复制设备码") }
                Button(onClick = {
                    try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(challenge.verificationUrl))) }
                    catch (_: android.content.ActivityNotFoundException) { browserError = "未找到可用浏览器" }
                }) { Text("打开授权页面") }
            }
            Text("正在等待授权；设备码过期后可重新获取。", style = MaterialTheme.typography.bodySmall)
        }
        if (state.isLoading) {
            CircularProgressIndicator()
            Text("正在获取授权信息或验证额度…")
        }
        browserError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (state.error != null) Button(onClick = viewModel::startOAuth) { Text("重新获取设备码") }
        if (state.platform == Platform.CHATGPT) {
            TextButton(onClick = viewModel::useWebLogin) { Text("取消并返回网页登录") }
        }
    }
}

@Composable
private fun LoginBrowser(state: AuthUiState, viewModel: AuthViewModel, modifier: Modifier = Modifier) {
    var pageLoading by remember { mutableStateOf(true) }
    var currentUrl by remember { mutableStateOf("") }
    var mainWebView by remember { mutableStateOf<WebView?>(null) }
    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var devinReminderDismissed by rememberSaveable { mutableStateOf(false) }
    val isOAuth = state.mode == AuthMode.OAUTH
    val isDevin = state.platform == Platform.DEVIN
    val initialUrl = if (isOAuth) state.authorizationUrl else state.platform.loginUrl
    val scope = rememberCoroutineScope()
    val canReadDevinSession = isDevin && !pageLoading && popupWebView == null &&
        !state.isLoading && !state.switchNeedsConfirmation
    val canConfirmDevinSession = canReadDevinSession && DevinWebSession.isDevinPage(currentUrl) &&
        Uri.parse(currentUrl).pathSegments.firstOrNull() !in setOf("login", "signup", "auth")

    LaunchedEffect(mainWebView, canReadDevinSession, state.pageGeneration) {
        val browser = mainWebView ?: return@LaunchedEffect
        if (!canReadDevinSession) return@LaunchedEffect
        // Devin's single-page app may write the session and organization after page-finished fires.
        while (true) {
            browser.readDevinSession()?.let { viewModel.onDevinSession(it, state.pageGeneration) }
            delay(1000)
        }
    }

    DisposableEffect(Unit) { onDispose {
        mainWebView?.stopLoading()
        mainWebView?.destroy()
        mainWebView = null
    } }
    Column(modifier) {
        if (pageLoading || state.isLoading) LinearProgressIndicator(Modifier.fillMaxWidth())
        if (isOAuth) Text("授权后将验证账号和额度，验证成功才会保存登录。",
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(12.dp))
        Box(Modifier.fillMaxSize()) {
            if (initialUrl != null) AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        mainWebView = this
                        configureForLogin()
                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        webChromeClient = object : WebChromeClient() {
                            override fun onCreateWindow(view: WebView?, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message?): Boolean {
                                if (!isUserGesture) return false
                                val popup = WebView(context).apply {
                                    configureForLogin()
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val url = request?.url?.toString() ?: return false
                                            return (isOAuth && request.isForMainFrame && viewModel.onOAuthNavigation(url)) ||
                                                (request.url.scheme != "https" && url != "about:blank")
                                        }
                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            val uri = url?.let(Uri::parse)
                                            if (uri?.host == "accounts.google.com" && uri.path == "/gsi/transform") {
                                                // Google normally calls window.close after posting to the opener.
                                                // Keep the existing delayed-close fallback for WebView versions that miss it.
                                                view?.postDelayed({ if (popupWebView === view) popupWebView = null }, 2000)
                                            }
                                        }
                                    }
                                    webChromeClient = object : WebChromeClient() {
                                        override fun onCloseWindow(window: WebView?) { popupWebView = null }
                                    }
                                }
                                val transport = resultMsg?.obj as? WebView.WebViewTransport ?: run { popup.destroy(); return false }
                                transport.webView = popup
                                resultMsg.sendToTarget()
                                popupWebView = popup
                                return true
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                if (isDevin && url != null) currentUrl = url
                            }
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                return (isOAuth && request.isForMainFrame && viewModel.onOAuthNavigation(url)) ||
                                    (request.url.scheme != "https" && url != "about:blank")
                            }
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                if (isOAuth && url != null && viewModel.onOAuthNavigation(url)) { view?.stopLoading(); return }
                                pageLoading = true
                                currentUrl = url.orEmpty()
                            }
                            override fun onPageFinished(view: WebView?, url: String?) {
                                pageLoading = false
                                url ?: return
                                currentUrl = url
                                if (isOAuth) { view?.requestFocus(); viewModel.onOAuthNavigation(url); return }
                                if (Uri.parse(url).host == "accounts.google.com" && Uri.parse(url).path == "/gsi/transform") {
                                    view?.loadUrl(state.platform.loginUrl)
                                    return
                                }
                                if (isDevin) {
                                    if (DevinWebSession.isDevinPage(url)) view?.evaluateJavascript(DEVIN_VIEWPORT_FALLBACK, null)
                                    return
                                }
                                val cookie = readLoginCookie(state.platform, url)
                                if (cookie != null && (state.platform.isLoggedIn(url) || state.platform.hasSessionCookie(cookie))) {
                                    viewModel.onLoginSuccess(cookie, url)
                                }
                            }
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                if (request?.isForMainFrame == true) {
                                    pageLoading = false
                                    // WebView error descriptions can embed the full redirect URL; don't surface them.
                                    viewModel.onLoginError("登录页面加载失败，请检查网络后重试")
                                }
                            }
                        }
                        val target = this
                        if (isDevin) {
                            target.loadUrl(initialUrl)
                        } else {
                            // Load only after cookie cleanup completes.
                            CookieManager.getInstance().removeAllCookies {
                                if (mainWebView === target) target.loadUrl(initialUrl)
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            popupWebView?.let { popup ->
                DisposableEffect(popup) { onDispose { popup.stopLoading(); popup.destroy() } }
                AndroidView(factory = { popup }, modifier = Modifier.fillMaxSize())
            }
            if (!isOAuth && !state.isLoading && (!isDevin || canConfirmDevinSession)) ExtendedFloatingActionButton(
                onClick = {
                    if (isDevin) {
                        scope.launch {
                            val session = mainWebView?.readDevinSession()
                            if (session == null) viewModel.onLoginError("请先在 Devin 打开或新建一个对话，再验证额度")
                            else viewModel.onDevinSession(session, state.pageGeneration, manual = true)
                        }
                    } else {
                        readLoginCookie(state.platform, currentUrl)?.let { viewModel.onLoginSuccess(it, currentUrl, manual = true) }
                            ?: viewModel.onLoginError("未检测到登录 Cookie，请确认已完成登录")
                    }
                },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                icon = { AppIcon(AppIcons.CheckCircle, contentDescription = null) },
                text = { Text("已登录，验证额度") }
            )
            if (state.isLoading) Dialog(onDismissRequest = viewModel::useWebLogin) {
                Card {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text("正在验证账号和额度…")
                        TextButton(onClick = viewModel::useWebLogin) { Text("取消") }
                    }
                }
            }
        }
    }
    if (canConfirmDevinSession && !devinReminderDismissed) {
        DevinConversationReminder(currentUrl) { devinReminderDismissed = true }
    }
}

@Composable
private fun DevinConversationReminder(pageUrl: String, onDismiss: () -> Unit) {
    var visible by remember(pageUrl) { mutableStateOf(false) }
    LaunchedEffect(pageUrl) {
        // Let the automatic quota check finish before asking for a manual step.
        delay(2500)
        visible = true
    }
    if (visible) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("请打开一个 Devin 对话") },
            text = {
                Text("请打开任意已有对话。如果还没有对话，请新建一个并发送一条消息。知余会自动尝试读取额度。")
            },
            confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } }
        )
    }
}

// Some Android WebViews report a usable innerHeight while viewport CSS units resolve to zero.
private val DEVIN_VIEWPORT_FALLBACK = """
    (() => {
        if (window.top !== window || location.origin !== "${Platform.DEVIN.baseUrl}" ||
            !document.body || !window.innerHeight || document.getElementById("zhiyu-devin-viewport")) return;
        const probe = document.createElement("div");
        probe.style.cssText = "position:fixed; visibility:hidden; height:100dvh";
        document.body.append(probe);
        const height = probe.getBoundingClientRect().height;
        probe.remove();
        if (height > 0) return;
        const style = document.createElement("style");
        style.id = "zhiyu-devin-viewport";
        style.textContent = ".h-dvh{height:var(--zhiyu-viewport-height)!important}" +
            ".min-h-dvh{min-height:var(--zhiyu-viewport-height)!important}";
        const resize = () => document.documentElement.style.setProperty(
            "--zhiyu-viewport-height", (window.visualViewport?.height || window.innerHeight) + "px");
        resize();
        document.head.append(style);
        window.addEventListener("resize", resize);
        window.visualViewport?.addEventListener("resize", resize);
    })()
""".trimIndent()

private suspend fun WebView.readDevinSession(): DevinWebSession? {
    val page = url
    if (!DevinWebSession.isDevinPage(page)) return null
    val result = withTimeoutOrNull(2000) {
        suspendCancellableCoroutine<String?> { continuation ->
            evaluateJavascript(DevinWebSession.readScript) { value ->
                if (continuation.isActive) continuation.resume(value)
            }
        }
    } ?: return null
    if (url != page) return null
    return DevinWebSession.fromWebViewResult(result)
}

private fun WebView.configureForLogin() {
    isFocusable = true
    isFocusableInTouchMode = true
    settings.javaScriptEnabled = true
    settings.domStorageEnabled = true
    settings.javaScriptCanOpenWindowsAutomatically = true
    settings.setSupportMultipleWindows(true)
    settings.allowFileAccess = false
    settings.allowContentAccess = false
    settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
}

private fun readLoginCookie(platform: Platform, currentUrl: String): String? =
    (platform.getCookieDomains() + currentUrl).distinct()
        .mapNotNull { CookieManager.getInstance().getCookie(it) }
        .filter { AuthViewModel.extractCookieValue(platform, it) != null }
        .maxByOrNull { it.length }
