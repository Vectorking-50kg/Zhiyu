package funapp.ctrlcv.zhiyu.widget

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.MainActivity
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.domain.model.*
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.feature.widget.WidgetAccountReference
import funapp.ctrlcv.zhiyu.feature.widget.WidgetIntents
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import android.content.Intent

class WidgetNavigationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private lateinit var store: AccountStore
    private lateinit var cache: UsageCache
    private lateinit var account: Account
    private lateinit var activity: MainActivity
    private lateinit var originalIntent: Intent

    @Before fun setUp() {
        activity = compose.activity
        originalIntent = Intent(activity.intent)
        store = AccountStore(compose.activity)
        cache = UsageCache(compose.activity, Gson(), store)
        account = Account("widget-link-${UUID.randomUUID()}/work", Platform.CHATGPT, "工作小组件测试", "Plus", monitoringEnabled = false)
        store.saveAccount(account, durable = true)
        cache.save(account.platform, account.id, UsageInfo(account.platform, listOf(UsageItem("周限额", 85f)),
            planLabel = "Plus", updatedAt = System.currentTimeMillis(), accountId = account.id))
    }

    @After fun tearDown() {
        // ActivityScenario matches lifecycle events by the original launch intent.
        compose.runOnUiThread { activity.intent = originalIntent }
        cache.clear(account.platform, account.id)
        store.removeAccount(account.platform, account.id, durable = true)
    }

    @Test fun exactAccountOpensOnRepeatedWidgetClicksInAnExistingActivity() {
        fun open() = activity.startActivity(WidgetIntents.open(activity,
            WidgetAccountReference(account.platform, account.id)))
        open()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Plus · 工作小组件测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Plus · 工作小组件测试").onLast().assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭面板").performClick()
        open()
        compose.waitUntil(10_000) { compose.onAllNodesWithText("Plus · 工作小组件测试").fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodesWithText("Plus · 工作小组件测试").onLast().assertIsDisplayed()
    }
}
