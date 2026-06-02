package funapp.ctrlcv.zhiyu

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import funapp.ctrlcv.zhiyu.core.data.notification.BalanceNotificationManager
import funapp.ctrlcv.zhiyu.core.data.worker.RefreshWorker
import javax.inject.Inject

@HiltAndroidApp
class ZhiyuApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var balanceNotifier: BalanceNotificationManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        RefreshWorker.schedule(this)
        // 进程重建（重启 / 被系统回收后重新拉起）时，依据缓存恢复状态栏常驻通知
        balanceNotifier.refresh()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        val warnChannel = NotificationChannel(
            "usage_warn",
            "用量警告",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "用量超过80%时提醒"
        }

        val dangerChannel = NotificationChannel(
            "usage_danger",
            "用量紧急",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "用量超过95%时强提醒"
            enableVibration(true)
        }

        val expiredChannel = NotificationChannel(
            "session_expired",
            "登录过期",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Cookie过期需要重新登录"
        }

        manager.createNotificationChannels(listOf(warnChannel, dangerChannel, expiredChannel))
    }
}
