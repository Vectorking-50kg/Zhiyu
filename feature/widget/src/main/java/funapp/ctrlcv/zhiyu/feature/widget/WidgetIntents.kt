package funapp.ctrlcv.zhiyu.feature.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri

sealed interface WidgetDestination {
    data object Overview : WidgetDestination
    data class Account(val reference: WidgetAccountReference) : WidgetDestination
}

object WidgetIntents {
    fun open(context: Context, reference: WidgetAccountReference? = null): Intent {
        val uri = Uri.Builder().scheme("zhiyu").authority(if (reference == null) "overview" else "account")
        reference?.let { uri.appendQueryParameter("platform", it.platform.key).appendQueryParameter("accountId", it.accountId) }
        return checkNotNull(context.packageManager.getLaunchIntentForPackage(context.packageName)).apply {
            action = Intent.ACTION_VIEW
            data = uri.build()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
    }

    fun destination(intent: Intent?): WidgetDestination? {
        val uri = intent?.data?.takeIf { it.scheme == "zhiyu" } ?: return null
        return when (uri.host) {
            "overview" -> WidgetDestination.Overview
            "account" -> {
                val key = uri.getQueryParameter("platform") ?: return null
                val id = uri.getQueryParameter("accountId") ?: return null
                WidgetAccountReference.parse("$key/$id")?.let(WidgetDestination::Account)
            }
            else -> null
        }
    }

    fun configure(context: Context, widgetId: Int? = null): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
        .setClassName(context.packageName, "funapp.ctrlcv.zhiyu.WidgetConfigurationActivity")
        .apply { widgetId?.let { putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, it) } }
}
