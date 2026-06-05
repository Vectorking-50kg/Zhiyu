package funapp.ctrlcv.zhiyu.core.data.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import funapp.ctrlcv.zhiyu.core.data.worker.RefreshWorker
import javax.inject.Inject

@AndroidEntryPoint
class NotificationRefreshReceiver : BroadcastReceiver() {

    @Inject
    lateinit var balanceNotifier: BalanceNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != BalanceNotificationManager.ACTION_REFRESH_NOTIFICATION) return
        balanceNotifier.showRefreshing()
        RefreshWorker.refreshNow(context)
    }
}
