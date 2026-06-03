package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.SessionExpiredException
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

data class ClaudeOrgInfo(val orgId: String, val planTier: String?)

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
            try {
                val orgs = gson.fromJson<List<JsonObject>>(body, object : TypeToken<List<JsonObject>>() {}.type)
                val org = orgs.firstOrNull()
                    ?: throw ApiStructureChangedException(Platform.CLAUDE, "No organization found")
                val orgId = org.get("uuid")?.asString
                    ?: throw ApiStructureChangedException(Platform.CLAUDE, "No organization found")
                val planTier = org.getAsJsonArray("capabilities")
                    ?.asSequence()
                    ?.mapNotNull { it.takeUnless { el -> el.isJsonNull }?.asString }
                    ?.firstOrNull { cap ->
                        cap.startsWith("claude_pro") || cap.startsWith("claude_max") ||
                        cap.startsWith("claude_team") || cap.startsWith("claude_enterprise") ||
                        cap == "free"
                    }
                    ?: org.get("plan_tier")?.takeUnless { it.isJsonNull }?.asString
                ClaudeOrgInfo(orgId, planTier)
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CLAUDE, "Failed to parse organizations: ${e.message}")
            }
        }
    }

    suspend fun getClaudeUsage(cookie: String, orgInfo: ClaudeOrgInfo): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations/${orgInfo.orgId}/usage")
            .header("Cookie", "sessionKey=$cookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CLAUDE)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CLAUDE)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = mutableListOf<UsageItem>()

                parseClaudeBucket(json, "five_hour", "5 小时限额")?.let(items::add)
                parseClaudeBucket(json, "seven_day", "周限额｜所有模型")?.let(items::add)
                parseClaudeBucket(json, "seven_day_opus", "周限额｜Opus")?.let(items::add)
                parseClaudeBucket(json, "seven_day_sonnet", "周限额｜Sonnet")?.let(items::add)
                parseClaudeBucket(json, "seven_day_omelette", "周限额｜Claude Design")?.let(items::add)

                UsageInfo(
                    platform = Platform.CLAUDE,
                    items = items,
                    planLabel = orgInfo.planTier?.let { formatClaudePlan(it) },
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CLAUDE, "Failed to parse usage: ${e.message}")
            }
        }
    }

    private fun formatClaudePlan(tier: String): String = when (tier.lowercase()) {
        "free" -> "Free"
        "pro", "claude_pro" -> "Pro"
        "claude_max_5" -> "Max 5×"
        "claude_max_20" -> "Max 20×"
        "team", "claude_team" -> "Team"
        "enterprise" -> "Enterprise"
        else -> tier.replaceFirstChar { it.uppercase() }
    }

    private fun parseClaudeBucket(root: JsonObject, key: String, label: String): UsageItem? {
        val node = root.get(key)
        if (node == null || node.isJsonNull) return null
        val obj = node.asJsonObject
        val util = obj.get("utilization")
        if (util == null || util.isJsonNull) return null
        val percent = util.asFloat
        val resetAt = obj.get("resets_at")?.takeUnless { it.isJsonNull }?.asString
        return UsageItem(
            label = label,
            percent = percent,
            resetCountdown = resetAt?.let { formatResetTime(it) }
        )
    }

    // Step 1: exchange the long-lived session cookie for a short-lived access token.
    // ChatGPT backend-api endpoints require "Authorization: Bearer <accessToken>", not the
    // raw session cookie, which is a NextAuth.js server-side credential only.
    private suspend fun getOpenAIAccessToken(sessionCookie: String): String {
        val request = Request.Builder()
            .url("https://chatgpt.com/api/auth/session")
            .header("Cookie", "__Secure-next-auth.session-token=$sessionCookie")
            .header("User-Agent", USER_AGENT)
            .build()

        client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) {
                throw SessionExpiredException(Platform.CHATGPT)
            }
            val body = response.body?.string()
                ?: throw SessionExpiredException(Platform.CHATGPT)
            if (!response.isSuccessful) {
                throw SessionExpiredException(Platform.CHATGPT)
            }
            val json = try {
                gson.fromJson(body, JsonObject::class.java)
            } catch (e: Exception) {
                throw SessionExpiredException(Platform.CHATGPT)
            }
            return json.get("accessToken")?.asString
                ?: throw SessionExpiredException(Platform.CHATGPT)
        }
    }

    suspend fun getChatGptUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        val accessToken = getOpenAIAccessToken(cookie)
        val items = mutableListOf<UsageItem>()

        // Step 2: real usage windows. /backend-api/wham/usage returns rate_limit with
        // primary_window (5h) and secondary_window (weekly) used_percent, plus plan_type.
        val request = Request.Builder()
            .url("https://chatgpt.com/backend-api/wham/usage")
            .header("Authorization", "Bearer $accessToken")
            .header("Cookie", "__Secure-next-auth.session-token=$cookie")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", "https://chatgpt.com/")
            .header("Origin", "https://chatgpt.com")
            .tag(Platform::class.java, Platform.CHATGPT)
            .build()

        val planFromUsage = client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CHATGPT)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val rateLimit = json.optObject("rate_limit")
                parseChatGptWindow(rateLimit, "primary_window", "5 小时限额")?.let(items::add)
                parseChatGptWindow(rateLimit, "secondary_window", "周限额")?.let(items::add)
                val codeReview = json.optObject("code_review_rate_limit")
                parseChatGptWindow(codeReview, "primary_window", "Code Review｜5 小时")?.let(items::add)
                parseChatGptWindow(codeReview, "secondary_window", "Code Review｜周")?.let(items::add)
                json.get("plan_type")?.takeUnless { it.isJsonNull }?.asString
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CHATGPT, "Failed to parse usage: ${e.message}")
            }
        }

        // Plan type + renewal date come from accounts/check (best-effort; never fails the card).
        val planInfo = fetchChatGptPlanInfo(accessToken)
        val planType = planInfo?.planType ?: planFromUsage
        planInfo?.renewIso?.let { iso ->
            val text = formatRenewDate(iso)
            if (text.isNotBlank()) {
                items.add(UsageItem(
                    label = if (planInfo.hasActive) "续订时间" else "到期时间",
                    percent = -1f,
                    valueText = text
                ))
            }
        }

        if (items.isEmpty()) {
            throw ApiStructureChangedException(
                Platform.CHATGPT,
                "wham/usage returned no rate_limit windows or plan info"
            )
        }

        UsageInfo(
            platform = Platform.CHATGPT,
            items = items,
            planLabel = planType?.let { formatChatGptPlan(it) },
            updatedAt = System.currentTimeMillis()
        )
    }

    private data class ChatGptPlanInfo(
        val planType: String?,
        val hasActive: Boolean,
        val renewIso: String?
    )

    // Best-effort fetch of subscription plan + renewal date from accounts/check.
    // Returns null on any failure so it never blocks the usage card.
    private fun fetchChatGptPlanInfo(accessToken: String): ChatGptPlanInfo? {
        return try {
            val request = Request.Builder()
                .url("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27")
                .header("Authorization", "Bearer $accessToken")
                .header("User-Agent", USER_AGENT)
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = gson.fromJson(body, JsonObject::class.java)
                val accounts = json.getAsJsonObject("accounts") ?: return null
                val orderedId = json.getAsJsonArray("account_ordering")
                    ?.firstOrNull()?.takeUnless { it.isJsonNull }?.asString
                val accountNode = (orderedId?.let { accounts.getAsJsonObject(it) }
                    ?: accounts.getAsJsonObject("default")) ?: return null
                val account = accountNode.getAsJsonObject("account")
                val entitlement = accountNode.getAsJsonObject("entitlement")
                val planType = account?.get("plan_type")?.takeUnless { it.isJsonNull }?.asString
                    ?: entitlement?.get("subscription_plan")?.takeUnless { it.isJsonNull }?.asString
                val hasActive = entitlement?.get("has_active_subscription")?.asBoolean ?: false
                val renewIso = entitlement?.get("renews_at")?.takeUnless { it.isJsonNull }?.asString
                    ?: entitlement?.get("expires_at")?.takeUnless { it.isJsonNull }?.asString
                ChatGptPlanInfo(planType, hasActive, renewIso)
            }
        } catch (e: Exception) {
            null
        }
    }

    // Safe object accessor: getAsJsonObject does an unchecked cast that throws
    // ClassCastException when the member is present but JSON null (e.g. "code_review_rate_limit": null).
    private fun JsonObject.optObject(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun parseChatGptWindow(rateLimit: JsonObject?, key: String, label: String): UsageItem? {
        val node = rateLimit?.get(key)
        if (node == null || node.isJsonNull) return null
        val obj = node.asJsonObject
        val percentEl = obj.get("used_percent")
        if (percentEl == null || percentEl.isJsonNull) return null
        val percent = percentEl.asFloat.coerceIn(0f, 100f)
        val resetCountdown = obj.get("reset_after_seconds")?.takeUnless { it.isJsonNull }?.asLong
            ?.let { formatResetSeconds(it) }
            ?: obj.get("reset_at")?.takeUnless { it.isJsonNull }?.let { el ->
                runCatching { formatResetTimestamp(el.asLong) }.getOrNull()
                    ?: runCatching { formatResetTime(el.asString) }.getOrNull()
            }
        return UsageItem(label = label, percent = percent, resetCountdown = resetCountdown)
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
            val usage = json.optObject("planUsage") ?: json
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
                        boostKey = "interval_boost_permille"
                    ),
                    buildMinimaxUsageItem(
                        obj = general,
                        label = "周限额",
                        statusKey = "current_weekly_status",
                        remainingPercentKey = "current_weekly_remaining_percent",
                        remainsTimeKey = "weekly_remains_time",
                        boostKey = "weekly_boost_permille"
                    )
                )

                UsageInfo(
                    platform = Platform.MINIMAX,
                    items = items,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.MINIMAX, "Failed to parse usage: ${e.message}")
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
        boostKey: String
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
            boostPercent = boostPercent
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
                throw ApiStructureChangedException(Platform.AIHUBMIX, "Failed to parse usage: ${e.message}")
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
                throw ApiStructureChangedException(Platform.DEEPSEEK, "Failed to parse usage: ${e.message}")
            }
        }
    }

    private fun readOrThrow(response: Response, platform: Platform): String {
        if (response.code == 401 || response.code == 403) {
            throw SessionExpiredException(platform)
        }
        val body = response.body?.string()
            ?: throw ApiStructureChangedException(platform, "Empty response")
        if (!response.isSuccessful) {
            throw ApiStructureChangedException(
                platform,
                "HTTP ${response.code}: ${body.take(200)}"
            )
        }
        return body
    }

    private fun formatResetTime(isoTime: String): String {
        return try {
            val instant = java.time.Instant.parse(isoTime)
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(now, instant)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
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

    private fun formatResetSeconds(seconds: Long): String {
        if (seconds <= 0) return "即将重置"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 24 -> "${hours / 24}天后重置"
            hours > 0 -> "${hours}小时${if (minutes > 0) "${minutes}分钟" else ""}后重置"
            minutes > 0 -> "${minutes}分钟后重置"
            else -> "即将重置"
        }
    }

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

    private fun formatResetTimestamp(timestamp: Long): String {
        return try {
            val instant = java.time.Instant.ofEpochSecond(timestamp)
            val now = java.time.Instant.now()
            val duration = java.time.Duration.between(now, instant)
            val hours = duration.toHours()
            val minutes = duration.toMinutes() % 60
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
    }
}
