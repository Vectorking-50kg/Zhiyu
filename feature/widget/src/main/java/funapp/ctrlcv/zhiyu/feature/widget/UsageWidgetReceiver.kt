package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.state.updateAppWidgetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class UsageWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = UsageWidget()

    companion object {
        fun updateAll(context: Context) = UsageWidgets.updateAll(context)
    }
}

class DetailWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DetailUsageWidget()
}

class CompactWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CompactUsageWidget()
}

class StripWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StripUsageWidget()
}

object UsageWidgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun widget(kind: WidgetKind): ZhiyuUsageWidget = when (kind) {
        WidgetKind.OVERVIEW -> UsageWidget()
        WidgetKind.DETAIL -> DetailUsageWidget()
        WidgetKind.COMPACT -> CompactUsageWidget()
        WidgetKind.STRIP -> StripUsageWidget()
    }

    fun receiver(kind: WidgetKind): Class<out GlanceAppWidgetReceiver> = when (kind) {
        WidgetKind.OVERVIEW -> UsageWidgetReceiver::class.java
        WidgetKind.DETAIL -> DetailWidgetReceiver::class.java
        WidgetKind.COMPACT -> CompactWidgetReceiver::class.java
        WidgetKind.STRIP -> StripWidgetReceiver::class.java
    }

    fun kindFor(context: Context, widgetId: Int): WidgetKind? {
        val info = AppWidgetManager.getInstance(context).getAppWidgetInfo(widgetId) ?: return null
        return WidgetKind.entries.firstOrNull { info.provider == ComponentName(context, receiver(it)) }
    }

    fun updateAll(context: Context) {
        scope.launch { updateAllNow(context.applicationContext) }
    }

    suspend fun updateAllNow(context: Context) {
        val manager = GlanceAppWidgetManager(context)
        WidgetKind.entries.forEach { kind ->
            val widget = widget(kind)
            val ids = try { manager.getGlanceIds(widget.javaClass) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { emptyList() }
            ids.forEach { id ->
                try {
                    updateAppWidgetState(context, id) { it[widgetRevisionKey] = System.nanoTime() }
                    widget.update(context, id)
                }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* One deleted/unavailable widget must not block the others. */ }
            }
        }
    }

    suspend fun update(context: Context, widgetId: Int) {
        val kind = kindFor(context, widgetId) ?: return
        val id = GlanceAppWidgetManager(context).getGlanceIdBy(widgetId)
        updateAppWidgetState(context, id) { it[widgetRevisionKey] = System.nanoTime() }
        widget(kind).update(context, id)
    }
}
