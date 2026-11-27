package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.JsonObject
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.domain.model.UsageRequestException
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale

internal object DevinUsageParser {
    private const val DAY_SECONDS = 86_400L

    fun parse(body: String, organizationId: String, nowMillis: Long): UsageInfo {
        val root = parseUsageObject(body, Platform.DEVIN)
        val allocated = root.booleanOrNull("has_quota_allocation")
        val items = buildList {
            if (allocated != false) {
                window(root, "daily", "日限额", DAY_SECONDS, nowMillis)?.let(::add)
                window(root, "weekly", "周限额", 7 * DAY_SECONDS, nowMillis)?.let(::add)
            }
            val balance = root.nonnegative("overage_balance") ?: root.nonnegative("overage_balance_cents")?.div(100.0)
            if (balance != null) {
                add(UsageItem("额外用量余额", -1f, valueText = String.format(Locale.US, "$%.2f", balance)))
            }
        }
        if (items.isEmpty() && allocated == false) {
            throw UsageRequestException(Platform.DEVIN, UsageFailure(UsageFailureKind.SUBSCRIPTION_REQUIRED, occurredAt = nowMillis))
        }
        if (items.isEmpty()) throw ApiStructureChangedException(Platform.DEVIN, "Missing Devin usage")
        return UsageInfo(
            platform = Platform.DEVIN,
            items = items,
            planLabel = planName(root.stringOrNull("plan_name") ?: root.stringOrNull("plan")),
            updatedAt = nowMillis,
            providerAccountId = organizationId,
        )
    }

    fun parseSubscriptionPlan(body: String): String? {
        val root = parseUsageObject(body, Platform.DEVIN)
        return planName(root.stringOrNull("slug") ?: root.objectOrNull("subscription")?.stringOrNull("slug"))
    }

    private fun planName(value: String?): String? = value?.trim()?.takeIf(String::isNotEmpty)?.let { plan ->
        when (plan.lowercase()) {
            "teams-v2" -> "Teams"
            "pro-trial" -> "Pro Trial"
            else -> plan.replaceFirstChar { it.uppercase() }
        }
    }

    private fun window(root: JsonObject, key: String, label: String, duration: Long, nowMillis: Long): UsageItem? {
        if (root.booleanOrNull("hide_${key}_quota") == true) return null
        val quota = root.objectOrNull("quota_usage")?.objectOrNull("${key}_quota")
            ?: root.objectOrNull(key)
        val usedPercent = root.nonnegative("${key}_percentage")?.let { if (it < 1) it * 100 else it }
            ?: quota?.let(::usedPercent)
            ?: return null
        val resetAt = root.resetMillis("${key}_reset_at")
            ?: quota?.resetMillis("reset_at")
            ?: quota?.resetMillis("next_reset_at")
        return usageWindow("devin.$key", label, usedPercent, resetAt, duration, nowMillis)
    }

    private fun usedPercent(quota: JsonObject): Double? {
        val reported = quota.nonnegative("used_percent") ?: quota.nonnegative("percentUsed")
        if (reported != null) return if (reported <= 1) reported * 100 else reported
        val remainingPercent = quota.nonnegative("remaining_percent")
        if (remainingPercent != null) return 100 - if (remainingPercent <= 1) remainingPercent * 100 else remainingPercent
        val limit = quota.nonnegative("limit")?.takeIf { it > 0 } ?: return null
        val remaining = quota.nonnegative("remaining")
        val used = quota.nonnegative("used") ?: remaining?.let { limit - it } ?: return null
        return used / limit * 100
    }
}

internal object CopilotUsageParser {
    private const val PREMIUM = "premium_interactions"
    private val quotas = listOf("chat" to "聊天额度", "completions" to "代码补全额度")

    fun parse(body: String, nowMillis: Long): UsageInfo {
        val root = parseUsageObject(body, Platform.GITHUB_COPILOT)
        val snapshots = root.objectOrNull("quota_snapshots")
        val premium = snapshots?.objectOrNull(PREMIUM)
        val usesCredits = root.booleanOrNull("token_based_billing")
            ?: premium?.booleanOrNull("token_based_billing")
            ?: false
        val resetAt = root.resetMillis("quota_reset_date_utc") ?: root.resetMillis("quota_reset_date")
        val legacyResetAt = root.resetMillis("limited_user_reset_date") ?: resetAt
        val plan = planName(root)
        val items = buildList {
            val premiumLabel = if (usesCredits) "AI 积分" else "高级请求"
            val premiumResetAt = premium?.resetMillis("quota_reset_at") ?: resetAt
            val premiumWindow = premium?.let { quotaWindow(it, PREMIUM, premiumLabel, premiumResetAt, nowMillis) }
            if (premiumWindow != null) {
                add(premiumWindow)
            } else if (usesCredits) {
                val spent = listOfNotNull(premium, snapshots?.objectOrNull("chat")).firstNotNullOfOrNull { snapshot ->
                    snapshot.nonnegative("credits_used")?.let { used ->
                        val spentResetAt = snapshot.resetMillis("quota_reset_at") ?: resetAt
                        UsageItem("已用 AI 积分", -1f, valueText = creditAmount(used), resetAt = spentResetAt).atTime(nowMillis)
                    }
                }
                if (spent != null) add(spent)
            }
            for ((key, label) in quotas) {
                val snapshot = snapshots?.objectOrNull(key)
                val window = snapshot?.let {
                    quotaWindow(it, key, label, it.resetMillis("quota_reset_at") ?: resetAt, nowMillis)
                } ?: legacyWindow(root, key, label, legacyResetAt, nowMillis)
                if (window != null) add(window)
            }
            if (usesCredits && premium != null) additionalUsage(premium)?.let(::add)
        }
        val availableItems = when {
            items.isNotEmpty() -> items
            root.stringOrNull("access_type_sku") == "no_access" ->
                throw UsageRequestException(Platform.GITHUB_COPILOT, UsageFailure(UsageFailureKind.SUBSCRIPTION_REQUIRED, occurredAt = nowMillis))
            (plan == "Business" || plan == "Enterprise") && hasSeatPlaceholder(snapshots) ->
                listOf(UsageItem("账户额度", -1f, valueText = "平台未提供"))
            else -> throw ApiStructureChangedException(Platform.GITHUB_COPILOT, "Missing Copilot usage")
        }
        return UsageInfo(
            platform = Platform.GITHUB_COPILOT,
            items = availableItems,
            planLabel = plan,
            updatedAt = nowMillis,
            providerAccountId = root.stringOrNull("login"),
        )
    }

