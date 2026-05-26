package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

data class WidgetPlatformItem(
    val name: String,
    val mainPercent: Float,
    val resetInfo: String? = null
)

data class WidgetUsageData(
    val items: List<WidgetPlatformItem> = emptyList(),
    val lastUpdated: Long = 0L
)

object WidgetDataStore {
    private const val PREFS_NAME = "widget_data"
    private const val KEY_DATA = "usage_data"
    private val gson = Gson()

    fun read(context: Context): WidgetUsageData {
        val prefs = getPrefs(context)
        val json = prefs.getString(KEY_DATA, null) ?: return WidgetUsageData()
        return try {
            gson.fromJson(json, WidgetUsageData::class.java)
        } catch (e: Exception) {
            WidgetUsageData()
        }
    }

    fun write(context: Context, data: WidgetUsageData) {
        getPrefs(context).edit()
            .putString(KEY_DATA, gson.toJson(data))
            .apply()
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
