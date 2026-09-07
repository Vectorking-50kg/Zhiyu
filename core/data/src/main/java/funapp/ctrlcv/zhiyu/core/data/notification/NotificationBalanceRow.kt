package funapp.ctrlcv.zhiyu.core.data.notification

import android.content.Context
import android.view.View
import android.widget.RemoteViews
import funapp.ctrlcv.zhiyu.core.data.R
import funapp.ctrlcv.zhiyu.core.domain.model.UsageMetric
import kotlin.math.roundToInt

/** Build one native quota or balance row for the expanded persistent notification. */
fun notificationBalanceRow(
    context: Context,
    platformName: String,
    metric: UsageMetric?,
    metricLabel: String?,
): RemoteViews {
    val percent = metric?.percent?.coerceIn(0, 100)
    val layout = when {
        percent == null || percent < 70 -> R.layout.notification_balance_row
        percent < 90 -> R.layout.notification_balance_row_warning
        else -> R.layout.notification_balance_row_danger
    }
    return RemoteViews(context.packageName, layout).apply {
        setTextViewText(R.id.platform_name, platformName)
        setTextViewText(R.id.platform_value, metric?.text ?: "--")
        setViewVisibility(R.id.platform_metric_label, if (metricLabel.isNullOrBlank()) View.GONE else View.VISIBLE)
        setTextViewText(R.id.platform_metric_label, metricLabel.orEmpty())

        setViewVisibility(R.id.platform_bar, if (percent == null) View.GONE else View.VISIBLE)
        if (percent != null && metric != null) {
            val elapsed = metric.elapsedPercent?.takeIf { it.isFinite() }?.coerceIn(0f, 100f)
            // The light layer uses the same bounds as usage, behind it, just like the overview.
            setProgressBar(R.id.platform_bar, 10_000, percent * 100, false)
            setInt(R.id.platform_bar, "setSecondaryProgress", elapsed?.let { (it * 100).roundToInt() } ?: 0)
            setContentDescription(R.id.platform_bar, buildString {
                append(metric.label ?: platformName)
                append("，用量 $percent%")
                elapsed?.let { append("，时间已过 ${it.toInt()}%") }
            })
        }
    }
}
