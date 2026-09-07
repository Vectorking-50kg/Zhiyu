package funapp.ctrlcv.zhiyu.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.SystemClock
import android.view.ContextThemeWrapper
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import funapp.ctrlcv.zhiyu.core.data.R as DataR
import funapp.ctrlcv.zhiyu.core.data.notification.notificationBalanceRow
import funapp.ctrlcv.zhiyu.core.domain.model.UsageMetric
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
class NotificationProgressTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test
    fun nativeRemoteViewsDrawBothLayersAtTenDpInLightAndDarkModes() {
        instrumentation.runOnMainSync {
            for (night in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
                val configuration = Configuration(context.resources.configuration).apply {
                    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
                }
                val themed = ContextThemeWrapper(context.createConfigurationContext(configuration),
                    if (night == Configuration.UI_MODE_NIGHT_YES) android.R.style.Theme_Material_NoActionBar
                    else android.R.style.Theme_Material_Light_NoActionBar)
                for ((percent, usageColor, timeColor) in listOf(
                    Triple(62, DataR.color.notification_usage_good, DataR.color.notification_time_good),
                    Triple(78, DataR.color.notification_usage_warning, DataR.color.notification_time_warning),
                    Triple(93, DataR.color.notification_usage_danger, DataR.color.notification_time_danger),
                )) {
                    val elapsed = percent + 5f
                    val remote = notificationBalanceRow(themed, "ChatGPT", UsageMetric("$percent%", percent, "周限额", elapsed), "周限额")
                    val view = remote.apply(themed, FrameLayout(themed)) as ViewGroup
                    val bar = view.findViewById<ProgressBar>(DataR.id.platform_bar)
                    assertEquals(themed.getColor(usageColor), view.findViewById<TextView>(DataR.id.platform_value).currentTextColor)
                    render(view, themed).useBitmap { bitmap ->
                        assertEquals((10 * themed.resources.displayMetrics.density).roundToInt(), bar.height)
                        assertEquals(percent * 100, bar.progress)
                        assertEquals((elapsed * 100).roundToInt(), bar.secondaryProgress)
                        val layers = bar.progressDrawable as LayerDrawable
                        assertEquals(layers.findDrawableByLayerId(android.R.id.progress).bounds.height(),
                            layers.findDrawableByLayerId(android.R.id.secondaryProgress).bounds.height())
                        val bounds = Rect().also { bar.getDrawingRect(it); view.offsetDescendantRectToMyCoords(bar, it) }
                        fun pixel(fraction: Float) = bitmap.getPixel(bounds.left + (bounds.width() * fraction).toInt(), bounds.centerY())
                        assertEquals("usage color at $percent% / night=$night", themed.getColor(usageColor), pixel(.25f))
                        assertEquals("elapsed color at $percent% / night=$night", themed.getColor(timeColor), pixel((percent + 2.5f) / 100))
                        assertEquals(themed.getColor(DataR.color.notification_progress_track), pixel(.995f))
                    }
                }
            }
        }
    }

    @Test
    fun elapsedBehindUsageAndMissingTimeNeverChangeTheUsageValue() {
        instrumentation.runOnMainSync {
            val remote = notificationBalanceRow(context, "ChatGPT", UsageMetric("62%", 62, "周限额", 25f), "周限额")
            val view = remote.apply(context, FrameLayout(context)) as ViewGroup
            val bar = view.findViewById<ProgressBar>(DataR.id.platform_bar)
            render(view, context).recycle()
            assertEquals(6200, bar.progress)
            assertEquals(2500, bar.secondaryProgress)
            assertTrue(bar.contentDescription.contains("时间已过 25%"))
            notificationBalanceRow(context, "ChatGPT", UsageMetric("62%", 62, "周限额"), "周限额").reapply(context, view)
            assertEquals(0, bar.secondaryProgress)
            assertEquals(6200, bar.progress)
            assertFalse(bar.contentDescription.contains("时间"))
            for (metric in listOf(UsageMetric("¥12.30", null, "账户余额"), UsageMetric("无限制", null), null)) {
                notificationBalanceRow(context, "DeepSeek", metric, metric?.label).reapply(context, view)
                assertEquals(View.GONE, bar.visibility)
            }
        }
    }

    @Test
    fun decoratedNotificationCanBePostedWithNativeRows() {
        val manager = context.getSystemService(NotificationManager::class.java)
        val granted = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val channel = "notification_progress_render_test"
        val keepPreview = InstrumentationRegistry.getArguments().getString("keepNotificationPreview") == "true"
        try {
            if (!granted) instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.POST_NOTIFICATIONS)
            manager.createNotificationChannel(NotificationChannel(channel, "进度条样式验证", NotificationManager.IMPORTANCE_LOW))
            val content = RemoteViews(context.packageName, DataR.layout.notification_balance_expanded)
            content.setTextViewText(DataR.id.notif_header, "进度条样式验证")
            listOf(Triple("ChatGPT", 62, 85f), Triple("Claude", 78, 92f), Triple("MiniMax", 93, 100f)).forEach { (name, used, elapsed) ->
                content.addView(DataR.id.notif_rows, notificationBalanceRow(context, name, UsageMetric("$used%", used, "周限额", elapsed), "周限额"))
            }
            manager.notify(9901, NotificationCompat.Builder(context, channel)
                .setSmallIcon(DataR.drawable.ic_balance_notification).setContentTitle("进度条样式验证")
                .setContentText("深色用量 · 浅色时间").setStyle(NotificationCompat.DecoratedCustomViewStyle())
                .setCustomBigContentView(content).setOnlyAlertOnce(true).build())
            val deadline = SystemClock.uptimeMillis() + 5_000
            while (manager.activeNotifications.none { it.id == 9901 } && SystemClock.uptimeMillis() < deadline) {
                SystemClock.sleep(50)
            }
            assertTrue(manager.activeNotifications.any { it.id == 9901 })
            assertNotNull(manager.activeNotifications.first { it.id == 9901 }.notification.bigContentView)
        } finally {
            // Only the external screenshot harness opts into retaining this labelled test preview.
            if (!keepPreview) {
                manager.cancel(9901)
                manager.deleteNotificationChannel(channel)
            }
            // Revoking a runtime permission can kill the instrumentation UID. The device harness
            // restores the original grant after the test process has finished.
        }
    }

    private fun render(view: View, context: Context): Bitmap {
        val width = (320 * context.resources.displayMetrics.density).roundToInt()
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        view.layout(0, 0, width, view.measuredHeight)
        return Bitmap.createBitmap(width, view.measuredHeight, Bitmap.Config.ARGB_8888).also { view.draw(Canvas(it)) }
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) = try { block(this) } finally { recycle() }
}
