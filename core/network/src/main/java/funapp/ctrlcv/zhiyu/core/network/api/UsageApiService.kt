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

    suspend fun getChatGptUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://chatgpt.com/backend-api/accounts/check/v4-2023-04-27")
            .header("Cookie", "__Secure-next-auth.session-token=$cookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CHATGPT)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CHATGPT)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = mutableListOf<UsageItem>()

                json.getAsJsonArray("rate_limits")?.forEach { element ->
                    val limit = element.asJsonObject
                    val id = limit.get("id")?.asString ?: return@forEach
                    val total = limit.get("limit")?.asFloat ?: 1f
                    val remaining = limit.get("remaining")?.asFloat ?: 0f
                    val used = total - remaining
                    val percent = if (total > 0) (used / total * 100f) else 0f
                    val resetTimestamp = limit.get("reset_timestamp")?.asLong

                    items.add(UsageItem(
                        label = id,
                        percent = percent,
                        resetCountdown = resetTimestamp?.let { formatResetTimestamp(it) }
                    ))
                }

                if (items.isEmpty()) {
                    throw ApiStructureChangedException(
                        Platform.CHATGPT,
                        "Response missing rate_limits field (endpoint may have changed)"
                    )
                }

                UsageInfo(
                    platform = Platform.CHATGPT,
                    items = items,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.CHATGPT, "Failed to parse usage: ${e.message}")
            }
        }
    }

    suspend fun getCursorUsage(cookie: String): UsageInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://api2.cursor.sh/auth/full_stripe_profile")
            .header("Cookie", "WorkosCursorSessionToken=$cookie")
            .header("User-Agent", USER_AGENT)
            .tag(Platform::class.java, Platform.CURSOR)
            .build()

        client.newCall(request).execute().use { response ->
            val body = readOrThrow(response, Platform.CURSOR)
            try {
                val json = gson.fromJson(body, JsonObject::class.java)
                val items = mutableListOf<UsageItem>()

                json.getAsJsonObject("memberCredits")?.let { credits ->
                    val used = credits.get("used")?.asFloat ?: 0f
                    val limit = credits.get("limit")?.asFloat ?: 1f
                    val percent = if (limit > 0) (used / limit * 100f) else 0f
                    items.add(UsageItem(
                        label = "套餐总量",
                        percent = percent,
                        resetCountdown = null
                    ))
                }

                json.getAsJsonObject("autoUsage")?.let { auto ->
                    val percent = auto.get("percent")?.asFloat ?: 0f
                    items.add(UsageItem(
                        label = "Auto",
                        percent = percent,
                        resetCountdown = null
                    ))
                }

                json.getAsJsonObject("apiUsage")?.let { api ->
                    val percent = api.get("percent")?.asFloat ?: 0f
                    items.add(UsageItem(
                        label = "API",
                        percent = percent,
                        resetCountdown = null
                    ))
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

    // ── MiniMax ──────────────────────────────────────────────────────────────
    // Docs: https://www.minimaxi.com/v1/token_plan/remains
    // 注意：此处使用 Token Plan 专属 API Key，非普通按量 API Key
    // GET https://www.minimaxi.com/v1/token_plan/remains
    // Authorization: Bearer {token_plan_api_key}
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

                val baseResp = json.getAsJsonObject("base_resp")
                val statusCode = baseResp?.get("status_code")?.asInt ?: 0
                if (statusCode != 0) {
                    val msg = baseResp?.get("status_msg")?.asString ?: "Unknown error"
                    throw ApiStructureChangedException(Platform.MINIMAX, "API error $statusCode: $msg")
                }

                // 优先从 token_plan / 根级别读取 remains 与 total
                val planNode = json.getAsJsonObject("token_plan")
                    ?: json.getAsJsonObject("token_plan_remains")
                    ?: json

                val remains = planNode.get("remains")?.takeUnless { it.isJsonNull }?.asLong
                    ?: planNode.get("remain_tokens")?.takeUnless { it.isJsonNull }?.asLong
                    ?: 0L
                val total = planNode.get("total")?.takeUnless { it.isJsonNull }?.asLong
                    ?: planNode.get("total_tokens")?.takeUnless { it.isJsonNull }?.asLong
                    ?: planNode.get("total_token")?.takeUnless { it.isJsonNull }?.asLong
                    ?: 0L

                val expireTime = planNode.get("expire_time")?.takeUnless { it.isJsonNull }?.asString
                    ?: planNode.get("expireTime")?.takeUnless { it.isJsonNull }?.asString
                    ?: json.get("expire_time")?.takeUnless { it.isJsonNull }?.asString

                val items = mutableListOf<UsageItem>()
                if (total > 0) {
                    val used = total - remains
                    val percent = (used.toFloat() / total * 100f).coerceIn(0f, 100f)
                    items.add(UsageItem(
                        label = "Token Plan 用量",
                        percent = percent,
                        resetCountdown = expireTime?.let { formatResetTime(it) },
                        valueText = "剩余 ${formatTokenCount(remains)}"
                    ))
                } else {
                    // 只拿到 remains，无 total，以信息行展示
                    items.add(UsageItem(
                        label = "Token Plan 剩余",
                        percent = -1f,
                        resetCountdown = expireTime?.let { formatResetTime(it) },
                        valueText = "${formatTokenCount(remains)} tokens"
                    ))
                }

                UsageInfo(
                    platform = Platform.MINIMAX,
                    items = items,
                    resetInfo = if (total > 0) "共 ${formatTokenCount(total)} tokens" else null,
                    updatedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                if (e is ApiStructureChangedException || e is SessionExpiredException) throw e
                throw ApiStructureChangedException(Platform.MINIMAX, "Failed to parse usage: ${e.message}")
            }
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
                val total = quota + usedQuota
                val percent = if (total > 0) (usedQuota.toFloat() / total * 100f).coerceIn(0f, 100f) else 0f

                // 1 USD = 500000 quota 单位
                val remainingUsd = quota / 500000.0
                val balanceText = "剩余 \$${String.format("%.4f", remainingUsd)}"

                val items = mutableListOf<UsageItem>()
                items.add(UsageItem(
                    label = "额度用量",
                    percent = percent,
                    valueText = balanceText
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
    }
}
