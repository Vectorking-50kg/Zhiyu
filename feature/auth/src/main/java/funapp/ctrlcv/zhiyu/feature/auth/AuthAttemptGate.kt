package funapp.ctrlcv.zhiyu.feature.auth

/** Login generation shared with the short IO commit: canceled/older callbacks cannot commit. */
internal class AuthAttemptGate {
    private var generation = 0L
    @Volatile var busy: Boolean = false
        private set

    @Synchronized fun begin(): Long? {
        if (busy) return null
        busy = true
        return ++generation
    }

    @Synchronized fun isCurrent(attempt: Long): Boolean = busy && attempt == generation

    @Synchronized fun finish(attempt: Long) {
        if (isCurrent(attempt)) busy = false
    }

    @Synchronized fun cancel() {
        generation++
        busy = false
    }
}

internal fun requiresAccountSwitchConfirmation(existing: Boolean, previousId: String?, newId: String?): Boolean =
    existing && (previousId == null || newId == null || previousId != newId)
