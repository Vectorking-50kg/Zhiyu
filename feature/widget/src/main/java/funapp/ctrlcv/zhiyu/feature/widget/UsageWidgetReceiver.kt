package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    companion object {
        fun updateAll(context: Context) {
            MainScope().launch {
                val manager = GlanceAppWidgetManager(context)
                val widget = UsageWidget()
                manager.getGlanceIds(UsageWidget::class.java).forEach { id ->
                    widget.update(context, id)
                }
            }
        }
    }
}
