package funapp.ctrlcv.zhiyu.core.network.api

import com.google.gson.JsonObject
import funapp.ctrlcv.zhiyu.core.domain.model.ApiStructureChangedException
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.atTime

/** Cloud Code response shapes verified against TokenTracker's MIT implementation. */
internal object AntigravityUsageParser {
    private val windows = listOf(
        Triple("gemini-5h", "Gemini · 5 小时", 18_000L),
        Triple("gemini-weekly", "Gemini · 每周", 604_800L),
        Triple("3p-5h", "Claude and GPT · 5 小时", 18_000L),
        Triple("3p-weekly", "Claude and GPT · 每周", 604_800L),
    )

    fun summary(body: String, now: Long): UsageInfo {
        val root = parseUsageObject(body, Platform.ANTIGRAVITY)
        val data = root.objectOrNull("response") ?: root
        val groups = data.getAsJsonArrayOrNull("groups") ?: malformed()
        val buckets = groups.flatMap { group ->
            group.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonArrayOrNull("buckets")?.toList().orEmpty()
        }.mapNotNull { it.takeIf { node -> node.isJsonObject }?.asJsonObject }
        val items = windows.mapNotNull { (key, label, seconds) ->
            val bucket = buckets.firstOrNull { it.stringOrNull("bucketId") == key } ?: return@mapNotNull null
            val remaining = bucket.fraction() ?: return@mapNotNull null
            usageWindow("antigravity.$key", label, (1 - remaining) * 100,
                bucket.get("resetTime").instantMillisOrNull(), seconds, now).atTime(now)
        }
        if (items.isEmpty()) malformed()
        return UsageInfo(Platform.ANTIGRAVITY, items, updatedAt = now)
    }

    fun models(body: String, now: Long): UsageInfo {
        val models = parseUsageObject(body, Platform.ANTIGRAVITY).objectOrNull("models") ?: malformed()
        val items = models.entrySet().sortedBy { it.key }.mapNotNull { (id, value) ->
            val model = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val quota = model.objectOrNull("quotaInfo") ?: return@mapNotNull null
            val remaining = quota.fraction() ?: return@mapNotNull null
            val label = model.stringOrNull("displayName") ?: id
            // The models endpoint does not identify a window duration. Never guess 5h/weekly
            // from remaining quota or collapse different models into an invented shared window.
            usageWindow("antigravity.model.$id", label, (1 - remaining) * 100,
                quota.get("resetTime").instantMillisOrNull(), null, now).atTime(now)
        }
        if (items.isEmpty()) malformed()
        return UsageInfo(Platform.ANTIGRAVITY, items, updatedAt = now)
    }

    fun plan(body: String): String? {
        val root = parseUsageObject(body, Platform.ANTIGRAVITY)
        return listOf("paidTier", "currentTier").firstNotNullOfOrNull { key ->
            root.objectOrNull(key)?.let { it.stringOrNull("name") ?: it.stringOrNull("id") }
        }
    }

    private fun JsonObject.fraction(): Double? = finiteNumber("remainingFraction")?.takeIf { it in 0.0..1.0 }
    private fun JsonObject.getAsJsonArrayOrNull(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun malformed(): Nothing = throw ApiStructureChangedException(Platform.ANTIGRAVITY, "Missing Antigravity quota")
}
