package funapp.ctrlcv.zhiyu.feature.auth

/** Compensates partial writes across account metadata and encrypted credentials. */
internal fun commitLogin(commit: () -> Unit, restore: () -> Unit, quarantine: () -> Unit) {
    try {
        commit()
    } catch (failure: Exception) {
        var restored = true
        try {
            restore()
        } catch (_: Exception) {
            restored = false
            // If rollback also fails, revoke the local credential instead of pairing it with old metadata.
            runCatching(quarantine)
        }
        throw AuthSaveException(requiresLogin = !restored)
    }
}

internal class AuthSaveException(requiresLogin: Boolean) : Exception(
    if (requiresLogin) "登录状态保存失败，请重新登录" else "登录状态保存失败，已恢复原登录，请重试"
)
