package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import android.os.SystemClock
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.action.ActionCallback
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        if (UsageWidgets.kindFor(context, widgetId) == null) return
        val store = WidgetConfigurationStore(context)
        if (!store.beginRefreshing(widgetId)) return
        val request = OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
            .setInputData(workDataOf(WidgetRefreshWorker.WIDGET_ID to widgetId)).build()
        try {
            withContext(Dispatchers.IO) {
                WorkManager.getInstance(context).enqueueUniqueWork("widget_refresh_$widgetId", ExistingWorkPolicy.KEEP, request).result.get()
            }
        } catch (error: Exception) {
            store.setRefreshing(widgetId, false)
            throw error
        }
        // Render feedback as soon as the click is accepted, including time spent queued.
        UsageWidgets.update(context, widgetId)
    }
}

/** WorkManager owns the request lifetime; all usage and failures go through the shared repository. */
class WidgetRefreshWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val widgetId = inputData.getInt(WIDGET_ID, -1)
        val kind = UsageWidgets.kindFor(applicationContext, widgetId) ?: return Result.success()
        val store = WidgetConfigurationStore(applicationContext)
        store.beginRefreshing(widgetId)
        val feedbackStarted = SystemClock.elapsedRealtime()
        try {
            UsageWidgets.update(applicationContext, widgetId)
            val data = WidgetDataStore.read(applicationContext)
            val targets = data.selected(store.read(widgetId, kind), kind).mapNotNull { it.reference }
            val repository = WidgetDataStore.dependencies(applicationContext).usageRepository()
            coroutineScope {
                targets.distinct().map { account -> async { repository.getUsage(account.platform, account.accountId) } }.awaitAll()
            }
            return Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.failure()
        } finally {
            withContext(NonCancellable) {
                // Keep very fast/paused/cache-only refreshes perceptible rather than flashing.
                val remaining = MIN_FEEDBACK_MILLIS - (SystemClock.elapsedRealtime() - feedbackStarted)
                if (remaining > 0) delay(remaining)
                store.setRefreshing(widgetId, false)
                UsageWidgets.updateAllNow(applicationContext)
            }
        }
    }

    companion object {
        const val WIDGET_ID = "widget_id"
        private const val MIN_FEEDBACK_MILLIS = 1_000L
    }
}
