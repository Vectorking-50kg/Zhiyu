package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
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

    fun save(platform: Platform, accountId: String, cookie: String) {
        prefs.edit()
            .putString("${platform.key}_${accountId}_cookie", cookie)
            .apply()
    }

    fun get(platform: Platform, accountId: String): String? =
        prefs.getString("${platform.key}_${accountId}_cookie", null)

    fun clear(platform: Platform, accountId: String) {
        prefs.edit().remove("${platform.key}_${accountId}_cookie").apply()
    }

    fun hasToken(platform: Platform, accountId: String): Boolean =
        get(platform, accountId) != null

    /** 用于存储平台附加凭据，例如 MiniMax 的 GroupId */
    fun saveExtra(platform: Platform, accountId: String, key: String, value: String) {
        prefs.edit()
            .putString("${platform.key}_${accountId}_extra_$key", value)
            .apply()
    }

    fun getExtra(platform: Platform, accountId: String, key: String): String? =
        prefs.getString("${platform.key}_${accountId}_extra_$key", null)

    fun clearExtra(platform: Platform, accountId: String, key: String) {
        prefs.edit().remove("${platform.key}_${accountId}_extra_$key").apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun exportAll(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            if (v is String) k to v else null
        }.toMap()
    }

    fun importAll(data: Map<String, String>) {
        val editor = prefs.edit()
        data.forEach { (key, value) -> editor.putString(key, value) }
        editor.apply()
    }

    companion object {
        /** OpenCode Zen：登录时捕获的 workspace id，取余额时直接定位仪表盘页 */
        const val EXTRA_ZEN_WORKSPACE_ID = "workspace_id"
    }
}
