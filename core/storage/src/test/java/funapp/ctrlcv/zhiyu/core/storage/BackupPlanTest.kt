package funapp.ctrlcv.zhiyu.core.storage

import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class BackupPlanTest {
    @Test fun includesRegisteredMetadataAndTokenOnlyAccountsBeforeAnyWrite() {
        val data = parseBackup("""{
            "version":1,
            "tokens":{"claude_a_cookie":"fixture-value", "chatgpt_token_only_oauth":"{}"},
            "accountStrings":{"claude_a_name":"A", "deepseek_default_plan":"API"},
            "accountSets":{"accounts_claude":["a", "b"]}
        }""")
        assertEquals(setOf(
            Platform.CLAUDE to "a", Platform.CLAUDE to "b",
            Platform.CHATGPT to "token_only", Platform.DEEPSEEK to "default",
        ), backupAccountKeys(data))
        assertFalse(data.toString().contains("fixture-value"))
        assertEquals("PreparedBackup([REDACTED])", PreparedBackup(data, backupAccountKeys(data)).toString())
    }

    @Test fun longestKnownAccountIdOwnsItsExtrasAndCredentialFields() {
        val data = parseBackup("""{
            "version":1,
            "tokens":{"claude_a_extra_owner_cookie":"fixture", "claude_a_extra_owner_extra_workspace_id":"workspace"},
            "accountStrings":{}, "accountSets":{"accounts_claude":["a", "a_extra_owner"]}
        }""")
        assertEquals(setOf(Platform.CLAUDE to "a", Platform.CLAUDE to "a_extra_owner"), backupAccountKeys(data))
    }

    @Test fun malformedBackupCollectionsAndUnsupportedPlatformsFailWithSafeText() {
        val invalid = listOf(
            """{"version":1,"tokens":null,"accountStrings":{},"accountSets":{}}""",
            """{"version":1,"tokens":{},"accountStrings":{},"accountSets":{"accounts_claude":[1]}}""",
            """{"version":1,"tokens":{"unsupported_a_cookie":"private-fixture"},"accountStrings":{},"accountSets":{}}""",
            """{"version":2,"tokens":{},"accountStrings":{},"accountSets":{}}""",
            "private-fixture-not-json",
        )
        invalid.forEach { json ->
            try { parseBackup(json); fail("Expected validation failure") }
            catch (e: IllegalArgumentException) {
                assertEquals("无效或不支持的备份文件", e.message)
                assertNull(e.cause)
            }
        }
    }

    @Test fun partialImportRestoresBothStoresAndDoesNotExposeSourceException() {
        val steps = mutableListOf<String>()
        try {
            commitBackupImport(
                commit = { steps.add("write-tokens"); throw IOException("private-fixture") },
                restoreTokens = { steps.add("restore-tokens") },
                restoreAccounts = { steps.add("restore-accounts") },
                quarantine = { steps.add("quarantine") },
            )
            fail("Expected failure")
        } catch (e: IllegalStateException) {
            assertFalse(e.message.orEmpty().contains("private-fixture"))
        }
        assertEquals(listOf("write-tokens", "restore-tokens", "restore-accounts"), steps)
    }

    @Test fun bothRestorationsAreAttemptedAndCredentialsAreQuarantinedWhenRollbackFails() {
        val steps = mutableListOf<String>()
        try {
            commitBackupImport(
                commit = { throw IOException() },
                restoreTokens = { steps.add("restore-tokens"); throw IOException() },
                restoreAccounts = { steps.add("restore-accounts") },
                quarantine = { steps.add("quarantine") },
            )
            fail("Expected failure")
        } catch (_: IllegalStateException) { }
        assertEquals(listOf("restore-tokens", "restore-accounts", "quarantine"), steps)
    }

    @Test(expected = CancellationException::class)
    fun cancellationIsNotHiddenByImportFailureHandling() {
        commitBackupImport(
            commit = { throw CancellationException() },
            restoreTokens = { fail("No write occurred") },
            restoreAccounts = { fail("No write occurred") },
            quarantine = { fail("No write occurred") },
        )
    }
}
