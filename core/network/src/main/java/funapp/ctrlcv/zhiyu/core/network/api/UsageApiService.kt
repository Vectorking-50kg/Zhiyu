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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageApiService @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    suspend fun getClaudeOrganizationId(cookie: String): String = withContext(Dispatchers.IO) {
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
                orgs.firstOrNull()?.get("uuid")?.asString
                    ?: throw ApiStructureChangedException(Platform.CLAUDE, "No organization found")
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CLAUDE, "Failed to parse organizations: ${e.message}")
            }
        }
    }

    suspend fun getClaudeUsage(cookie: String, orgId: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://claude.ai/api/organizations/$orgId/usage")
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
                parseClaudeBucket(json, "seven_day", "周限额 · 所有模型")?.let(items::add)
                parseClaudeBucket(json, "seven_day_opus", "周限额 · Opus")?.let(items::add)
                parseClaudeBucket(json, "seven_day_sonnet", "周限额 · Sonnet")?.let(items::add)
                parseClaudeBucket(json, "seven_day_omelette", "周限额 · Omelette")?.let(items::add)

                UsageInfo(
                    platform = Platform.CLAUDE,
                    items = items,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CLAUDE, "Failed to parse usage: ${e.message}")
            }
        }
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

        val request = Request.Builder()
            .url("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CHATGPT)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CHATGPT)
            try {
                // accounts/check returns subscription/plan info (no rate_limits array).
                // Display plan type, subscription status and renewal date.
                val json = gson.fromJson(body, JsonObject::class.java)
                val accounts = json.getAsJsonObject("accounts")
                    ?: throw ApiStructureChangedException(Platform.CHATGPT, "Missing accounts field")
                val orderedId = json.getAsJsonArray("account_ordering")
                    ?.firstOrNull()?.takeUnless { it.isJsonNull }?.asString
                val accountNode = (orderedId?.let { accounts.getAsJsonObject(it) }
                    ?: accounts.getAsJsonObject("default"))
                    ?: throw ApiStructureChangedException(Platform.CHATGPT, "No account entry found")
                val account = accountNode.getAsJsonObject("account")
                val entitlement = accountNode.getAsJsonObject("entitlement")

                val planType = account?.get("plan_type")?.takeUnless { it.isJsonNull }?.asString
                    ?: entitlement?.get("subscription_plan")?.takeUnless { it.isJsonNull }?.asString
                    ?: "unknown"
                val hasActive = entitlement?.get("has_active_subscription")?.asBoolean ?: false
                val renewsAt = entitlement?.get("renews_at")?.takeUnless { it.isJsonNull }?.asString
                val expiresAt = entitlement?.get("expires_at")?.takeUnless { it.isJsonNull }?.asString

                val items = mutableListOf<UsageItem>()
                items.add(UsageItem(
                    label = "套餐类型",
                    percent = -1f,
                    valueText = formatChatGptPlan(planType)
                ))
                items.add(UsageItem(
                    label = "订阅状态",
                    percent = -1f,
                    valueText = if (hasActive) "有效" else "未订阅"
                ))
                (renewsAt ?: expiresAt)?.let { iso ->
                    val text = formatRenewDate(iso)
                    if (text.isNotBlank()) {
                        items.add(UsageItem(
                            label = if (hasActive) "续订时间" else "到期时间",
                            percent = -1f,
                            valueText = text
                        ))
                    }
                }

                UsageInfo(
                    platform = Platform.CHATGPT,
                    items = items,
                    resetInfo = null,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CHATGPT, "Failed to parse usage: ${e.message}")
            }
        }
    }

    suspend fun getCursorUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        // WebView may store the cookie value URL-encoded (%3A%3A instead of ::).
        // Decode first so we can correctly split the "userId::accessJwt" format.
        val decodedCookie = try {
            java.net.URLDecoder.decode(cookie, "UTF-8")
        } catch (e: Exception) {
            cookie
        }
        val accessToken = when {
            decodedCookie.startsWith("eyJ") -> decodedCookie           // already a bare JWT
            "::" in decodedCookie -> decodedCookie.substringAfter("::") // userId::jwt (decoded)
            "::" in cookie -> cookie.substringAfter("::")               // jwt not encoded, :: is raw
            else -> cookie                                               // unknown format, pass through
        }

        val request = Request.Builder()
            .url("https://api2.cursor.sh/auth/full_stripe_profile")
            .header("Cookie", "WorkosCursorSessionToken=$cookie")
            .header("Authorization", "Bearer $accessToken")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CURSOR)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CURSOR)
            try {
                // full_stripe_profile returns membership/subscription info, not usage credits.
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = mutableListOf<UsageItem>()

                val membership = json.get("membershipType")?.takeUnless { it.isJsonNull }?.asString
                    ?: json.get("individualMembershipType")?.takeUnless { it.isJsonNull }?.asString
                val status = json.get("subscriptionStatus")?.takeUnless { it.isJsonNull }?.asString
                val isOnBillableAuto = json.get("isOnBillableAuto")?.asBoolean ?: false

                if (membership != null) {
                    items.add(UsageItem(
                        label = "会员类型",
                        percent = -1f,
                        valueText = formatCursorMembership(membership)
                    ))
                }
                if (status != null) {
                    items.add(UsageItem(
                        label = "订阅状态",
                        percent = -1f,
                        valueText = if (status == "active") "有效" else status
                    ))
                }
                items.add(UsageItem(
                    label = "Auto 用量计费",
                    percent = -1f,
                    valueText = if (isOnBillableAuto) "已开启" else "未开启"
                ))

                if (items.isEmpty()) {
                    throw ApiStructureChangedException(
                        Platform.CURSOR,
                        "Response missing membership fields (endpoint structure may have changed)"
                    )
                }

                UsageInfo(
                    platform = Platform.CURSOR,
                    items = items,
                    resetInfo = null,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CURSOR, "Failed to parse usage: ${e.message}")
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
    // 返回 category_remains（按大类）和 model_remains（按模型）两个数组
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
                val items = mutableListOf<UsageItem>()
                val collapsibleItems = mutableListOf<UsageItem>()
                val seenLabels = mutableSetOf<String>()

                // 优先展示 category_remains（大类聚合，有 display_name）
                json.getAsJsonArray("category_remains")?.forEach { element ->
                    val obj = element.asJsonObject
                    val total = obj.get("current_interval_total_count")?.asInt ?: 0
                    if (total <= 0) return@forEach
                    val used = obj.get("current_interval_usage_count")?.asInt ?: 0
                    val percent = (used.toFloat() / total * 100f).coerceIn(0f, 100f)
                    val label = obj.get("display_name")?.asString
                        ?: obj.get("category")?.asString
                        ?: return@forEach
                    if (!seenLabels.add(label)) return@forEach
                    val startMs = obj.get("start_time")?.asLong
                    val endMs = obj.get("end_time")?.asLong
                    val isCollapsible = label in MINIMAX_COLLAPSIBLE_LABELS
                    val item = UsageItem(
                        label = label,
                        percent = percent,
                        resetCountdown = endMs?.let { formatMinimaxResetTime(it) },
                        usageCount = used,
                        totalCount = total,
                        timeRange = if (startMs != null && endMs != null) formatMinimaxTimeRange(startMs, endMs) else null,
                        collapsible = isCollapsible
                    )
                    if (isCollapsible) collapsibleItems.add(item) else items.add(item)
                }

                // 再追加 model_remains：先展示常用模型（MCP 等），再追加折叠的创作工具
                json.getAsJsonArray("model_remains")?.forEach { element ->
                    val obj = element.asJsonObject
                    val total = obj.get("current_interval_total_count")?.asInt ?: 0
                    if (total <= 0) return@forEach
                    val modelName = obj.get("model_name")?.asString ?: return@forEach
                    if (isMinimaxCategoryModel(modelName)) return@forEach
                    val used = obj.get("current_interval_usage_count")?.asInt ?: 0
                    val percent = (used.toFloat() / total * 100f).coerceIn(0f, 100f)
                    val label = getMinimaxModelDisplayName(modelName)
                    if (!seenLabels.add(label)) return@forEach
                    val startMs = obj.get("start_time")?.asLong
                    val endMs = obj.get("end_time")?.asLong
                    val isCollapsible = label in MINIMAX_COLLAPSIBLE_LABELS
                    val item = UsageItem(
                        label = label,
                        percent = percent,
                        resetCountdown = endMs?.let { formatMinimaxResetTime(it) },
                        usageCount = used,
                        totalCount = total,
                        timeRange = if (startMs != null && endMs != null) formatMinimaxTimeRange(startMs, endMs) else null,
                        collapsible = isCollapsible
                    )
                    if (isCollapsible) collapsibleItems.add(item) else items.add(item)
                }
                items.addAll(collapsibleItems)

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

    private fun isMinimaxCategoryModel(modelName: String): Boolean =
        modelName.startsWith("MiniMax-M") ||
        modelName == "speech-hd" ||
        modelName.startsWith("MiniMax-Hailuo")

    private fun isMinimaxCollapsibleModel(modelName: String): Boolean =
        getMinimaxModelDisplayName(modelName) in MINIMAX_COLLAPSIBLE_LABELS

    private fun getMinimaxModelDisplayName(modelName: String): String = when (modelName) {
        "music-2.5", "music-2.6" -> "音乐生成"
        "music-cover" -> "音乐翻唱"
        "lyrics_generation" -> "歌词生成"
        "image-01" -> "图片生成"
        "coding-plan-vlm" -> "图片理解 MCP"
        "coding-plan-search" -> "网络搜索 MCP"
        else -> modelName
    }

    private fun formatMinimaxResetTime(endTimeMs: Long): String {
        return try {
            val remaining = endTimeMs - System.currentTimeMillis()
            if (remaining <= 0) return "即将重置"
            val hours = remaining / 3_600_000L
            val minutes = (remaining % 3_600_000L) / 60_000L
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

    private fun formatMinimaxTimeRange(startMs: Long, endMs: Long): String {
        return try {
            val zone = java.time.ZoneId.of("Asia/Shanghai")
            val start = java.time.Instant.ofEpochMilli(startMs).atZone(zone)
            val end = java.time.Instant.ofEpochMilli(endMs).atZone(zone)
            val timeFmt = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
            if (start.toLocalDate() == end.toLocalDate()) {
                "${start.format(timeFmt)}-${end.format(timeFmt)}(UTC+8)"
            } else {
                "每日刷新"
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
        val MINIMAX_COLLAPSIBLE_LABELS = setOf("歌词生成", "音乐生成", "音乐翻唱")
    }
}
