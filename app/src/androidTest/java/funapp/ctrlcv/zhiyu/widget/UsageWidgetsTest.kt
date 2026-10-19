package funapp.ctrlcv.zhiyu.widget

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ProgressBar
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import funapp.ctrlcv.zhiyu.MainActivity
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.domain.model.*
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.feature.widget.*
import funapp.ctrlcv.zhiyu.feature.widget.R as WidgetR
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch

@RunWith(AndroidJUnit4::class)
class UsageWidgetsTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val manager = AppWidgetManager.getInstance(context)
    private val settings = WidgetConfigurationStore(context)
    private lateinit var accounts: AccountStore
    private lateinit var cache: UsageCache
    private lateinit var fixtures: List<Account>
    private lateinit var host: AppWidgetHost
    private val widgetIds = mutableListOf<Int>()
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun setUp() {
        instrumentation.uiAutomation.adoptShellPermissionIdentity("android.permission.BIND_APPWIDGET")
        accounts = AccountStore(context)
        cache = UsageCache(context, Gson(), accounts)
        val prefix = "widget-test-${UUID.randomUUID()}"
        fixtures = listOf(
            Account("$prefix-personal", Platform.CHATGPT, "个人订阅", "Plus", monitoringEnabled = false),
            Account("$prefix-claude", Platform.CLAUDE, "工作订阅", "Pro", monitoringEnabled = false),
            Account("$prefix-go", Platform.OPENCODE_GO, "Go 订阅", "Go", monitoringEnabled = false),
            Account("$prefix-balance", Platform.DEEPSEEK, "API 账户", monitoringEnabled = false),
            Account("$prefix-work/second", Platform.CHATGPT, "另一个账户", "Plus", monitoringEnabled = false),
        )
        val now = System.currentTimeMillis()
        fixtures.forEachIndexed { index, account ->
            accounts.saveAccount(account, durable = true)
            val details = if (account.platform == Platform.DEEPSEEK) listOf(
                UsageItem("账户余额", -1f, valueText = "¥86.32"),
                UsageItem("赠送余额", -1f, valueText = "¥12.00"),
                UsageItem("充值余额", -1f, valueText = "¥74.32"),
            ) else listOf(
                UsageItem("5 小时限额", if (index == 4) 85f else 20f + index * 10f,
                    resetAt = now + 7_200_000, windowDurationSeconds = 18_000),
                UsageItem("周限额", 35f + index * 5f, resetAt = now + 3 * 86_400_000L, windowDurationSeconds = 604_800),
            ) + if (account.platform == Platform.OPENCODE_GO)
                listOf(UsageItem("月限额", 62f, resetAt = now + 16 * 86_400_000L)) else emptyList()
            cache.save(account.platform, account.id, UsageInfo(account.platform, details,
                planLabel = account.planType, updatedAt = now, accountId = account.id))
        }
        host = AppWidgetHost(context, 9107)
        host.startListening()
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After fun tearDown() {
        widgetIds.forEach { id -> settings.remove(id); host.deleteAppWidgetId(id) }
        host.stopListening()
        scenario?.close()
        fixtures.forEach { account ->
            cache.clear(account.platform, account.id)
            accounts.removeAccount(account.platform, account.id, durable = true)
        }
        instrumentation.uiAutomation.dropShellPermissionIdentity()
    }

    @Test fun allFourLayoutsRenderAccountScopedDataAndExposeCorrectGridSizes() {
        val specs = listOf(
            Triple(WidgetKind.OVERVIEW, 300 to 320, 4 to 4),
            Triple(WidgetKind.DETAIL, 340 to 200, 4 to 2),
            Triple(WidgetKind.COMPACT, 180 to 200, 2 to 2),
            Triple(WidgetKind.STRIP, 360 to 88, 4 to 1),
        )
        specs.forEach { (kind, size, cells) ->
            val chosen = when (kind) {
                WidgetKind.OVERVIEW -> fixtures.take(4)
                WidgetKind.DETAIL -> listOf(fixtures[2])
                WidgetKind.COMPACT -> listOf(fixtures[4])
                WidgetKind.STRIP -> listOf(fixtures[0], fixtures[1], fixtures[3])
            }
            val (id, view) = bind(kind, size.first, size.second, chosen)
            val info = manager.getAppWidgetInfo(id)
            assertEquals(cells.first, info.targetCellWidth)
            assertEquals(cells.second, info.targetCellHeight)
            assertTrue(info.previewLayout != 0)
            assertNotNull(info.configure)
            when (kind) {
                WidgetKind.OVERVIEW -> awaitText(view, "ChatGPT")
                WidgetKind.DETAIL -> { awaitText(view, "月限额"); awaitText(view, "62%") }
                WidgetKind.COMPACT -> { awaitText(view, "另一个账户"); awaitText(view, "85%") }
                WidgetKind.STRIP -> { awaitText(view, "ChatGPT"); awaitText(view, "¥86.32") }
            }
            assertFalse(texts(view).any { it.contains("Error") || it.contains("错误") })
            screenshot(kind.name.lowercase())
        }
    }

    @Test fun activeWidgetUpdatesAfterReconfigurationCacheChangeAndAccountRemoval() {
        val (id, view) = bind(WidgetKind.COMPACT, 180, 200, listOf(fixtures[0]))
        awaitText(view, "35%")
        settings.save(id, configuration(listOf(fixtures[4])))
        runBlocking { UsageWidgets.update(context, id) }
        awaitText(view, "85%")
        val work = fixtures[4]
        cache.save(work.platform, work.id, UsageInfo(work.platform, listOf(UsageItem("周限额", 31f)),
            accountId = work.id, updatedAt = System.currentTimeMillis()))
        runBlocking { UsageWidgets.updateAllNow(context) }
        awaitText(view, "31%")
        accounts.removeAccount(work.platform, work.id, durable = true)
        cache.clear(work.platform, work.id)
        runBlocking { UsageWidgets.updateAllNow(context) }
        awaitText(view, "账户已移除")
        assertFalse(texts(view).any { it == "35%" || it == "31%" })
    }

    @Test fun refreshWorkerFinishesAndClearsLoadingWithoutChangingPausedAccounts() {
        val (id, view) = bind(WidgetKind.DETAIL, 340, 200, listOf(fixtures[2]))
        awaitText(view, "62%")
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(workDataOf(WidgetRefreshWorker.WIDGET_ID to id)).build()
        val work = WorkManager.getInstance(context)
        work.enqueue(request).result.get(10, TimeUnit.SECONDS)
        waitFor { work.getWorkInfoById(request.id).get(5, TimeUnit.SECONDS)?.state?.isFinished == true }
        assertFalse(settings.isRefreshing(id))
        awaitText(view, "62%")
        assertEquals(62f, cache.get(fixtures[2].platform, fixtures[2].id)!!.items.last().percent)
    }

    @Test fun refreshIconRotatesWhileBusyAndReturnsToTheClickableIcon() {
        listOf(WidgetKind.OVERVIEW, WidgetKind.DETAIL, WidgetKind.COMPACT).forEach { kind ->
            val size = when (kind) {
                WidgetKind.OVERVIEW -> 300 to 320
                WidgetKind.DETAIL -> 340 to 200
                else -> 180 to 200
            }
            val (id, view) = bind(kind, size.first, size.second, listOf(fixtures[2]))
            awaitText(view, "62%")
            settings.beginRefreshing(id)
            assertFalse(WidgetConfigurationStore(context).beginRefreshing(id))
            runBlocking { UsageWidgets.update(context, id) }
            waitFor { onMain { view.findViewById<ProgressBar>(WidgetR.id.widget_refresh_spinner)?.isShown == true } }
            val level = onMain { view.findViewById<ProgressBar>(WidgetR.id.widget_refresh_spinner).indeterminateDrawable.level }
            waitFor { onMain { view.findViewById<ProgressBar>(WidgetR.id.widget_refresh_spinner).indeterminateDrawable.level != level } }
            screenshot("${kind.name.lowercase()}-refreshing")
            settings.setRefreshing(id, false)
            runBlocking { UsageWidgets.update(context, id) }
            waitFor { onMain { view.findViewById<ProgressBar>(WidgetR.id.widget_refresh_spinner) == null } }
        }
    }

    @Test fun tappingRefreshImmediatelyShowsBusyStateAndDeduplicatesRapidTaps() {
        val (id, view) = bind(WidgetKind.DETAIL, 340, 200, listOf(fixtures[2]))
        awaitText(view, "62%")
        onMain {
            fun find(node: View): View? {
                if (node.contentDescription?.toString() == "刷新账户") return node
                if (node is ViewGroup) for (index in 0 until node.childCount) find(node.getChildAt(index))?.let { return it }
                return null
            }
            var button = checkNotNull(find(view))
            while (!button.hasOnClickListeners() && button.parent is View) button = button.parent as View
            assertTrue(button.width >= (48 * context.resources.displayMetrics.density).toInt())
            assertTrue(button.height >= (48 * context.resources.displayMetrics.density).toInt())
            assertTrue(button.performClick())
            assertTrue(button.performClick())
        }
        waitFor { settings.isRefreshing(id) }
        val work = WorkManager.getInstance(context)
        waitFor { work.getWorkInfosForUniqueWork("widget_refresh_$id").get(5, TimeUnit.SECONDS).isNotEmpty() }
        waitFor { work.getWorkInfosForUniqueWork("widget_refresh_$id").get(5, TimeUnit.SECONDS).all { it.state.isFinished } }
        assertEquals(1, work.getWorkInfosForUniqueWork("widget_refresh_$id").get(5, TimeUnit.SECONDS).size)
        assertFalse(settings.isRefreshing(id))
        awaitText(view, "62%")
    }

    private fun <T> onMain(block: () -> T): T {
        var value: Any? = null
        instrumentation.runOnMainSync { value = block() }
        @Suppress("UNCHECKED_CAST")
        return value as T
    }

    private fun configuration(chosen: List<Account>) = WidgetConfiguration(WidgetSelectionMode.CUSTOM,
        chosen.map { WidgetAccountReference(it.platform, it.id).key }.toSet())

    private fun bind(kind: WidgetKind, width: Int, height: Int, chosen: List<Account>): Pair<Int, AppWidgetHostView> {
        val id = host.allocateAppWidgetId()
        widgetIds.add(id)
        val options = Bundle().apply {
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, width)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, height)
            putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, height)
            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(SizeF(width.toFloat(), height.toFloat())))
        }
        assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, UsageWidgets.receiver(kind)), options))
        settings.save(id, configuration(chosen))
        var view: AppWidgetHostView? = null
        scenario!!.onActivity { activity ->
            val root = FrameLayout(activity).apply {
                setBackgroundColor(if (activity.resources.configuration.uiMode and 0x30 == 0x20) Color.rgb(12, 18, 22) else Color.rgb(227, 234, 239))
                setPadding(0, 64, 0, 0)
            }
            view = host.createView(activity, id, manager.getAppWidgetInfo(id))
            // Options describe the widget's content area; this test host supplies no launcher inset.
            view!!.setPadding(0, 0, 0, 0)
            val density = activity.resources.displayMetrics.density
            root.addView(view, FrameLayout.LayoutParams((width * density).toInt(), (height * density).toInt(), Gravity.TOP or Gravity.CENTER_HORIZONTAL))
            activity.setContentView(root)
        }
        runBlocking { UsageWidgets.update(context, id) }
        return id to checkNotNull(view)
    }

    private fun texts(view: View): List<String> {
        var result: List<String> = emptyList()
        instrumentation.runOnMainSync {
            fun collect(node: View): List<String> = (if (node is TextView) listOf(node.text.toString()) else emptyList()) +
                (if (node is ViewGroup) (0 until node.childCount).flatMap { collect(node.getChildAt(it)) } else emptyList())
            result = collect(view)
        }
        return result
    }

    private fun awaitText(view: View, value: String) = waitFor { texts(view).any { it.contains(value) } }

    private fun waitFor(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 25_000
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return
            Thread.sleep(150)
        }
        assertTrue("Widget did not reach the expected rendered state", condition())
    }

    private fun screenshot(name: String) {
        instrumentation.waitForIdleSync()
        val frame = CountDownLatch(1)
        scenario!!.onActivity { activity ->
            activity.window.decorView.postOnAnimation { activity.window.decorView.postOnAnimation { frame.countDown() } }
        }
        assertTrue(frame.await(5, TimeUnit.SECONDS))
        val directory = File(context.getExternalFilesDir(null), "widget-qa").apply { mkdirs() }
        val mode = if (context.resources.configuration.uiMode and 0x30 == 0x20) "dark" else "light"
        instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
            File(directory, "$name-$mode.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
