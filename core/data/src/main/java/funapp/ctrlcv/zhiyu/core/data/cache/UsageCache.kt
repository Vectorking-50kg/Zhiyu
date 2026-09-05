package funapp.ctrlcv.zhiyu.core.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.atTime
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import javax.inject.Inject
import javax.inject.Singleton

/** Last successful snapshot and latest failed attempt have separate lifetimes. */
@Singleton
class UsageCache internal constructor(
    private val prefs: SharedPreferences,
    private val gson: Gson,
    private val accounts: () -> List<Account>,
    private val now: () -> Long = System::currentTimeMillis,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        gson: Gson,
        accountStore: AccountStore,
    ) : this(
        context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE),
        gson,
        accountStore::getAllAccounts,
    )

    /** Compatibility for demo seeding and callers which have exactly one account. */
    @Synchronized
    fun save(platform: Platform, usageInfo: UsageInfo) {
        val ids = accountIds(platform)
        val accountId = usageInfo.accountId ?: ids.singleOrNull()
        if (accountId != null) {
            save(platform, accountId, usageInfo)
        } else if (ids.isEmpty()) {
            // The demo seeds its snapshots before registering its accounts.
            prefs.edit().putString(platform.key, gson.toJson(usageInfo)).apply()
        }
    }

    @Synchronized
    fun save(platform: Platform, accountId: String, usageInfo: UsageInfo) {
        require(usageInfo.platform == platform)
        val snapshot = usageInfo.copy(accountId = accountId, stale = false, refreshFailure = null)
        prefs.edit()
            .putString(snapshotKey(platform, accountId), gson.toJson(snapshot))
            .remove(failureKey(platform, accountId))
            .remove(platform.key)
            .apply()
    }

    @Synchronized
    fun saveFailure(platform: Platform, accountId: String, failure: UsageFailure) {
        migrateLegacy(platform, accountId)
        prefs.edit().putString(failureKey(platform, accountId), gson.toJson(failure)).apply()
    }

    @Synchronized
    fun getFailure(platform: Platform, accountId: String): UsageFailure? =
        decodeFailure(failureKey(platform, accountId))

    @Synchronized
    fun get(platform: Platform, accountId: String): UsageInfo? {
        migrateLegacy(platform, accountId)
        val snapshot = decodeSnapshot(snapshotKey(platform, accountId), platform)
            ?.takeIf { it.accountId == accountId }
        val failure = decodeFailure(failureKey(platform, accountId))
        val base = snapshot ?: failure?.let {
            UsageInfo(platform, emptyList(), updatedAt = 0L, accountId = accountId)
        } ?: return null
        val time = now()
        return base.copy(
            stale = base.stale || failure != null || base.updatedAt <= 0L || time < base.updatedAt,
            refreshFailure = failure,
        ).atTime(time)
    }

    /** A platform-only lookup must never choose an arbitrary account. */
    @Synchronized
    fun get(platform: Platform): UsageInfo? =
        accountIds(platform).singleOrNull()?.let { get(platform, it) }

    @Synchronized
    fun getAll(): List<UsageInfo> = accounts()
        .distinctBy { it.platform to it.id }
        .mapNotNull { get(it.platform, it.id) }

    @Synchronized
    fun clear(platform: Platform, accountId: String) {
        prefs.edit()
            .remove(snapshotKey(platform, accountId))
            .remove(failureKey(platform, accountId))
            .remove(platform.key)
            .apply()
    }

    @Synchronized
    fun clear(platform: Platform) {
        val editor = prefs.edit().remove(platform.key)
        prefs.all.keys.filter { it.startsWith("v2:${platform.key}:") }.forEach { editor.remove(it) }
        editor.apply()
    }

    private fun accountIds(platform: Platform): List<String> = accounts()
        .filter { it.platform == platform }.map { it.id }.distinct()

    private fun migrateLegacy(platform: Platform, accountId: String) {
        if (!prefs.contains(platform.key)) return
        val ids = accountIds(platform)
        if (ids.size > 1) {
            // Discard ambiguity now; removing an account later must not reassign its old data.
            prefs.edit().remove(platform.key).apply()
            return
        }
        if (ids.singleOrNull() != accountId) return
        val legacy = decodeSnapshot(platform.key, platform)
        val editor = prefs.edit().remove(platform.key)
        if (legacy != null && (legacy.accountId == null || legacy.accountId == accountId) &&
            !prefs.contains(snapshotKey(platform, accountId))) {
            editor.putString(
                snapshotKey(platform, accountId),
                gson.toJson(legacy.copy(accountId = accountId, refreshFailure = null)),
            )
            legacy.refreshFailure?.let {
                if (!prefs.contains(failureKey(platform, accountId))) {
                    editor.putString(failureKey(platform, accountId), gson.toJson(it))
                }
            }
        }
        editor.apply()
    }

    private fun decodeSnapshot(key: String, platform: Platform): UsageInfo? = runCatching {
        val json = prefs.getString(key, null) ?: return null
        // Gson bypasses Kotlin constructors, including non-null properties inside collections.
        // Check the complete nested shape before projecting timestamps or exposing it to UI.
        val shape = gson.fromJson(json, JsonObject::class.java) ?: return null
        if (!validSnapshotShape(shape)) return null
        gson.fromJson(shape, UsageInfo::class.java)?.takeIf { it.platform == platform }
    }.getOrNull()

    private fun validSnapshotShape(shape: JsonObject): Boolean {
        val items = shape.get("items")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        if (!items.all { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
            val label = item.get("label")
            val percent = item.get("percent").finiteNumberOrNull()
            label?.isJsonPrimitive == true && label.asJsonPrimitive.isString &&
                percent != null && (percent == -1.0 || percent in 0.0..100.0) &&
                item.validOptionalNumber("elapsedPercent") { it in 0.0..100.0 } &&
                item.validOptionalLong("resetAt") && item.validOptionalLong("windowDurationSeconds") { it > 0 } &&
                item.validOptionalLong("boostPercent") { it in 0..Int.MAX_VALUE.toLong() }
        }) return false
        val cardsElement = shape.get("resetCredits")
        if (cardsElement == null || cardsElement.isJsonNull) return true
        val cards = cardsElement.takeIf { it.isJsonObject }?.asJsonObject ?: return false
        val count = cards.get("availableCount").longOrNull() ?: return false
        if (count !in 0..Int.MAX_VALUE.toLong()) return false
        val rows = cards.get("credits")?.takeIf { it.isJsonArray }?.asJsonArray ?: return false
        return rows.all { element ->
            val row = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@all false
            row.get("expiresAt").longOrNull() != null && row.validOptionalLong("grantedAt")
        }
    }

    private fun JsonElement?.finiteNumberOrNull(): Double? = this
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asDouble?.takeIf { it.isFinite() }

    private fun JsonElement?.longOrNull(): Long? = this
        ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }
        ?.asString?.toLongOrNull()

    private fun JsonObject.validOptionalNumber(key: String, valid: (Double) -> Boolean): Boolean {
        val value = get(key)?.takeUnless { it.isJsonNull } ?: return true
        return value.finiteNumberOrNull()?.let(valid) ?: false
    }

    private fun JsonObject.validOptionalLong(key: String, valid: (Long) -> Boolean = { true }): Boolean {
        val value = get(key)?.takeUnless { it.isJsonNull } ?: return true
        return value.longOrNull()?.let(valid) ?: false
    }

    private fun decodeFailure(key: String): UsageFailure? = runCatching {
        val json = prefs.getString(key, null) ?: return null
        gson.fromJson(json, UsageFailure::class.java)?.takeIf { it.kind in UsageFailureKind.entries }
    }.getOrNull()

    private fun snapshotKey(platform: Platform, accountId: String) = "${keyPrefix(platform, accountId)}:snapshot"
    private fun failureKey(platform: Platform, accountId: String) = "${keyPrefix(platform, accountId)}:failure"
    private fun keyPrefix(platform: Platform, accountId: String) = "v2:${platform.key}:${accountId.length}:$accountId"
}
