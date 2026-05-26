package funapp.ctrlcv.zhiyu.core.domain.model

enum class Platform(
    val key: String,
    val displayName: String,
    val loginUrl: String,
    val baseUrl: String
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
        loginUrl = "https://authenticator.cursor.sh",
        baseUrl = "https://api2.cursor.sh"
    );

    fun isLoggedIn(url: String): Boolean = when (this) {
        CLAUDE -> url.contains("claude.ai") &&
            (url.contains("/new") || url.contains("/chat") ||
             url.contains("/recents") || url.endsWith("claude.ai/"))
        CHATGPT -> (url.contains("chat.openai.com") || url.contains("chatgpt.com")) &&
            !url.contains("/auth") && !url.contains("/login")
        CURSOR -> url.contains("cursor.com") && !url.contains("authenticator") &&
            !url.contains("/sign-in") && !url.contains("/login")
    }

    fun getCookieName(): String = when (this) {
        CLAUDE -> "sessionKey"
        CHATGPT -> "__Secure-next-auth.session-token"
        CURSOR -> "WorkosCursorSessionToken"
    }

    fun getCookieDomains(): List<String> = when (this) {
        CLAUDE -> listOf("https://claude.ai")
        CHATGPT -> listOf("https://chatgpt.com", "https://chat.openai.com")
        CURSOR -> listOf("https://cursor.sh", "https://authenticator.cursor.sh", "https://api2.cursor.sh", "https://www.cursor.com")
    }
}
