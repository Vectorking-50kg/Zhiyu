package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.ResetCredit
import funapp.ctrlcv.zhiyu.core.domain.model.ResetCredits
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import java.math.BigDecimal
import java.time.Instant

/** Pure provider parsing: no network, storage, Android APIs, or implicit clock reads. */
internal object ClaudeUsageParser {
    fun parse(body: String, planTier: String?, nowMillis: Long): UsageInfo {
        val root = parseUsageObject(body, Platform.CLAUDE)
        val items = listOf(
            Triple("five_hour", "5 小时限额", FIVE_HOUR_SECONDS),
            Triple("seven_day", "周限额｜所有模型", WEEKLY_SECONDS),
            Triple("seven_day_opus", "周限额｜Opus", WEEKLY_SECONDS),
            Triple("seven_day_sonnet", "周限额｜Sonnet", WEEKLY_SECONDS),
            Triple("seven_day_omelette", "周限额｜Claude Design", WEEKLY_SECONDS),
        ).mapNotNull { (key, label, duration) ->
            val bucket = root.objectOrNull(key) ?: return@mapNotNull null
            val percent = bucket.finiteNumber("utilization") ?: return@mapNotNull null
            usageWindow(
                id = "claude.$key", label = label, percent = percent,
                resetAt = bucket.get("resets_at").instantMillisOrNull(),
                duration = duration, nowMillis = nowMillis,
            )
        }.toMutableList()

        // The endpoint does not establish a currency/unit for extra credits. Display only its
        // explicit percentage rather than guessing that the values are dollars or cents.
        root.objectOrNull("extra_usage")?.takeIf { it.booleanOrNull("is_enabled") == true }
            ?.finiteNumber("utilization")?.let { percent ->
                items += UsageItem(label = "额外用量", percent = percent.toFloat().coerceIn(0f, 100f), windowId = "claude.extra_usage")
            }
        if (items.isEmpty()) throw ApiStructureChangedException(Platform.CLAUDE, "Claude response has no valid usage windows")
        return UsageInfo(
            platform = Platform.CLAUDE, items = items,
            planLabel = planTier?.let(::formatClaudePlan), updatedAt = nowMillis,
        )
    }

    private fun formatClaudePlan(tier: String): String = when (tier.lowercase()) {
        "free" -> "Free"
        "pro", "claude_pro" -> "Pro"
        "claude_max_5" -> "Max 5×"
        "claude_max_20" -> "Max 20×"
        "team", "claude_team" -> "Team"
        "enterprise", "claude_enterprise" -> "Enterprise"
        else -> tier.replaceFirstChar { it.uppercase() }
    }
}

internal object CodexUsageParser {
    fun parse(body: String, nowMillis: Long): UsageInfo {
        val root = parseUsageObject(body, Platform.CHATGPT)
        val items = mutableListOf<UsageItem>()
        addWindows(items, root.objectOrNull("rate_limit"), "codex.rate_limit", "", nowMillis)
        addWindows(items, root.objectOrNull("code_review_rate_limit"), "codex.code_review", "Code Review", nowMillis)
        root.get("additional_rate_limits")?.takeIf { it.isJsonArray }?.asJsonArray?.forEachIndexed { index, element ->
            val additional = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEachIndexed
            val name = additional.stringOrNull("limit_name") ?: additional.stringOrNull("metered_feature") ?: "附加限额 ${index + 1}"
            addWindows(items, additional.objectOrNull("rate_limit"), "codex.additional.$name", name, nowMillis, useDefaultDuration = false)
        }
        root.objectOrNull("credits")?.let { credits ->
            val unlimited = credits.booleanOrNull("unlimited") == true
            val balance = credits.finiteNumber("balance")?.takeIf { it >= 0 }
            if (unlimited || balance != null) {
                items += UsageItem(
                    label = "额外额度", percent = -1f,
                    valueText = if (unlimited) "无限制" else BigDecimal.valueOf(balance!!).stripTrailingZeros().toPlainString(),
                    windowId = "codex.credits", unlimited = unlimited,
                )
            }
        }
        // Metadata such as a plan name alone must not make a failed/empty quota lookup successful.
        if (items.isEmpty()) throw ApiStructureChangedException(Platform.CHATGPT, "ChatGPT response has no valid usage windows or credits")
        return UsageInfo(
            platform = Platform.CHATGPT, items = items,
            planLabel = root.stringOrNull("plan_type")?.let(::formatCodexPlan),
            updatedAt = nowMillis, resetCredits = parseResetCredits(root.objectOrNull("rate_limit_reset_credits"), nowMillis),
        )
    }

