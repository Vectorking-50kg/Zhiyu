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
class AccountStore @Inject constructor(
    @ApplicationContext private val ctx: Context
) {
    private val masterKey = MasterKey.Builder(ctx)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        ctx,
        "ai_accounts",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveAccount(account: Account) {
        prefs.edit()
            .putString("${account.platform.key}_${account.id}_name", account.displayName)
            .putString("${account.platform.key}_${account.id}_plan", account.planType)
            .putStringSet("accounts_${account.platform.key}",
                getAccountIds(account.platform) + account.id)
            .apply()
    }

    fun getAccounts(platform: Platform): List<Account> {
        return getAccountIds(platform).map { id ->
            Account(
                id = id,
                platform = platform,
                displayName = prefs.getString("${platform.key}_${id}_name", platform.displayName) ?: platform.displayName,
                planType = prefs.getString("${platform.key}_${id}_plan", "") ?: ""
            )
        }
    }

    fun getAllAccounts(): List<Account> {
        return Platform.entries.flatMap { getAccounts(it) }
    }

    fun removeAccount(platform: Platform, accountId: String) {
        val ids = getAccountIds(platform) - accountId
        prefs.edit()
            .putStringSet("accounts_${platform.key}", ids)
            .remove("${platform.key}_${accountId}_name")
            .remove("${platform.key}_${accountId}_plan")
            .apply()
    }

    private fun getAccountIds(platform: Platform): Set<String> {
        return prefs.getStringSet("accounts_${platform.key}", emptySet()) ?: emptySet()
    }

    fun exportAllStrings(): Map<String, String> {
        return prefs.all.mapNotNull { (k, v) ->
            if (v is String) k to v else null
        }.toMap()
    }

    @Suppress("UNCHECKED_CAST")
    fun exportAllSets(): Map<String, List<String>> {
        return prefs.all.mapNotNull { (k, v) ->
            if (v is Set<*>) k to (v as Set<String>).toList() else null
        }.toMap()
    }

    fun importAll(strings: Map<String, String>, sets: Map<String, List<String>>) {
        val editor = prefs.edit()
        strings.forEach { (key, value) -> editor.putString(key, value) }
        sets.forEach { (key, value) -> editor.putStringSet(key, value.toSet()) }
        editor.apply()
    }
}
