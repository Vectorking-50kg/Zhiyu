package funapp.ctrlcv.zhiyu.core.storage

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RefreshPreferences @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("refresh_preferences", Context.MODE_PRIVATE)
    var intervalMinutes: Long
        get() = prefs.getLong("interval_minutes", 15L).takeIf { it in setOf(15L, 30L, 60L) } ?: 15L
        set(value) {
            require(value in setOf(15L, 30L, 60L))
            prefs.edit().putLong("interval_minutes", value).apply()
        }
}
