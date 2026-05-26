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

                // 真实接口返回的是 {five_hour:{utilization, resets_at}, seven_day:{...},
                // seven_day_opus, seven_day_sonnet, seven_day_omelette, ...}
                // utilization 已经是 0-100 的百分比
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

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    }
}
