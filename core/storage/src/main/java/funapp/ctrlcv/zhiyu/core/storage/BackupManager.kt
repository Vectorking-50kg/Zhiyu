package funapp.ctrlcv.zhiyu.core.storage

import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import java.util.concurrent.CancellationException
import javax.inject.Inject
import javax.inject.Singleton

data class BackupData(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val tokens: Map<String, String>,
    val accountStrings: Map<String, String>,
    val accountSets: Map<String, List<String>>
) {
    override fun toString(): String = "BackupData([REDACTED])"
}

/** Parsed once before acquiring refresh gates; sensitive contents stay inside the storage module. */
class PreparedBackup internal constructor(
    internal val data: BackupData,
    val affectedAccounts: Set<Pair<Platform, String>>,
) {
    override fun toString(): String = "PreparedBackup([REDACTED])"
}

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
        import(prepareImport(json))
    }

    fun prepareImport(json: String): PreparedBackup {
        val data = parseBackup(json)
        return PreparedBackup(data, backupAccountKeys(data) + currentAccountKeys())
    }

    /** Caller must hold repository.updateAccounts(prepared.affectedAccounts) until this returns. */
    fun import(prepared: PreparedBackup) {
        // A new login between inspection and acquiring the gates must not be overwritten unlocked.
        require(currentAccountKeys().all { it in prepared.affectedAccounts }) { "账号状态已变化，请重新导入" }
        val previous = BackupData(
            tokens = tokenStore.exportAll(),
            accountStrings = accountStore.exportAllStrings(),
            accountSets = accountStore.exportAllSets(),
        )
        commitBackupImport(
            commit = {
                tokenStore.importAll(prepared.data.tokens, durable = true)
                accountStore.importAll(prepared.data.accountStrings, prepared.data.accountSets, durable = true)
            },
            restoreTokens = { tokenStore.restoreAll(previous.tokens) },
            restoreAccounts = { accountStore.restoreAll(previous.accountStrings, previous.accountSets) },
            quarantine = { tokenStore.clearAll(durable = true) },
        )
    }

    private fun currentAccountKeys(): Set<Pair<Platform, String>> =
        accountStore.getAllAccounts().map { it.platform to it.id }.toSet() + backupAccountKeys(
            BackupData(
                tokens = tokenStore.exportAll(),
                accountStrings = accountStore.exportAllStrings(),
                accountSets = accountStore.exportAllSets(),
            ),
            strict = false,
        )
}

/** Both restorations run even if one store fails; no exception text from a backup is surfaced. */
internal fun commitBackupImport(
    commit: () -> Unit,
    restoreTokens: () -> Unit,
    restoreAccounts: () -> Unit,
    quarantine: () -> Unit,
) {
    try {
        commit()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        val tokensRestored = runCatching(restoreTokens).isSuccess
        val accountsRestored = runCatching(restoreAccounts).isSuccess
        if (!tokensRestored || !accountsRestored) runCatching(quarantine)
        throw IllegalStateException("备份导入失败，请重试；如登录状态异常请重新登录")
    }
}
