package funapp.ctrlcv.zhiyu.core.storage

import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tokens: Map<String, String>,
    val accountStrings: Map<String, String>,
    val accountSets: Map<String, List<String>>
)

@Singleton
class BackupManager @Inject constructor(
    private val tokenStore: SecureTokenStore,
    private val accountStore: AccountStore
) {
    private val gson = Gson()

    fun export(): String {
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            tokens = tokenStore.exportAll(),
            accountStrings = accountStore.exportAllStrings(),
            accountSets = accountStore.exportAllSets()
        )
        return gson.toJson(data)
    }

    fun import(json: String) {
        val data = gson.fromJson(json, BackupData::class.java)
            ?: throw IllegalArgumentException("无效的备份文件格式")
        if (data.version > 1) throw IllegalArgumentException("备份文件版本过新，请升级应用后再导入")
        tokenStore.importAll(data.tokens)
        accountStore.importAll(data.accountStrings, data.accountSets)
    }
}
