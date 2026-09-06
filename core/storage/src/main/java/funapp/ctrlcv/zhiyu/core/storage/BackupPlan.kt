package funapp.ctrlcv.zhiyu.core.storage

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import funapp.ctrlcv.zhiyu.core.domain.model.Platform

/** Validate all collection shapes before any durable credential mutation. */
internal fun parseBackup(json: String): BackupData = try {
    val root = JsonParser.parseString(json).asJsonObject
    val version = root.get("version")?.asString?.toIntOrNull()
    require(version == 1)
    val data = BackupData(
        version = version,
        tokens = stringMap(root.getAsJsonObject("tokens")),
        accountStrings = stringMap(root.getAsJsonObject("accountStrings")),
        accountSets = requireNotNull(root.getAsJsonObject("accountSets")).entrySet().associate { (key, value) ->
            require(value.isJsonArray)
            key to value.asJsonArray.map(::stringValue)
        },
    )
    backupAccountKeys(data) // Reject unsupported credential/metadata keys before writing either store.
    data
} catch (_: Exception) {
    throw IllegalArgumentException("无效或不支持的备份文件")
}

private fun stringMap(value: JsonObject?): Map<String, String> =
    requireNotNull(value).entrySet().associate { (key, element) -> key to stringValue(element) }

private fun stringValue(value: JsonElement): String {
    require(value.isJsonPrimitive && value.asJsonPrimitive.isString)
    return value.asString
}

internal fun backupAccountKeys(data: BackupData, strict: Boolean = true): Set<Pair<Platform, String>> = buildSet {
    fun accept(key: Pair<Platform, String>?) {
        if (key != null && key.second.isNotBlank()) add(key)
        else require(!strict) { "不支持的备份账号字段" }
    }
    data.accountSets.forEach { (key, ids) ->
        val platform = Platform.entries.firstOrNull { key == "accounts_${it.key}" }
        if (platform == null) require(!strict) { "不支持的备份平台" }
        else ids.forEach { accept(platform to it) }
    }
    data.accountStrings.keys.forEach { key ->
        accept(accountKey(key, listOf("_provider_id", "_name", "_plan", "_monitoring", "_visible", "_alerts", "_pinned")))
        if (strict && listOf("_monitoring", "_visible", "_alerts", "_pinned").any(key::endsWith)) {
            require(data.accountStrings[key] in setOf("true", "false")) { "无效的账户监控偏好" }
        }
    }
    data.tokens.keys.forEach { key ->
        // Prefer registry/metadata identities, including IDs which themselves contain underscores.
        val known = filter { (platform, id) -> key.startsWith("${platform.key}_${id}_") }
            .maxByOrNull { it.second.length }
        accept(known ?: accountKey(key, listOf("_cookie", "_oauth")) ?: extraAccountKey(key))
    }
}

private fun accountKey(key: String, suffixes: List<String>): Pair<Platform, String>? {
    val platform = Platform.entries.firstOrNull { key.startsWith("${it.key}_") } ?: return null
    val suffix = suffixes.firstOrNull { key.endsWith(it) } ?: return null
    val id = key.removePrefix("${platform.key}_").removeSuffix(suffix)
    return platform to id
}

private fun extraAccountKey(key: String): Pair<Platform, String>? {
    val platform = Platform.entries.firstOrNull { key.startsWith("${it.key}_") } ?: return null
    val rest = key.removePrefix("${platform.key}_")
    if (!rest.contains("_extra_")) return null
    return platform to rest.substringBefore("_extra_")
}
