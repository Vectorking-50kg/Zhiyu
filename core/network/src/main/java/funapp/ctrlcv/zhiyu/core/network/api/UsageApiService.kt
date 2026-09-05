package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.ResetCredits
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

data class ClaudeOrgInfo(val orgId: String, val planTier: String?, val displayName: String? = null)
data class ValidatedSession(val providerAccountId: String?, val displayName: String?, val usage: UsageInfo)

@Singleton
class UsageApiService @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    suspend fun getClaudeOrgInfo(cookie: String): ClaudeOrgInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations")
            .header("Cookie", "sessionKey=$cookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CLAUDE)
            .build()
        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CLAUDE)
            val orgs = try {
                com.google.gson.JsonParser.parseString(body).takeIf { it.isJsonArray }?.asJsonArray
                    ?.mapNotNull { it.takeIf { node -> node.isJsonObject }?.asJsonObject }
            } catch (_: Exception) { null }
                ?: throw ApiStructureChangedException(Platform.CLAUDE, "Invalid organization response")
            val org = orgs.firstOrNull { it.stringOrNull("uuid") != null && it.get("capabilities")
                ?.takeIf { caps -> caps.isJsonArray }?.asJsonArray?.any { cap ->
                    cap.isJsonPrimitive && (cap.asString == "chat" || cap.asString.startsWith("claude_"))
                } == true }
                ?: orgs.firstOrNull { it.stringOrNull("uuid") != null }
                ?: throw ApiStructureChangedException(Platform.CLAUDE, "No account organization found")
            val plan = org.get("capabilities")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.mapNotNull { it.takeIf { node -> node.isJsonPrimitive }?.asString }
                ?.firstOrNull { it.startsWith("claude_pro") || it.startsWith("claude_max") ||
                    it.startsWith("claude_team") || it.startsWith("claude_enterprise") || it == "free" }
                ?: org.stringOrNull("plan_tier")
            ClaudeOrgInfo(org.stringOrNull("uuid")!!, plan, org.stringOrNull("name"))
        }
    }

    suspend fun getClaudeUsage(cookie: String, orgInfo: ClaudeOrgInfo): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations".toHttpUrl().newBuilder().addPathSegment(orgInfo.orgId).addPathSegment("usage").build())
            .header("Cookie", "sessionKey=$cookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CLAUDE)
            .build()
        client.newCall(request).execute().use { response ->
            ClaudeUsageParser.parse(readOrThrow(response, Platform.CLAUDE), orgInfo.planTier, System.currentTimeMillis())
                .copy(providerAccountId = orgInfo.orgId)
        }
    }

    suspend fun getClaudeOAuthUsage(accessToken: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.anthropic.com/api/oauth/usage")
            .header("Authorization", "Bearer $accessToken")
            .header("anthropic-beta", "oauth-2025-04-20")
            .header("Accept", "application/json")
            .header("User-Agent", "claude-code/2.1.0")
            .tag(Platform::class.java, Platform.CLAUDE)
            .build()
        client.newCall(request).execute().use { response ->
            ClaudeUsageParser.parse(readOrThrow(response, Platform.CLAUDE), null, System.currentTimeMillis())
        }
    }

    private data class OpenAISession(val accessToken: String, val providerAccountId: String?, val displayName: String?)

    private suspend fun getOpenAISession(sessionCookie: String): OpenAISession {
        val request = Request.Builder()
            .url("https://chatgpt.com/api/auth/session")
            .header("Cookie", "__Secure-next-auth.session-token=$sessionCookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CHATGPT)
            .build()
        return client.newCall(request).execute().use { response ->
            val json = parseUsageObject(readOrThrow(response, Platform.CHATGPT), Platform.CHATGPT)
            val token = json.stringOrNull("accessToken") ?: run {
                if (json.size() == 0) throw SessionExpiredException(Platform.CHATGPT)
                throw ApiStructureChangedException(Platform.CHATGPT, "Session response has no access token")
            }
            val claims = runCatching {
                val payload = token.split('.').takeIf { it.size == 3 }?.get(1) ?: return@runCatching null
                val decoded = java.util.Base64.getUrlDecoder().decode(payload).toString(Charsets.UTF_8)
                com.google.gson.JsonParser.parseString(decoded).takeIf { it.isJsonObject }?.asJsonObject
            }.getOrNull()
            OpenAISession(
                accessToken = token,
                providerAccountId = claims?.objectOrNull("https://api.openai.com/auth")?.stringOrNull("chatgpt_account_id"),
                displayName = json.objectOrNull("user")?.stringOrNull("email") ?: json.objectOrNull("user")?.stringOrNull("name"),
            )
        }
    }

    suspend fun getChatGptUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        val session = getOpenAISession(cookie)
        fetchCodexUsage(session.accessToken, session.providerAccountId, cookie)
    }

    suspend fun getCodexOAuthUsage(accessToken: String, providerAccountId: String?): UsageInfo = withContext(Dispatchers.IO) {
        fetchCodexUsage(accessToken, providerAccountId, null)
    }

    private fun fetchCodexUsage(accessToken: String, providerAccountId: String?, cookie: String?): UsageInfo {
        // If the token does not identify a workspace, choose it before querying usage so the
        // snapshot and subscription metadata cannot silently refer to different accounts.
        val identityPlan = if (providerAccountId == null) fetchChatGptPlanInfo(accessToken, null) else null
        val selectedAccount = providerAccountId ?: identityPlan?.providerAccountId
        val request = codexRequest("https://chatgpt.com/backend-api/wham/usage", accessToken, selectedAccount)
            .apply { if (cookie != null) header("Cookie", "__Secure-next-auth.session-token=$cookie") }
            .tag(Platform::class.java, Platform.CHATGPT).build()
        val parsed = client.newCall(request).execute().use { response ->
            CodexUsageParser.parse(readOrThrow(response, Platform.CHATGPT), System.currentTimeMillis())
        }
        // These website-only additions are optional for OAuth tokens and never invalidate quota.
        val plan = if (providerAccountId == null) identityPlan else fetchChatGptPlanInfo(accessToken, selectedAccount)
        val cards = fetchChatGptResetCreditList(accessToken, selectedAccount) ?: parsed.resetCredits
        val renewal = plan?.renewIso?.let(::formatRenewDate)?.takeIf { it.isNotBlank() }?.let {
            UsageItem(label = if (plan.hasActive) "续订时间" else "到期时间", percent = -1f,
                valueText = it, windowId = "codex.subscription")
        }
        return parsed.copy(
            items = parsed.items + listOfNotNull(renewal),
            planLabel = plan?.planType?.let(::formatChatGptPlan) ?: parsed.planLabel,
            providerAccountId = selectedAccount, resetCredits = cards,
        )
    }

    private fun codexRequest(url: String, accessToken: String, providerAccountId: String?): Request.Builder =
        Request.Builder().url(url).header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT).header("Accept", "application/json")
            .apply { providerAccountId?.takeIf { it.isNotBlank() }?.let { header("ChatGPT-Account-Id", it) } }

    private data class ChatGptPlanInfo(val planType: String?, val hasActive: Boolean, val renewIso: String?, val providerAccountId: String?)

    private fun fetchChatGptPlanInfo(accessToken: String, providerAccountId: String?): ChatGptPlanInfo? {
        return try {
            val request = codexRequest("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27", accessToken, providerAccountId).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val json = parseUsageObject(response.body?.string() ?: return null, Platform.CHATGPT)
                val accounts = json.objectOrNull("accounts") ?: return null
                val orderedId = json.get("account_ordering")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.firstOrNull()?.takeIf { it.isJsonPrimitive }?.asString
                val key = if (providerAccountId != null) {
                    // Some responses use a "default" map key; still require its explicit account
                    // identity to match, rather than attaching another workspace's subscription.
                    providerAccountId.takeIf { accounts.objectOrNull(it) != null }
                        ?: accounts.entrySet().firstOrNull { entry ->
                            val account = entry.value.takeIf { it.isJsonObject }?.asJsonObject?.objectOrNull("account")
                            (account?.stringOrNull("account_id") ?: account?.stringOrNull("id")) == providerAccountId
                        }?.key
                } else {
                    orderedId?.takeIf { accounts.objectOrNull(it) != null }
                        ?: "default".takeIf { accounts.objectOrNull(it) != null }
                } ?: return null
                val node = accounts.objectOrNull(key) ?: return null
                val account = node.objectOrNull("account")
                val entitlement = node.objectOrNull("entitlement")
                ChatGptPlanInfo(
                    planType = account?.stringOrNull("plan_type") ?: entitlement?.stringOrNull("subscription_plan"),
                    hasActive = entitlement?.booleanOrNull("has_active_subscription") ?: false,
                    renewIso = entitlement?.stringOrNull("renews_at") ?: entitlement?.stringOrNull("expires_at"),
                    providerAccountId = account?.stringOrNull("account_id") ?: account?.stringOrNull("id") ?: key.takeUnless { it == "default" },
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchChatGptResetCreditList(accessToken: String, providerAccountId: String?): ResetCredits? {
        return try {
            val request = codexRequest("https://chatgpt.com/backend-api/wham/rate-limit-reset-credits", accessToken, providerAccountId).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                CodexUsageParser.parseResetCredits(parseUsageObject(response.body?.string() ?: return null, Platform.CHATGPT), System.currentTimeMillis())
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        }
    }

    suspend fun validateCookie(platform: Platform, cookie: String, workspaceId: String? = null): ValidatedSession = withContext(Dispatchers.IO) {
        when (platform) {
            Platform.CLAUDE -> {
                val org = getClaudeOrgInfo(cookie)
                ValidatedSession(org.orgId, org.displayName, getClaudeUsage(cookie, org))
            }
            Platform.CHATGPT -> {
                val session = getOpenAISession(cookie)
                val usage = fetchCodexUsage(session.accessToken, session.providerAccountId, cookie)
                val identity = usage.providerAccountId
                    ?: throw ApiStructureChangedException(platform, "Could not identify the Codex account")
                ValidatedSession(identity, session.displayName, usage)
            }
            else -> {
                val usage = when (platform) {
                    Platform.CURSOR -> getCursorUsage(cookie)
                    Platform.ZEN -> getZenUsage(cookie, workspaceId)
                    Platform.MINIMAX -> getMiniMaxUsage(cookie)
                    Platform.AIHUBMIX -> getAiHubMixUsage(cookie)
                    Platform.DEEPSEEK -> getDeepSeekUsage(cookie)
                    else -> error("Unsupported platform")
                }
                if (usage.items.isEmpty()) throw ApiStructureChangedException(platform, "No usage data found")
                ValidatedSession(null, null, usage)
            }
        }
    }

    suspend fun getCursorUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        val accessToken = extractCursorToken(cookie)
        val items = mutableListOf<UsageItem>()
        var sawSessionExpired = false
        var cursorPlanLabel: String? = null

        // 1) Membership / subscription info (full_stripe_profile). This already worked, so
        // fetch it first to guarantee the card always has content even if usage RPC changes.
        try {
            cursorPlanLabel = fetchCursorMembership(accessToken, cookie, items)
        } catch (e: SessionExpiredException) {
            sawSessionExpired = true
        } catch (e: Exception) {
            // ignore: usage RPC below may still succeed
        }

        // 2) Real period usage from the dashboard RPC (Connect protocol, JSON).
        try {
            fetchCursorPeriodUsage(accessToken, cookie, items)
        } catch (e: SessionExpiredException) {
            sawSessionExpired = true
        } catch (e: Exception) {
            // ignore: membership info above may already be present
        }

        if (items.isEmpty()) {
            if (sawSessionExpired) throw SessionExpiredException(Platform.CURSOR)
            throw ApiStructureChangedException(
                Platform.CURSOR,
                "No membership or usage data returned (endpoint structure may have changed)"
            )
        }

        UsageInfo(
            platform = Platform.CURSOR,
            items = items,
            planLabel = cursorPlanLabel,
            updatedAt = System.currentTimeMillis()
        )
    }

    // WebView may store the cookie value URL-encoded (%3A%3A instead of ::).
    // Decode first so we can correctly split the "userId::accessJwt" format.
    private fun extractCursorToken(cookie: String): String {
        val decoded = try {
            java.net.URLDecoder.decode(cookie, "UTF-8")
        } catch (e: Exception) {
            cookie
        }
        return when {
            decoded.startsWith("eyJ") -> decoded            // already a bare JWT
            "::" in decoded -> decoded.substringAfter("::")  // userId::jwt (decoded)
            "::" in cookie -> cookie.substringAfter("::")    // jwt not encoded, :: is raw
            else -> cookie                                    // unknown format, pass through
        }
    }

    private fun fetchCursorMembership(accessToken: String, cookie: String, items: MutableList<UsageItem>): String? {
        val request = Request.Builder()
            .url("https://api2.cursor.sh/auth/full_stripe_profile")
            .header("Cookie", "WorkosCursorSessionToken=$cookie")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CURSOR)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CURSOR)
            val json = gson.fromJson(body, JsonObject::class.java)
            val membership = json.get("membershipType")?.takeUnless { it.isJsonNull }?.asString
                ?: json.get("individualMembershipType")?.takeUnless { it.isJsonNull }?.asString
            val status = json.get("subscriptionStatus")?.takeUnless { it.isJsonNull }?.asString
            return membership?.let { formatCursorMembership(it) }
        }
    }

    // GetCurrentPeriodUsage is a unary Connect-RPC call: POST empty JSON body, Bearer auth.
    // Response carries spend (cents) / limit (cents) and per-mode used percentages.
    private fun fetchCursorPeriodUsage(accessToken: String, cookie: String, items: MutableList<UsageItem>) {
        val request = Request.Builder()
            .url("https://api2.cursor.sh/aiserver.v1.DashboardService/GetCurrentPeriodUsage")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $accessToken")
            .header("Cookie", "WorkosCursorSessionToken=$cookie")
            .header("Connect-Protocol-Version", "1")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CURSOR)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CURSOR)
            val json = gson.fromJson(body, JsonObject::class.java)
            // Usage numbers are nested under "planUsage"; fall back to top-level for safety.
            val usage = json.objectOrNull("planUsage") ?: json
            val spendCents = (usage.get("used") ?: usage.get("totalSpend"))
                ?.takeUnless { it.isJsonNull }?.asDouble
            val limitCents = usage.get("limit")?.takeUnless { it.isJsonNull }?.asDouble
            val totalPercent = usage.get("totalPercentUsed")?.takeUnless { it.isJsonNull }?.asFloat
            val percent = totalPercent
                ?: if (limitCents != null && limitCents > 0 && spendCents != null) {
                    (spendCents / limitCents * 100.0).toFloat()
                } else null

            if (percent != null) {
                val valueText = if (spendCents != null && limitCents != null && limitCents > 0) {
                    "\$${String.format("%.2f", spendCents / 100.0)} / \$${String.format("%.2f", limitCents / 100.0)}"
                } else null
                val reset = (json.get("billingCycleEnd") ?: usage.get("billingCycleEnd"))
                    ?.takeUnless { it.isJsonNull }?.asString?.toLongOrNull()
                    ?.let { formatResetTimestampMs(it) }
                items.add(UsageItem(
                    label = "本周期用量",
                    percent = percent.coerceIn(0f, 100f),
                    valueText = valueText,
                    resetCountdown = reset
                ))
            }

            usage.get("autoPercentUsed")?.takeUnless { it.isJsonNull }?.asFloat?.let {
                items.add(UsageItem(label = "Auto 用量", percent = it.coerceIn(0f, 100f)))
            }
            usage.get("apiPercentUsed")?.takeUnless { it.isJsonNull }?.asFloat?.let {
                items.add(UsageItem(label = "API 用量", percent = it.coerceIn(0f, 100f)))
            }
        }
    }

    // ── OpenCode Zen ─────────────────────────────────────────────────────────
    // Zen 没有官方「查余额」接口（issue anomalyco/opencode#10448 仍 Open）。余额来自网页控制台
    // opencode.ai 的 workspace 仪表盘。复用网页登录得到的 Hapi/Iron 会话 Cookie（auth /
    // __Host-auth），按 CodexBar 的实现取数：
    //   1) 先抓 /workspace/{id} 仪表盘 SSR 页面解析余额（无需构建哈希，命中最省事）；
    //   2) 命中不到时回退到 Zen 的 SolidStart server function：
    //      GET /_server?id=<billingHash>&args=["<wid>"]，返回 text/javascript，
    //      从中解析 customerID + balance（balance / 1e8 = 美元）。
    // 注意：server function 的 id 是构建哈希，opencode.ai 每次部署可能变化，失效时需对照
    // CodexBar 的 OpenCodeGoUsageFetcher 更新 ZEN_*_SERVER_ID。
    suspend fun getZenUsage(cookieHeader: String, workspaceId: String? = null): UsageInfo = withContext(Dispatchers.IO) {
        val wid = resolveZenWorkspaceId(cookieHeader, workspaceId)
            ?: throw ApiStructureChangedException(
                Platform.ZEN,
                "未能确定 workspace（请在设置中重新登录 Zen）"
            )

        val balance = fetchZenBalance(cookieHeader, wid)
            ?: throw ApiStructureChangedException(
                Platform.ZEN,
                "未能解析到余额（控制台页面或接口结构可能已变化）"
            )

        UsageInfo(
            platform = Platform.ZEN,
            items = listOf(
                UsageItem(
                    label = "账户余额",
                    percent = -1f,
                    valueText = "\$${String.format("%.2f", balance)}"
                )
            ),
            updatedAt = System.currentTimeMillis()
        )
    }

    // 确定 workspace id：优先用登录时捕获的；缺失时调 workspaces server function 发现。
    private fun resolveZenWorkspaceId(cookieHeader: String, captured: String?): String? {
        normalizeZenWorkspaceId(captured)?.let { return it }

        val text = fetchZenServer(ZEN_WORKSPACES_SERVER_ID, args = null, method = "GET", referer = ZEN_BASE_URL, cookieHeader = cookieHeader)
            ?: fetchZenServer(ZEN_WORKSPACES_SERVER_ID, args = "[]", method = "POST", referer = ZEN_BASE_URL, cookieHeader = cookieHeader)
        if (text != null) {
            if (zenServerLooksSignedOut(text)) throw SessionExpiredException(Platform.ZEN)
            ZEN_WORKSPACE_ID_IN_TEXT.find(text)?.value?.let { return it }
        }
        return null
    }

    private fun normalizeZenWorkspaceId(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.startsWith("wrk_") && trimmed.length > 4) return trimmed
        return ZEN_WORKSPACE_ID_IN_TEXT.find(trimmed)?.value
    }

    private fun fetchZenBalance(cookieHeader: String, workspaceId: String): Double? {
        val dashboardUrl = "https://opencode.ai/workspace/$workspaceId"

        // 1) 仪表盘页面（SSR 命中则无需碰构建哈希）
        fetchZenPageOrNull(dashboardUrl, cookieHeader)?.let { (finalUrl, html) ->
            throwIfZenSignedOut(finalUrl)
            parseZenBalance(html)?.let { return it }
            }

        // 2) billing server function（可靠来源）
        val billingText = fetchZenServer(
            serverId = ZEN_BILLING_SERVER_ID,
            args = "[\"$workspaceId\"]",
            method = "GET",
            referer = dashboardUrl,
            cookieHeader = cookieHeader,
        ) ?: return null
        if (zenServerLooksSignedOut(billingText)) throw SessionExpiredException(Platform.ZEN)
        return parseZenBillingBalance(billingText)
    }

    // 调 opencode.ai 的 SolidStart server function（/_server）。GET 时 id/args 走查询参数，
    // 非 GET 时 args 作为 JSON 请求体；统一带 X-Server-Id 等头。失败返回 null（会话失效抛出）。
    private fun fetchZenServer(
        serverId: String,
        args: String?,
        method: String,
        referer: String,
        cookieHeader: String,
    ): String? {
        val url = if (method.equals("GET", ignoreCase = true)) {
            val builder = ZEN_SERVER_URL.toHttpUrl().newBuilder().addQueryParameter("id", serverId)
            if (!args.isNullOrEmpty()) builder.addQueryParameter("args", args)
            builder.build()
        } else {
            ZEN_SERVER_URL.toHttpUrl()
        }

        val requestBuilder = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("X-Server-Id", serverId)
            .header("X-Server-Instance", "server-fn:${java.util.UUID.randomUUID()}")
            .header("User-Agent", USER_AGENT)
            .header("Origin", ZEN_BASE_URL)
            .header("Referer", referer)
            .header("Accept", "text/javascript, application/json;q=0.9, */*;q=0.8")
            .tag(Platform::class.java, Platform.ZEN)

        if (method.equals("GET", ignoreCase = true)) {
            requestBuilder.get()
        } else {
            requestBuilder.method(method.uppercase(), (args ?: "").toRequestBody("application/json".toMediaType()))
        }

        return try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.code == 401 || response.code == 403) throw SessionExpiredException(Platform.ZEN)
                val body = response.body?.string()
                if (response.isSuccessful) body else null
            }
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }

    // 解析 billing server function 响应（text/javascript，seroval 序列化）：
    // 先按 JSON 尝试（找同时含非空 customerID 与 balance 的对象），失败再用正则。balance / 1e8 = 美元。
    private fun parseZenBillingBalance(text: String): Double? {
        runCatching {
            val root = gson.fromJson(text, JsonElement::class.java)
            findRawZenBillingBalance(root)?.let { return it / ZEN_BILLING_SCALE }
        }
        if (!ZEN_BILLING_CUSTOMER.containsMatchIn(text)) return null
        val raw = ZEN_BILLING_BALANCE.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: return null
        return raw / ZEN_BILLING_SCALE
    }

    // 递归查找含「非空 customerID + balance」的对象，返回原始 balance（未缩放）。
    private fun findRawZenBillingBalance(el: JsonElement?): Double? {
        when {
            el == null || el.isJsonNull -> return null
            el.isJsonObject -> {
                val obj = el.asJsonObject
                if (obj.has("balance")) {
                    val customer = obj.get("customerID")?.takeIf { it.isJsonPrimitive }?.asString
                    if (!customer.isNullOrEmpty()) {
                        zenBillingDouble(obj.get("balance"))?.let { return it }
                    }
                }
                for (entry in obj.entrySet()) findRawZenBillingBalance(entry.value)?.let { return it }
            }
            el.isJsonArray -> for (item in el.asJsonArray) findRawZenBillingBalance(item)?.let { return it }
        }
        return null
    }

    private fun zenBillingDouble(el: JsonElement?): Double? {
        val p = el?.takeIf { it.isJsonPrimitive }?.asJsonPrimitive ?: return null
        return when {
            p.isBoolean -> null
            p.isNumber -> p.asDouble
            p.isString -> p.asString.trim().replace(",", "").toDoubleOrNull()
            else -> null
        }
    }

    // server function 在未登录时返回的特征文案（对齐 CodexBar 的 looksSignedOut 强信号）。
    private fun zenServerLooksSignedOut(text: String): Boolean {
        val lower = text.lowercase()
        return lower.contains("auth/authorize") ||
            lower.contains("not associated with an account") ||
            lower.contains("actor of type \"public\"")
    }

    private fun fetchZenPageOrNull(url: String, cookieHeader: String): Pair<String, String>? =
        try {
            fetchZenPage(url, cookieHeader)
        } catch (e: SessionExpiredException) {
            throw e
        } catch (e: Exception) {
            null
        }

    private fun fetchZenPage(url: String, cookieHeader: String): Pair<String, String> {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", cookieHeader)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .tag(Platform::class.java, Platform.ZEN)
            .build()

        client.newCall(request).execute().use { response ->
            val finalUrl = response.request.url.toString()
            val body = readOrThrow(response, Platform.ZEN)
            // OkHttp 同域重定向会保留 Cookie 头；取最终 URL 用于登出判断
            return finalUrl to body
        }
    }

    // 未登录时控制台会把 /workspace 重定向到 /auth（HTTP 200），据此判定会话失效。
    private fun throwIfZenSignedOut(finalUrl: String) {
        if (finalUrl.contains("/auth") || finalUrl.contains("/login")) {
            throw SessionExpiredException(Platform.ZEN)
        }
    }

    private fun parseZenBalance(html: String): Double? {
        for (pattern in ZEN_BALANCE_PATTERNS) {
            val raw = pattern.find(html)?.groupValues?.getOrNull(1) ?: continue
            raw.replace(",", "").toDoubleOrNull()?.let { return it }
        }
        return null
    }

    private fun formatChatGptPlan(plan: String): String = when (plan.lowercase()) {
        "plus", "chatgptplusplan" -> "Plus"
        "pro", "chatgptproplan" -> "Pro"
        "free", "chatgptfreeplan" -> "Free"
        "team", "chatgptteamplan" -> "Team"
        "go", "chatgptgoplan" -> "Go"
        else -> plan.replaceFirstChar { it.uppercase() }
    }

    private fun formatCursorMembership(type: String): String = when (type.lowercase()) {
        "pro" -> "Pro"
        "free" -> "Free"
        "free_trial" -> "免费试用"
        "pro_plus", "pro-plus" -> "Pro+"
        "ultra" -> "Ultra"
        "enterprise" -> "Enterprise"
        else -> type.replaceFirstChar { it.uppercase() }
    }

    private fun formatRenewDate(iso: String): String {
        return try {
            val target = java.time.OffsetDateTime.parse(iso)
            val now = java.time.OffsetDateTime.now()
            val days = java.time.Duration.between(now, target).toDays()
            when {
                days > 1 -> "${days}天后"
                days == 1L -> "明天"
                days == 0L -> "今日"
                else -> "已过期"
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ── MiniMax ──────────────────────────────────────────────────────────────
    // GET https://www.minimaxi.com/v1/token_plan/remains
    // Authorization: Bearer {token_plan_api_key}
    // 返回 model_remains 数组，每个模型含「5h 间隔限额」与「周限额」两个窗口。
    // 改为按用量计费后用 *_remaining_percent / *_status 表示余量（不再用请求次数），
    // 计费切换期间还会下发 *_boost_permille 提升倍率。仅展示 general 主额度（与官网「我的用量」一致）。
    suspend fun getMiniMaxUsage(apiKey: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://www.minimaxi.com/v1/token_plan/remains")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .tag(Platform::class.java, Platform.MINIMAX)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.MINIMAX)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)

                // base_resp.status_code：1004 为鉴权失败（API Key 无效），按会话过期处理
                json.getAsJsonObject("base_resp")?.get("status_code")?.asInt?.let { code ->
                    if (code == 1004) throw SessionExpiredException(Platform.MINIMAX)
                    if (code != 0) throw ApiStructureChangedException(Platform.MINIMAX, "base_resp status_code=$code")
                }

                val models = json.getAsJsonArray("model_remains")
                    ?: throw ApiStructureChangedException(Platform.MINIMAX, "missing model_remains")

                // 仅展示 general 主额度；找不到时退回首个模型，避免出现空卡片
                val general = models
                    .map { it.asJsonObject }
                    .firstOrNull { it.get("model_name")?.asString == "general" }
                    ?: models.firstOrNull()?.asJsonObject
                    ?: throw ApiStructureChangedException(Platform.MINIMAX, "empty model_remains")

                val items = listOf(
                    buildMinimaxUsageItem(
                        obj = general,
                        label = "5 小时限额",
                        statusKey = "current_interval_status",
                        remainingPercentKey = "current_interval_remaining_percent",
                        remainsTimeKey = "remains_time",
                        boostKey = "interval_boost_permille",
                        windowSeconds = FIVE_HOUR_SECONDS
                    ),
                    buildMinimaxUsageItem(
                        obj = general,
                        label = "周限额",
                        statusKey = "current_weekly_status",
                        remainingPercentKey = "current_weekly_remaining_percent",
                        remainsTimeKey = "weekly_remains_time",
                        boostKey = "weekly_boost_permille",
                        windowSeconds = WEEKLY_SECONDS
                    )
                )

                UsageInfo(
                    platform = Platform.MINIMAX,
                    items = items,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.MINIMAX, "Failed to parse usage response")
            }
        }
    }

    // MiniMax Token Plan 单个限额窗口（5h / 周）。status == 3 表示该窗口无上限（无限制）。
    private fun buildMinimaxUsageItem(
        obj: JsonObject,
        label: String,
        statusKey: String,
        remainingPercentKey: String,
        remainsTimeKey: String,
        boostKey: String,
        windowSeconds: Long
    ): UsageItem {
        val status = obj.get(statusKey)?.asInt ?: 0
        if (status == MINIMAX_STATUS_UNLIMITED) {
            return UsageItem(label = label, percent = 0f, unlimited = true)
        }
        val remainingPercent = obj.get(remainingPercentKey)?.asInt ?: 100
        val usedPercent = (100 - remainingPercent).coerceIn(0, 100).toFloat()
        val remainsTimeMs = obj.get(remainsTimeKey)?.asLong
        // boost_permille 为千分比，2000 → 200%；仅在有提升（> 1000，即超过原始 100%）时展示「总额度」
        val boostPercent = obj.get(boostKey)?.asInt?.takeIf { it > 1000 }?.div(10)
        return UsageItem(
            label = label,
            percent = usedPercent,
            resetCountdown = remainsTimeMs?.let { formatMinimaxResetCountdown(it) },
            boostPercent = boostPercent,
            elapsedPercent = remainsTimeMs?.let { elapsedPercentFromRemaining(it / 1000L, windowSeconds) }
        )
    }

    // remains_time 为距离重置的剩余毫秒数
    private fun formatMinimaxResetCountdown(remainingMs: Long): String {
        return try {
            if (remainingMs <= 0) return "即将重置"
            val hours = remainingMs / 3_600_000L
            val minutes = (remainingMs % 3_600_000L) / 60_000L
            when {
                hours > 24 -> "${hours / 24}天后重置"
                hours > 0 -> "${hours}小时${if (minutes > 0) "${minutes}分钟" else ""}后重置"
                minutes > 0 -> "${minutes}分钟后重置"
                else -> "即将重置"
            }
        } catch (e: Exception) {
            ""
        }
    }

    // ── AIHubMix ─────────────────────────────────────────────────────────────
    // Docs: https://docs.aihubmix.com/cn/api/Cli
    // GET https://aihubmix.com/api/user/self
    // Authorization: {token}  （无 Bearer 前缀）
    // 余额（CNY）= quota / 500000
    suspend fun getAiHubMixUsage(token: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://aihubmix.com/api/user/self")
            .header("Authorization", token)          // 无 Bearer 前缀
            .header("Content-Type", "application/json")
            .tag(Platform::class.java, Platform.AIHUBMIX)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.AIHUBMIX)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val success = json.get("success")?.asBoolean ?: false
                if (!success) {
                    val msg = json.get("message")?.asString ?: "Request failed"
                    throw ApiStructureChangedException(Platform.AIHUBMIX, msg)
                }

                val data = json.getAsJsonObject("data")
                    ?: throw ApiStructureChangedException(Platform.AIHUBMIX, "Missing data field")

                val quota = data.get("quota")?.asLong ?: 0L
                val usedQuota = data.get("used_quota")?.asLong ?: 0L
                val requestCount = data.get("request_count")?.asLong ?: 0L

                // 1 USD = 500000 quota 单位
                val remainingUsd = quota / 500000.0
                val spentUsd = usedQuota / 500000.0

                val items = mutableListOf<UsageItem>()
                items.add(UsageItem(
                    label = "余额",
                    percent = -1f,
                    valueText = "\$${String.format("%.4f", remainingUsd)}"
                ))
                items.add(UsageItem(
                    label = "已消费",
                    percent = -1f,
                    valueText = "\$${String.format("%.4f", spentUsd)}"
                ))
                items.add(UsageItem(
                    label = "累计请求次数",
                    percent = -1f,
                    valueText = "$requestCount 次"
                ))

                UsageInfo(
                    platform = Platform.AIHUBMIX,
                    items = items,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.AIHUBMIX, "Failed to parse usage response")
            }
        }
    }

    // ── DeepSeek ─────────────────────────────────────────────────────────────
    // Docs: https://platform.deepseek.com/api-docs/zh-cn/api/get-user-balance
    // GET https://api.deepseek.com/user/balance
    // Authorization: Bearer {api_key}
    suspend fun getDeepSeekUsage(apiKey: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api.deepseek.com/user/balance")
            .header("Authorization", "Bearer $apiKey")
            .header("Accept", "application/json")
            .tag(Platform::class.java, Platform.DEEPSEEK)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.DEEPSEEK)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val isAvailable = json.get("is_available")?.asBoolean ?: false
                val balanceArray = json.getAsJsonArray("balance_infos")
                    ?: throw ApiStructureChangedException(Platform.DEEPSEEK, "Missing balance_infos field")

                val items = mutableListOf<UsageItem>()
                balanceArray.forEach { element ->
                    val info = element.asJsonObject
                    val currency = info.get("currency")?.asString ?: "CNY"
                    val totalBalance = info.get("total_balance")?.asString ?: "0.00"
                    val grantedBalance = info.get("granted_balance")?.asString ?: "0.00"
                    val toppedUpBalance = info.get("topped_up_balance")?.asString ?: "0.00"

                    val currencySymbol = if (currency == "CNY") "¥" else "$"

                    items.add(UsageItem(
                        label = "账户余额",
                        percent = -1f,
                        valueText = "$currencySymbol$totalBalance"
                    ))
                    val grantedVal = grantedBalance.toDoubleOrNull() ?: 0.0
                    if (grantedVal > 0.0) {
                        items.add(UsageItem(
                            label = "赠送余额",
                            percent = -1f,
                            valueText = "$currencySymbol$grantedBalance"
                        ))
                    }
                    items.add(UsageItem(
                        label = "充值余额",
                        percent = -1f,
                        valueText = "$currencySymbol$toppedUpBalance"
                    ))
                }

                val statusText = if (isAvailable) "账户可用" else "账户不可用"

                UsageInfo(
                    platform = Platform.DEEPSEEK,
                    items = items,
                    resetInfo = statusText,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.DEEPSEEK, "Failed to parse usage response")
            }
        }
    }

    private fun readOrThrow(response: Response, platform: Platform): String =
        UsageHttpResponse.read(response, platform)

    private fun formatResetTimestampMs(ms: Long): String {
        return try {
            val target = java.time.Instant.ofEpochMilli(ms)
            val now = java.time.Instant.now()
            val days = java.time.Duration.between(now, target).toDays()
            when {
                days > 1 -> "${days}天后重置"
                days == 1L -> "明天重置"
                days == 0L -> "今日重置"
                else -> "已结束"
            }
        } catch (e: Exception) {
            ""
        }
    }

    // 窗口已过去的时间比例（0-100），供「奶油」主题双段进度条使用；windowSeconds 为该窗口固定总时长。
    private fun elapsedPercentFromRemaining(remainingSeconds: Long, windowSeconds: Long): Float =
        (((windowSeconds - remainingSeconds).toFloat() / windowSeconds) * 100f).coerceIn(0f, 100f)

    private fun formatTokenCount(count: Long): String = when {
        count >= 1_000_000 -> "${count / 1_000_000}M"
        count >= 1_000 -> "${count / 1_000}K"
        else -> count.toString()
    }

    private fun formatQuota(quota: Long): String = when {
        quota >= 1_000_000 -> String.format("%.1fM", quota / 1_000_000.0)
        quota >= 1_000 -> String.format("%.1fK", quota / 1_000.0)
        else -> quota.toString()
    }

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

        // MiniMax Token Plan：current_*_status == 3 表示该窗口无上限（无限制）
        private const val MINIMAX_STATUS_UNLIMITED = 3

        // 固定窗口总时长（秒），用于推算「时间已过去比例」（elapsedPercent）
        private const val FIVE_HOUR_SECONDS = 5 * 60 * 60L
        private const val WEEKLY_SECONDS = 7 * 24 * 60 * 60L

        // ── OpenCode Zen / opencode.ai server function 常量 ──
        private const val ZEN_BASE_URL = "https://opencode.ai"
        private const val ZEN_SERVER_URL = "https://opencode.ai/_server"
        // 原始 balance 的缩放：balance / 1e8 = 美元（对齐 CodexBar billingScale）
        private const val ZEN_BILLING_SCALE = 100_000_000.0
        // SolidStart server function 的 id 是构建哈希，opencode.ai 每次部署可能变化；
        // 失效时对照 CodexBar 的 OpenCodeGoUsageFetcher.swift 更新这两个值。
        private const val ZEN_BILLING_SERVER_ID = "c83b78a614689c38ebee981f9b39a8b377716db85c1fd7dbab604adc02d3313d"
        private const val ZEN_WORKSPACES_SERVER_ID = "def39973159c7f0483d8793a822b8dbb10d067e12c65455fcb4608459ba0234f"

        // workspace id 形如 wrk_xxx
        private val ZEN_WORKSPACE_ID_IN_TEXT = Regex("wrk_[A-Za-z0-9]+")
        // billing 响应（text/javascript / seroval）里 customerID 与 balance 的提取，
        // 兼容 seroval 的 $R[n]= 引用前缀。
        private val ZEN_BILLING_CUSTOMER = Regex("(?:\"customerID\"|customerID)\\s*:\\s*(?:\\\$R\\[\\d+\\]\\s*=\\s*)?\"[^\"]+\"")
        private val ZEN_BILLING_BALANCE = Regex("(?:\"balance\"|balance)\\s*:\\s*(?:\\\$R\\[\\d+\\]\\s*=\\s*)?(-?[0-9]+(?:\\.[0-9]+)?)")

        // 依次尝试解析 Zen 余额（取首个命中分组）：
        // 1) 「Current balance / Zen balance / 現在の残高」标签后紧跟的美元金额
        // 2) SSR 水合数据里的余额字段（驼峰 / 下划线均覆盖）
        // 3) 兜底：「balance / 残高」附近的美元金额
        private val ZEN_BALANCE_PATTERNS = listOf(
            Regex("(?i)(?:current\\s+balance|zen\\s+balance|現在の残高)[^$]{0,80}[$]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"),
            Regex("(?i)\"(?:zen_?balance|current_?balance(?:_?usd)?|balance_?usd|usd_?balance)\"\\s*:\\s*\"?([0-9][0-9.]+)\"?"),
            Regex("(?i)(?:balance|残高)[\\s\\S]{0,120}?[$]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)"),
        )
    }
}
