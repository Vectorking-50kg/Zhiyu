package funapp.ctrlcv.zhiyu.notification

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences
import funapp.ctrlcv.zhiyu.core.data.notification.UsageAlertManager
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.network.interceptor.SessionEventBus
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Run against a minified APK as well: JVM/debug tests cannot catch R8 erasing field types. */
@RunWith(AndroidJUnit4::class)
class UsageAlertStateTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val preferences = NotificationPreferences(context)
    private val state = context.getSharedPreferences("usage_alert_state", Context.MODE_PRIVATE)
    private val key = "alert_${Platform.CLAUDE.key}"
    private var previousState: String? = null
    private var notificationsEnabled = false
    private lateinit var manager: UsageAlertManager

    @Before fun setUp() {
        previousState = state.getString(key, null)
        notificationsEnabled = preferences.notificationsEnabled
        preferences.notificationsEnabled = false
        state.edit().remove(key).commit()
        manager = UsageAlertManager(context, preferences, SessionEventBus(), AccountStore(context))
    }

    @After fun restore() {
        state.edit().putString(key, previousState).commit()
        preferences.notificationsEnabled = notificationsEnabled
    }

    @Test fun repeatedUpdatesReadPersistedFloatsWithoutCrashing() {
        for (percent in listOf(42.5f, 43.25f, 91f, 10f)) {
            manager.onUsageUpdated(usage(percent))
            assertSavedPercent(percent)
        }
    }

    @Test fun stateWrittenByAnOlderMinifiedReleaseIsReplacedSafely() {
        state.edit().putString(key, """{"a":{"5h":91.5},"b":1}""").commit()
        manager.onUsageUpdated(usage(25.5f))
        assertSavedPercent(25.5f)
        manager.onUsageUpdated(usage(26.5f))
        assertSavedPercent(26.5f)
    }

    private fun usage(percent: Float) = UsageInfo(Platform.CLAUDE, listOf(UsageItem("5h", percent)))

    private fun assertSavedPercent(percent: Float) {
        val saved = JSONObject(checkNotNull(state.getString(key, null)))
        assertEquals(percent.toDouble(), saved.getJSONObject("percents").getDouble("5h"), 0.0001)
        val level = when { percent >= 95f -> 2; percent >= 80f -> 1; else -> 0 }
        assertEquals(level, saved.getInt("notifiedLevel"))
    }
}
