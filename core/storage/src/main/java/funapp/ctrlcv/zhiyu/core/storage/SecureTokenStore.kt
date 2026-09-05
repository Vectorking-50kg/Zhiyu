package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureTokenStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "ai_tokens",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    @Synchronized
    fun save(platform: Platform, accountId: String, cookie: String, durable: Boolean = false) {
        val editor = prefs.edit()
            .putString("${platform.key}_${accountId}_cookie", cookie)
            .remove("${platform.key}_${accountId}_oauth")
        if (durable) {
            if (!editor.commit()) throw IOException("无法保存登录状态，请重试")
        } else editor.apply()
    }

    fun get(platform: Platform, accountId: String): String? =
        prefs.getString("${platform.key}_${accountId}_cookie", null)

    @Synchronized
    fun clear(platform: Platform, accountId: String, durable: Boolean = false) {
        val editor = prefs.edit()
        prefs.all.keys.filter { credentialKeyBelongsTo(it, platform, accountId) }.forEach(editor::remove)
        if (durable) {
            if (!editor.commit()) throw IOException("无法清除登录状态，请重试")
        } else editor.apply()
    }

    @Synchronized
    fun snapshot(platform: Platform, accountId: String): TokenSnapshot {
        return TokenSnapshot(prefs.all.mapNotNull { (key, value) ->
            if (credentialKeyBelongsTo(key, platform, accountId) && value is String) key to value else null
        }.toMap())
    }

    /** Compensation for the two encrypted preference stores when account metadata cannot be saved. */
    @Synchronized
    fun restore(platform: Platform, accountId: String, snapshot: TokenSnapshot) {
        require(snapshot.values.keys.all { credentialKeyBelongsTo(it, platform, accountId) })
        val editor = prefs.edit()
        prefs.all.keys.filter { credentialKeyBelongsTo(it, platform, accountId) }.forEach(editor::remove)
        snapshot.values.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) throw IOException("无法恢复登录状态，请重新登录")
    }

    fun hasToken(platform: Platform, accountId: String): Boolean =
        get(platform, accountId) != null || getOAuth(platform, accountId) != null

    fun getOAuth(platform: Platform, accountId: String): OAuthCredential? {
        val json = prefs.getString("${platform.key}_${accountId}_oauth", null) ?: return null
        return runCatching { Gson().fromJson(json, OAuthCredential::class.java) }
            .getOrNull()?.takeIf { !it.accessToken.isNullOrBlank() }
    }

    /** Cookie and OAuth credentials are mutually exclusive for an account. */
    @Synchronized
    fun saveOAuth(platform: Platform, accountId: String, credential: OAuthCredential) {
        val saved = prefs.edit()
            .putString("${platform.key}_${accountId}_oauth", Gson().toJson(credential))
            .remove("${platform.key}_${accountId}_cookie")
            .commit()
        if (!saved) throw IOException("无法保存授权状态，请重试")
    }

    /** An in-flight refresh must never restore a credential cleared or replaced by a login. */
    @Synchronized
    fun replaceOAuthIfCurrent(
        platform: Platform,
        accountId: String,
        expected: OAuthCredential,
        replacement: OAuthCredential
    ): Boolean {
        if (getOAuth(platform, accountId) != expected) return false
        saveOAuth(platform, accountId, replacement)
        return true
    }

    /** 用于存储平台附加凭据，例如 MiniMax 的 GroupId */
    fun saveExtra(platform: Platform, accountId: String, key: String, value: String, durable: Boolean = false) {
        val editor = prefs.edit()
            .putString("${platform.key}_${accountId}_extra_$key", value)
        if (durable) {
            if (!editor.commit()) throw IOException("无法保存登录信息，请重试")
        } else editor.apply()
    }

    fun getExtra(platform: Platform, accountId: String, key: String): String? =
        prefs.getString("${platform.key}_${accountId}_extra_$key", null)

    fun clearExtra(platform: Platform, accountId: String, key: String, durable: Boolean = false) {
        val editor = prefs.edit().remove("${platform.key}_${accountId}_extra_$key")
        if (durable) {
            if (!editor.commit()) throw IOException("无法保存登录信息，请重试")
        } else editor.apply()
    }

    @Synchronized
    fun clearAll(durable: Boolean = false) {
        val editor = prefs.edit().clear()
        if (durable) {
            if (!editor.commit()) throw IOException("无法清除登录状态，请重试")
        } else editor.apply()
    }

    fun exportAll(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            if (v is String) k to v else null
        }.toMap()
    }

    @Synchronized
    fun importAll(data: Map<String, String>, durable: Boolean = false) {
        val removals = authCounterpartKeysToRemove(data.keys)
        data.filterKeys { it.endsWith("_oauth") }.values.forEach { json ->
            val valid = runCatching { Gson().fromJson(json, OAuthCredential::class.java) }
                .getOrNull()?.accessToken?.isNotBlank() == true
            if (!valid) throw IOException("备份中的授权状态无效，无法恢复")
        }
        val editor = prefs.edit()
        data.filterKeys { it !in removals }.forEach { (key, value) -> editor.putString(key, value) }
        removals.forEach(editor::remove)
        if (durable) {
            if (!editor.commit()) throw IOException("无法恢复备份登录状态，请重试")
        } else editor.apply()
    }

    /** Exact rollback of a locally exported snapshot; do not normalize its authentication mode. */
    @Synchronized
    fun restoreAll(data: Map<String, String>) {
        val editor = prefs.edit().clear()
        data.forEach { (key, value) -> editor.putString(key, value) }
        if (!editor.commit()) throw IOException("无法恢复原登录状态，请重新登录")
    }

    companion object {
        /** OpenCode Zen：登录时捕获的 workspace id，取余额时直接定位仪表盘页 */
        const val EXTRA_ZEN_WORKSPACE_ID = "workspace_id"
    }
}

/** Serialized only into encrypted preferences. Never include these fields in diagnostic output. */
data class OAuthCredential(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long?,
    val providerAccountId: String?,
    val displayName: String? = null
) {
    fun needsRefresh(now: Long): Boolean = expiresAt?.let { it <= now + 60_000 } ?: false
    override fun toString(): String = "OAuthCredential([REDACTED])"
}

class TokenSnapshot internal constructor(internal val values: Map<String, String>) {
    override fun toString(): String = "TokenSnapshot([REDACTED])"
}

/** Old cookie backups switch the account back to cookie mode. In a mixed backup, OAuth wins. */
internal fun authCounterpartKeysToRemove(keys: Set<String>): Set<String> = buildSet {
    keys.filter { it.endsWith("_oauth") }.forEach { add(it.removeSuffix("_oauth") + "_cookie") }
    keys.filter { it.endsWith("_cookie") }.forEach {
        val oauthKey = it.removeSuffix("_cookie") + "_oauth"
        if (oauthKey !in keys) add(oauthKey)
    }
}
