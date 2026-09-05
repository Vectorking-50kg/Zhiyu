package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    companion object {
        private val updates = MainScope()

        fun updateAll(context: Context) {
            val application = context.applicationContext
            updates.launch {
                try {
                    val manager = GlanceAppWidgetManager(application)
                    val widget = UsageWidget()
                    manager.getGlanceIds(UsageWidget::class.java).forEach { id ->
                        widget.update(application, id)
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    // A removed/unavailable widget must not crash the app after a successful fetch.
                }
            }
        }
    }
}
