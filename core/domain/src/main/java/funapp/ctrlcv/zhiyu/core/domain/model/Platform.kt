package funapp.ctrlcv.zhiyu.core.domain.model

enum class Platform(
    val key: String,
    val displayName: String,
    val loginUrl: String,
    val baseUrl: String,
    val requiresApiKey: Boolean = false
) {
    CLAUDE(
        key = "claude",
        displayName = "Claude",
        loginUrl = "https://claude.ai/login",
        baseUrl = "https://claude.ai"
    ),
    CHATGPT(
        key = "chatgpt",
        displayName = "Codex",
        loginUrl = "https://chat.openai.com/auth/login",
        baseUrl = "https://chatgpt.com"
    ),
    CURSOR(
        key = "cursor",
        displayName = "Cursor",
        // authenticator.cursor.sh 是桌面 App 的深链接入口，需要 App 预先生成带
        // context 参数的 WorkOS URL，WebView 直接打开时 context 为空会返回 404。
        // 改为从 cursor.com 的 Web 登录页发起，由服务端正确生成 WorkOS 跳转链接。
        loginUrl = "https://cursor.com/login",
        baseUrl = "https://api2.cursor.sh"
    ),
    ZEN(
        key = "zen",
        displayName = "OpenCode",
        // OpenCode Zen 没有官方余额接口；余额只在网页控制台的 workspace 仪表盘可见。
        // 在内置 WebView 登录 opencode.ai 后复用其 Hapi/Iron 会话 Cookie（auth），
        // 直接抓取 SSR 渲染的 workspace 页面解析「Current balance / 現在の残高」。
        loginUrl = "https://opencode.ai/auth",
        baseUrl = "https://opencode.ai"
    ),
    MINIMAX(
        key = "minimax",
        displayName = "MiniMax",
        loginUrl = "",
        baseUrl = "https://api.minimax.chat",
        requiresApiKey = true
    ),
    AIHUBMIX(
        key = "aihubmix",
        displayName = "AIHubMix",
        loginUrl = "",
        baseUrl = "https://aihubmix.com",
        requiresApiKey = true
    ),
    DEEPSEEK(
        key = "deepseek",
        displayName = "DeepSeek",
        loginUrl = "",
        baseUrl = "https://api.deepseek.com",
        requiresApiKey = true
    );

    fun isLoggedIn(url: String): Boolean = when (this) {
        CLAUDE -> url.contains("claude.ai") &&
            (url.contains("/new") || url.contains("/chat") ||
             url.contains("/recents") || url.endsWith("claude.ai/"))
        CHATGPT -> (url.contains("chat.openai.com") || url.contains("chatgpt.com")) &&
            !url.contains("/auth") && !url.contains("/login")
        CURSOR -> url.contains("cursor.com") && !url.contains("authenticator") &&
            !url.contains("/sign-in") && !url.contains("/login")
        // Zen 的 auth Cookie 在 OAuth 握手阶段就被写入，无法用 Cookie 判定登录；
        // 只有真正登录后才会进入 workspace 仪表盘，用该 URL 作为唯一可靠的成功信号。
        ZEN -> url.contains("opencode.ai") && url.contains("/workspace")
        MINIMAX, AIHUBMIX, DEEPSEEK -> false
    }

    fun getCookieName(): String = when (this) {
        CLAUDE -> "sessionKey"
        CHATGPT -> "__Secure-next-auth.session-token"
        CURSOR -> "WorkosCursorSessionToken"
        // opencode.ai 用 Hapi/Iron 会话 Cookie，名字可能是 auth 或 __Host-auth，
        // 二者都包含 "auth"，用作 WebView 中的存在性匹配子串。
        ZEN -> "auth"
        MINIMAX, AIHUBMIX, DEEPSEEK -> "api_key"
    }

    fun getCookieDomains(): List<String> = when (this) {
        CLAUDE -> listOf("https://claude.ai")
        CHATGPT -> listOf("https://chatgpt.com", "https://chat.openai.com")
        CURSOR -> listOf("https://cursor.sh", "https://authenticator.cursor.sh", "https://api2.cursor.sh", "https://www.cursor.com", "https://cursor.com")
        ZEN -> listOf("https://opencode.ai")
        MINIMAX, AIHUBMIX, DEEPSEEK -> emptyList()
    }

    /**
     * 是否可「仅凭 Cookie」判定已登录——用于 WebView 中无需等到登录后页面就自动确认。
     *
     * Claude / Codex / Cursor 的会话 Cookie 名足够独特，出现即代表已登录。
     * 但 Zen 的 `auth`/`__Host-auth` Cookie 在 OAuth 握手阶段（点击 GitHub/Google、
     * 尚未完成认证）就会被写入，且同样是 Iron（`Fe26.2`）格式，单看 Cookie 无法区分
     * 「中间态」与「已登录」，否则点一下登录就会被误判而秒退。因此 Zen 返回 false，
     * 改由到达 workspace 仪表盘（[isLoggedIn]）来确认登录。
     */
    fun hasSessionCookie(cookieHeader: String): Boolean = when (this) {
        ZEN -> false
        else -> cookieHeader.contains(getCookieName())
    }
}
