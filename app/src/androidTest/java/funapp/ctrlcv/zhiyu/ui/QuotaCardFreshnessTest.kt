package funapp.ctrlcv.zhiyu.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailure
import funapp.ctrlcv.zhiyu.core.domain.model.UsageFailureKind
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QuotaCardFreshnessTest {
    @get:Rule val compose = createComposeRule()
    private val now = 10_000_000L
    private val fresh = UsageInfo(Platform.CLAUDE, listOf(UsageItem("5 小时", 35f)), updatedAt = now)
    private val networkFailure = UsageFailure(UsageFailureKind.NETWORK, occurredAt = now)

    private fun row(usage: UsageInfo?, paused: Boolean = false) = MonitoredAccount(
        Account("account", Platform.CLAUDE, "测试账户", monitoringEnabled = !paused),
        usage, visible = true, pinned = false,
    )

    private fun show(usage: UsageInfo?, paused: Boolean = false) {
        compose.setContent {
            MaterialTheme { QuotaCard(row(usage, paused), now, onDetail = {}, onConfigure = {}) }
        }
    }

    @Test fun freshDataIsExplicitlyIdentified() {
        show(fresh)
        compose.onNodeWithText("最新数据 · 刚刚更新").assertIsDisplayed()
        compose.onNodeWithText("缓存数据", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重新连接").assertDoesNotExist()
    }

    @Test fun staleSnapshotWithoutFailureIsStillLabeledAsCache() {
        show(fresh.copy(stale = true, updatedAt = now - 300_000L))
        compose.onNodeWithText("缓存数据 · 5 分钟前更新").assertIsDisplayed()
        compose.onNodeWithText("数据尚未更新，正在显示缓存").assertIsDisplayed()
        compose.onNodeWithText("暂时无法同步", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重新连接").assertDoesNotExist()
    }

    @Test fun networkFailureKeepsTheSnapshotVisibleAndRecoveryClearsTheCacheLabel() {
        var usage by mutableStateOf(fresh.copy(stale = true, refreshFailure = networkFailure))
        compose.setContent {
            MaterialTheme { QuotaCard(row(usage), now, onDetail = {}, onConfigure = {}) }
        }
        compose.onAllNodesWithText("35%").assertCountEquals(2)
        compose.onNodeWithText("缓存数据 · 刚刚更新").assertIsDisplayed()
        compose.onNodeWithText("暂时无法同步，正在显示缓存").assertIsDisplayed()
        compose.onNodeWithText("重新连接").assertDoesNotExist()

        compose.runOnIdle { usage = fresh }
        compose.onNodeWithText("最新数据 · 刚刚更新").assertIsDisplayed()
        compose.onNodeWithText("缓存数据", substring = true).assertDoesNotExist()
        compose.onNodeWithText("暂时无法同步", substring = true).assertDoesNotExist()
    }

    @Test fun firstFailureDoesNotClaimThatCachedDataExists() {
        show(fresh.copy(items = emptyList(), updatedAt = 0, stale = true, refreshFailure = networkFailure))
        compose.onNodeWithText("等待重试同步").assertIsDisplayed()
        compose.onNodeWithText("暂时无法同步，尚无同步数据").assertIsDisplayed()
        compose.onNodeWithText("缓存", substring = true).assertDoesNotExist()
        compose.onNodeWithText("最新数据", substring = true).assertDoesNotExist()
    }

    @Test fun pausedAccountLabelsTheRetainedSnapshotAsCache() {
        show(fresh, paused = true)
        compose.onNodeWithText("缓存数据 · 刚刚更新").assertIsDisplayed()
        compose.onNodeWithText("监控已暂停，正在显示缓存").assertIsDisplayed()
        compose.onNodeWithText("暂时无法同步", substring = true).assertDoesNotExist()
        compose.onNodeWithText("重新连接").assertDoesNotExist()
    }

    @Test fun pausedAccountWithoutSnapshotDoesNotClaimThatCachedDataExists() {
        show(null, paused = true)
        compose.onNodeWithText("监控已暂停，尚无同步数据").assertIsDisplayed()
        compose.onNodeWithText("缓存", substring = true).assertDoesNotExist()
        compose.onNodeWithText("最新数据", substring = true).assertDoesNotExist()
    }

    @Test fun expiredLoginWithoutSnapshotOffersReconnectWithoutClaimingCachedData() {
        show(fresh.copy(items = emptyList(), updatedAt = 0, stale = true,
            refreshFailure = UsageFailure(UsageFailureKind.AUTH_REQUIRED, occurredAt = now)))
        compose.onNodeWithText("登录已过期，尚无同步数据").assertIsDisplayed()
        compose.onNodeWithText("重新连接").assertIsDisplayed()
        compose.onNodeWithText("缓存", substring = true).assertDoesNotExist()
    }
}