    private fun addWindows(
        target: MutableList<UsageItem>, container: JsonObject?, idPrefix: String,
        prefix: String, nowMillis: Long, useDefaultDuration: Boolean = true,
    ) {
        listOf("primary_window", "secondary_window").forEachIndexed { index, key ->
            val window = container?.objectOrNull(key) ?: return@forEachIndexed
            val percent = window.finiteNumber("used_percent") ?: return@forEachIndexed
            val durationValue = window.get("limit_window_seconds")
            val duration = if (durationValue != null && !durationValue.isJsonNull) {
                window.positiveLong("limit_window_seconds")
            } else if (useDefaultDuration) {
                if (index == 0) FIVE_HOUR_SECONDS else WEEKLY_SECONDS
            } else null
            val resetAt = window.get("reset_at").instantMillisOrNull()
                ?: window.longOrNull("reset_after_seconds")?.let { remaining ->
                    runCatching { Math.addExact(nowMillis, Math.multiplyExact(remaining, 1000L)) }.getOrNull()
                }
            val period = when (duration) {
                FIVE_HOUR_SECONDS -> "5 小时"
                WEEKLY_SECONDS -> "周"
                null -> if (index == 0) "主要" else "次要"
                else -> when {
                    duration % 86400L == 0L -> "${duration / 86400L} 天"
                    duration % 3600L == 0L -> "${duration / 3600L} 小时"
                    duration % 60L == 0L -> "${duration / 60L} 分钟"
                    else -> "$duration 秒"
                }
            }
            val label = if (prefix.isBlank()) "${period}限额" else "$prefix｜$period"
            target += usageWindow("$idPrefix.$key", label, percent, resetAt, duration, nowMillis)
        }
    }

    fun parseResetCredits(node: JsonObject?, nowMillis: Long): ResetCredits? {
        if (node == null) return null
        val available = node.longOrNull("available_count")?.takeIf { it in 0..Int.MAX_VALUE.toLong() }?.toInt()
        val cards = if (available == 0) emptyList() else node.get("credits")?.takeIf { it.isJsonArray }?.asJsonArray
            ?.mapNotNull { element ->
                val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                if (row.stringOrNull("status") != "available") return@mapNotNull null
                val type = row.stringOrNull("reset_type")
                if (type != null && type != "codex_rate_limits") return@mapNotNull null
                val expires = row.get("expires_at").instantMillisOrNull() ?: return@mapNotNull null
                if (expires <= nowMillis) return@mapNotNull null
                ResetCredit(expiresAt = expires, grantedAt = row.get("granted_at").instantMillisOrNull())
            }?.sortedBy { it.expiresAt }?.take(50) ?: emptyList()
        if (available == null && cards.isEmpty()) return null
        return ResetCredits(availableCount = available ?: cards.size, credits = cards)
    }
}

internal fun formatCodexPlan(plan: String): String = when (plan.lowercase()) {
    "free", "chatgptfreeplan" -> "Free"
    "plus", "chatgptplusplan" -> "Plus"
    "pro", "chatgptproplan" -> "Pro"
    "team", "chatgptteamplan" -> "Team"
    "go", "chatgptgoplan" -> "Go"
    "business" -> "Business"
    "enterprise" -> "Enterprise"
    "edu" -> "Edu"
    else -> plan.replaceFirstChar { it.uppercase() }
}

private fun usageWindow(id: String, label: String, percent: Double, resetAt: Long?, duration: Long?, nowMillis: Long): UsageItem {
    val remaining = resetAt?.let { (it.toDouble() - nowMillis.toDouble()) / 1000.0 }
    return UsageItem(
        windowId = id, label = label, percent = percent.toFloat().coerceIn(0f, 100f),
        resetAt = resetAt, windowDurationSeconds = duration,
        resetCountdown = remaining?.let { formatRemainingSeconds(it.toLong()) },
        elapsedPercent = if (remaining != null && duration != null && duration > 0)
            ((1.0 - remaining / duration) * 100.0).toFloat().coerceIn(0f, 100f) else null,
    )
}

internal fun formatRemainingSeconds(seconds: Long): String = when {
    seconds <= 0L -> "即将重置"
    seconds >= 86400L -> "${seconds / 86400L}天后重置"
    seconds >= 3600L -> "${seconds / 3600L}小时${if (seconds % 3600L >= 60L) "${seconds % 3600L / 60L}分钟" else ""}后重置"
    seconds >= 60L -> "${seconds / 60L}分钟后重置"
    else -> "即将重置"
}

internal fun parseUsageObject(body: String, platform: Platform): JsonObject = try {
    JsonParser.parseString(body).takeIf { it.isJsonObject }?.asJsonObject
        ?: throw ApiStructureChangedException(platform, "Expected a usage object")
} catch (_: Exception) {
    // Parser exception messages may quote the response, including credentials/account details.
    throw ApiStructureChangedException(platform, "Invalid usage response structure")
}

internal fun JsonObject.objectOrNull(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
internal fun JsonObject.stringOrNull(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString?.takeIf { it.isNotBlank() }
internal fun JsonObject.finiteNumber(key: String): Double? = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toDoubleOrNull()?.takeIf { it.isFinite() }
internal fun JsonObject.longOrNull(key: String): Long? = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
internal fun JsonObject.positiveLong(key: String): Long? = longOrNull(key)?.takeIf { it > 0 }
internal fun JsonObject.booleanOrNull(key: String): Boolean? = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.toBooleanStrictOrNull()
internal fun JsonElement?.instantMillisOrNull(): Long? {
    if (this == null || !isJsonPrimitive) return null
    return runCatching {
        asString.toLongOrNull()?.let { Instant.ofEpochSecond(it).toEpochMilli() }
            ?: Instant.parse(asString).toEpochMilli()
    }.getOrNull()
}

private const val FIVE_HOUR_SECONDS = 18000L
private const val WEEKLY_SECONDS = 604800L
