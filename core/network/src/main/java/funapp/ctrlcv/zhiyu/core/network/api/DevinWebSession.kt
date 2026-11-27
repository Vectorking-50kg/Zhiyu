package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** A candidate from the app's own Devin sign-in page. A homepage may have no organization yet. */
@ConsistentCopyVisibility
data class DevinWebSession private constructor(val accessToken: String, val organizationId: String?) {
    override fun toString(): String = "DevinWebSession([REDACTED])"

    companion object {
        fun isDevinPage(url: String?): Boolean {
            val parsed = url?.toHttpUrlOrNull() ?: return false
            return parsed.scheme == "https" && parsed.host == "app.devin.ai" && parsed.port == 443 &&
                parsed.username.isEmpty() && parsed.password.isEmpty()
        }

        fun fromWebViewResult(result: String): DevinWebSession? = runCatching {
            val value = JsonParser.parseString(result)
            val decoded = if (value.isJsonPrimitive && value.asJsonPrimitive.isString) {
                JsonParser.parseString(value.asString)
            } else value
            val body = decoded.takeIf { it.isJsonObject }?.asJsonObject ?: return null
            val token = body.stringOrNull("accessToken")?.takeIf {
                it.startsWith("auth1_") && it.length > "auth1_".length && it.all { c -> c in '!'..'~' }
            } ?: return null
            val organizationId = body.get("organizationId")?.takeUnless { it.isJsonNull }?.let {
                body.stringOrNull("organizationId")?.takeIf(UsageApiService::isDevinOrganizationId) ?: return null
            }
            DevinWebSession(token, organizationId)
        }.getOrNull()

        // The provider writes this selection after the signed-in page has loaded. Prefer the
        // organization in the current route and never take another slug's saved selection.
        val readScript = """
            (() => {
                if (window.top !== window || location.origin !== "${Platform.DEVIN.baseUrl}") return null;
                const [, section, key] = location.pathname.split("/");
                if (["login", "signup", "auth"].includes(section) ||
                    location.pathname.endsWith("/settings/select-org")) return null;
                try {
                    const session = JSON.parse(localStorage.getItem("auth1_session"));
                    if (!session?.token || !session.userId) return null;
                    const isId = value => typeof value === "string" && value.startsWith("org") &&
                        (value[3] === "_" || value[3] === "-") && /^[A-Za-z0-9_-]+$/.test(value.slice(4));
                    const prefix = "last-internal-org-for-external-org-v1-";
                    let organizationId = null;
                    if (section === "org" || section === "organizations") {
                        if (!key) return null;
                        const current = decodeURIComponent(key);
                        organizationId = isId(current) ? current : localStorage.getItem(prefix + current);
                    } else {
                        organizationId = localStorage.getItem(prefix + "null");
                        if (!isId(organizationId)) {
                            const ids = new Set(Object.keys(localStorage)
                                .filter(name => name.startsWith(prefix)).map(name => localStorage.getItem(name)).filter(isId));
                            if (ids.size === 1) organizationId = ids.values().next().value;
                        }
                    }
                    return JSON.stringify({
                        accessToken: session.token,
                        organizationId: isId(organizationId) ? organizationId : null
                    });
                } catch (_) { return null; }
            })()
        """.trimIndent()
    }
}

class DevinOrganizationSelectionRequiredException : Exception()
