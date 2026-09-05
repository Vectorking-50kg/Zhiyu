package funapp.ctrlcv.zhiyu.feature.settings

import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SettingsAccountCommitTest {
    @Test fun metadataFailureRestoresOldCredentialAndAccountTogether() {
        var credential = "old-fixture"
        var metadata = "old-account"
        var quarantined = false
        try {
            commitSettingsAccount(
                commit = { credential = "new-fixture"; throw IOException("sensitive fixture details") },
                restore = { credential = "old-fixture"; metadata = "old-account" },
                quarantine = { quarantined = true },
            )
            fail("Expected storage failure")
        } catch (e: IllegalStateException) {
            assertFalse(e.message.orEmpty().contains("sensitive"))
        }
        assertEquals("old-fixture", credential)
        assertEquals("old-account", metadata)
        assertFalse(quarantined)
    }

    @Test fun rollbackFailureQuarantinesCredentialInsteadOfKeepingMixedIdentity() {
        var credential: String? = "old-fixture"
        try {
            commitSettingsAccount(
                commit = { credential = "new-fixture"; throw IOException() },
                restore = { throw IOException() },
                quarantine = { credential = null },
            )
            fail("Expected storage failure")
        } catch (_: IllegalStateException) { }
        assertNull(credential)
    }

    @Test fun successfulRemovalDoesNotRollBack() {
        var credential: String? = "old-fixture"
        var account: String? = "old-account"
        commitSettingsAccount(
            commit = { credential = null; account = null },
            restore = { fail("Successful deletion must not roll back") },
            quarantine = { fail("Successful deletion must not quarantine") },
        )
        assertNull(credential)
        assertNull(account)
    }

    @Test(expected = CancellationException::class)
    fun cancelledPrewriteCheckRemainsCancellation() {
        commitSettingsAccount(
            commit = { throw CancellationException() },
            restore = { fail("No write occurred") },
            quarantine = { fail("No write occurred") },
        )
    }
}
