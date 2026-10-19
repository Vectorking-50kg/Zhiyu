package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import java.io.IOException

/** Per-instance display choices only. Credentials remain in the existing encrypted stores. */
class WidgetConfigurationStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("usage_widget_settings", Context.MODE_PRIVATE)

    fun read(widgetId: Int, kind: WidgetKind): WidgetConfiguration {
        val mode = prefs.getString("${widgetId}_mode", null)?.let { saved ->
            WidgetSelectionMode.entries.firstOrNull { it.name == saved }
        } ?: WidgetConfiguration.defaultFor(kind).mode
        return WidgetConfiguration(mode, prefs.getStringSet("${widgetId}_accounts", emptySet())?.toSet().orEmpty())
    }

    fun save(widgetId: Int, configuration: WidgetConfiguration) {
        require(widgetId >= 0)
        require(configuration.accountKeys.all { WidgetAccountReference.parse(it) != null })
        if (!prefs.edit().putString("${widgetId}_mode", configuration.mode.name)
                .putStringSet("${widgetId}_accounts", configuration.accountKeys.toSet()).commit()) {
            throw IOException("无法保存小组件设置")
        }
    }

    fun isRefreshing(widgetId: Int, now: Long = System.currentTimeMillis()): Boolean {
        val started = prefs.getLong("${widgetId}_refresh_started", 0)
        return started > 0 && now - started in 0..120_000L
    }

    /** Share the gate across callback/worker instances so rapid taps enqueue only one refresh. */
    fun beginRefreshing(widgetId: Int): Boolean = synchronized(refreshLock) {
        if (isRefreshing(widgetId)) return@synchronized false
        if (!prefs.edit().putLong("${widgetId}_refresh_started", System.currentTimeMillis()).commit()) {
            throw IOException("无法保存小组件刷新状态")
        }
        true
    }

    fun setRefreshing(widgetId: Int, refreshing: Boolean) {
        prefs.edit().apply {
            if (refreshing) putLong("${widgetId}_refresh_started", System.currentTimeMillis())
            else remove("${widgetId}_refresh_started")
        }.commit()
    }

    fun remove(widgetId: Int) {
        prefs.edit().remove("${widgetId}_mode").remove("${widgetId}_accounts")
            .remove("${widgetId}_refresh_started").apply()
    }

    private companion object { val refreshLock = Any() }
}