    private fun additionalUsage(snapshot: JsonObject): UsageItem? {
        val spent = snapshot.nonnegative("overage_count") ?: return null
        val budget = snapshot.nonnegative("overage_entitlement")?.takeIf { it > 0 }
        if (spent == 0.0 && budget == null && snapshot.booleanOrNull("overage_permitted") != true) return null

        val spentText = "\$${creditsToUsd(spent)}"
        val valueText = if (budget != null) "$spentText / \$${creditsToUsd(budget).removeSuffix(".00")}" else spentText
        return UsageItem("额外用量", -1f, valueText = valueText, windowId = "github_copilot.additional_usage")
    }

    // Each AI credit is one US cent.
    private fun creditsToUsd(credits: Double): String =
        BigDecimal.valueOf(credits).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private fun hasSeatPlaceholder(snapshots: JsonObject?): Boolean =
        listOf(PREMIUM, "chat", "completions").any { key ->
            val quota = snapshots?.objectOrNull(key)
            quota?.nonnegative("entitlement") == 0.0 || quota?.booleanOrNull("unlimited") == true
        }

    private fun quotaWindow(snapshot: JsonObject, key: String, label: String, resetAt: Long?, nowMillis: Long): UsageItem? {
        val limit = snapshot.nonnegative("entitlement")
        // Seat placeholders can have unlimited=true and zero entitlement without exposing a quota.
        if (snapshot.booleanOrNull("unlimited") == true || limit == 0.0) return null
        val remaining = snapshot.nonnegative("quota_remaining") ?: snapshot.nonnegative("remaining")
        val reportedPercent = snapshot.finiteNumber("percent_remaining")
        val percentRemaining = reportedPercent ?: if (limit != null && remaining != null) remaining / limit * 100 else null
        return window(key, label, limit, remaining, percentRemaining ?: return null, resetAt, nowMillis)
    }

    private fun legacyWindow(root: JsonObject, key: String, label: String, resetAt: Long?, nowMillis: Long): UsageItem? {
        val limit = root.objectOrNull("monthly_quotas")?.nonnegative(key)?.takeIf { it > 0 } ?: return null
        val remaining = root.objectOrNull("limited_user_quotas")?.nonnegative(key) ?: return null
        return window(key, label, limit, remaining, remaining / limit * 100, resetAt, nowMillis)
    }

    private fun window(
        key: String, label: String, limit: Double?, remaining: Double?, percentRemaining: Double, resetAt: Long?, nowMillis: Long
    ): UsageItem {
        val item = usageWindow("github_copilot.$key", label, 100 - percentRemaining, resetAt, null, nowMillis)
        return if (limit != null && remaining != null) {
            item.copy(valueText = "剩余 ${creditAmount(remaining)} / ${creditAmount(limit)}")
        } else item
    }

    private fun planName(root: JsonObject): String? = when (root.stringOrNull("access_type_sku")) {
        "free_limited_copilot" -> "Free"
        "free_educational_quota" -> "Student"
        "no_access" -> null
        else -> when (val plan = root.stringOrNull("copilot_plan")) {
            "free" -> "Free"
            "individual_edu" -> "Student"
            "individual" -> "Pro"
            "individual_pro" -> "Pro+"
            "individual_max" -> "Max"
            null, "unknown" -> null
            else -> plan.replaceFirstChar { it.uppercase() }
        }
    }
}

private fun JsonObject.nonnegative(key: String): Double? = finiteNumber(key)?.takeIf { it >= 0 }

private fun creditAmount(value: Double): String = String.format(Locale.US, "%,.2f", value).trimEnd('0').trimEnd('.')

private fun JsonObject.resetMillis(key: String): Long? {
    val value = get(key)?.takeIf { it.isJsonPrimitive }?.asString?.trim() ?: return null
    value.toLongOrNull()?.let {
        return when {
            it <= 0 -> null
            it < 100_000_000_000L -> it * 1000
            else -> it
        }
    }
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(value).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}
