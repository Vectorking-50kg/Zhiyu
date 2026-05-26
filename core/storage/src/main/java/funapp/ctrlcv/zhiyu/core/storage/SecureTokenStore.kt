package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Account
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

    fun clearAll() {
        prefs.edit().clear().apply()
    }
}
