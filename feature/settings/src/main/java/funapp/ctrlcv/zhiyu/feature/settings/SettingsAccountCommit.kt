package funapp.ctrlcv.zhiyu.feature.settings

import kotlinx.coroutines.CancellationException

/** Compensates partial writes to credentials and account metadata without exposing their contents. */
internal fun commitSettingsAccount(commit: () -> Unit, restore: () -> Unit, quarantine: () -> Unit) {
    try {
        commit()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        try {
            restore()
        } catch (_: Exception) {
            runCatching(quarantine)
        }
        throw IllegalStateException("账号信息保存失败，请重试")
    }
}
