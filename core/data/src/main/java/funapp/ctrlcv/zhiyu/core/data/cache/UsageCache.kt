package funapp.ctrlcv.zhiyu.core.data.cache

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageCache @Inject constructor(
    @ApplicationContext context: Context,
    private val gson: Gson
) {
    private val prefs: SharedPreferences = context.getSharedPreferences("usage_cache", Context.MODE_PRIVATE)

    fun save(platform: Platform, usageInfo: UsageInfo) {
        prefs.edit()
            .putString(platform.key, gson.toJson(usageInfo))
            .apply()
    }

    fun get(platform: Platform): UsageInfo? {
        val json = prefs.getString(platform.key, null) ?: return null
        return try {
            gson.fromJson(json, UsageInfo::class.java)
        } catch (e: Exception) {
            null
        }
    }

    fun getAll(): List<UsageInfo> {
        return Platform.entries.mapNotNull { get(it) }
    }

    fun clear(platform: Platform) {
        prefs.edit().remove(platform.key).apply()
    }
}
