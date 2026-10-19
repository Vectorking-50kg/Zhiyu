package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import android.animation.ValueAnimator
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.min
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.currentState
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.*
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.ui.components.platformIcon
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

abstract class ZhiyuUsageWidget(val kind: WidgetKind) : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(when (kind) {
        WidgetKind.OVERVIEW -> setOf(DpSize(120.dp, 120.dp), DpSize(250.dp, 250.dp), DpSize(300.dp, 320.dp), DpSize(360.dp, 380.dp))
        WidgetKind.DETAIL -> setOf(DpSize(240.dp, 128.dp), DpSize(280.dp, 160.dp), DpSize(340.dp, 200.dp))
        WidgetKind.COMPACT -> setOf(DpSize(140.dp, 160.dp), DpSize(180.dp, 200.dp), DpSize(220.dp, 240.dp))
        WidgetKind.STRIP -> setOf(DpSize(240.dp, 64.dp), DpSize(300.dp, 80.dp), DpSize(360.dp, 88.dp))
    })

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val widgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        suspend fun read() = withContext(Dispatchers.IO) {
            val store = WidgetConfigurationStore(context)
            Triple(WidgetDataStore.read(context), store.read(widgetId, kind), store.isRefreshing(widgetId))
        }
        val initial = read()
        val configure = actionStartActivity(WidgetIntents.configure(context, widgetId))
        provideContent {
            val revision = currentState<Preferences>()[widgetRevisionKey] ?: 0L
            val snapshot by produceState(initial, revision) { value = read() }
            val (data, configuration, refreshing) = snapshot
            val selected = data.selected(configuration, kind)
            GlanceTheme {
                when (kind) {
                    WidgetKind.OVERVIEW -> OverviewWidgetContent(context, selected, configuration, refreshing, configure)
                    WidgetKind.DETAIL -> DetailWidgetContent(context, selected.firstOrNull(), configuration, refreshing, configure)
                    WidgetKind.COMPACT -> CompactWidgetContent(context, selected.firstOrNull(), configuration, refreshing, configure)
                    WidgetKind.STRIP -> StripWidgetContent(context, selected, configuration, refreshing, configure)
                }
            }
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        WidgetConfigurationStore(context).remove(GlanceAppWidgetManager(context).getAppWidgetId(glanceId))
    }
}

/** Retain the original receiver/widget class for widgets already placed on a home screen. */
class UsageWidget : ZhiyuUsageWidget(WidgetKind.OVERVIEW)
class DetailUsageWidget : ZhiyuUsageWidget(WidgetKind.DETAIL)
class CompactUsageWidget : ZhiyuUsageWidget(WidgetKind.COMPACT)
class StripUsageWidget : ZhiyuUsageWidget(WidgetKind.STRIP)

internal val widgetRevisionKey = longPreferencesKey("render_revision")

private object WidgetColors {
    val text = ColorProvider(R.color.widget_text)
    val muted = ColorProvider(R.color.widget_muted)
    val track = ColorProvider(R.color.widget_track)
    val accent = ColorProvider(R.color.widget_accent)
    val transparent = ColorProvider(androidx.compose.ui.graphics.Color.Transparent)
}

private fun rootModifier(padding: Int = 12) = GlanceModifier.fillMaxSize().appWidgetBackground()
    .background(ImageProvider(R.drawable.widget_background)).cornerRadius(24.dp).padding(padding.dp)

private fun textStyle(size: Int, bold: Boolean = false, color: ColorProvider = WidgetColors.text) = TextStyle(
    fontSize = size.sp, color = color, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
)

/** Fit the entire number within the fixed home-screen area, including large system text. */
private fun metricStyle(value: String, desired: Float, width: Float, scale: Float, height: Float = Float.POSITIVE_INFINITY,
    color: ColorProvider = WidgetColors.text): TextStyle {
    val units = value.sumOf { character -> when {
        character == '.' || character == ',' -> 0.3
        character.code > 255 -> 1.0
        else -> 0.65
    } }.coerceAtLeast(1.0).toFloat()
    val size = min(desired, min(width / (units * scale), height / (1.3f * scale))).coerceAtLeast(8f)
    return TextStyle(fontSize = size.sp, fontWeight = FontWeight.Bold, color = color)
}

@Composable
private fun RefreshButton(description: String, refreshing: Boolean) {
    val context = androidx.glance.LocalContext.current
    val modifier = GlanceModifier.size(48.dp).padding(14.dp)
    if (refreshing && ValueAnimator.areAnimatorsEnabled()) {
        val views = RemoteViews(context.packageName, R.layout.widget_refresh_loading).apply {
            setContentDescription(R.id.widget_refresh_spinner, "$description，正在刷新")
        }
        AndroidRemoteViews(views, modifier)
    } else {
        Image(ImageProvider(R.drawable.ic_widget_refresh), if (refreshing) "$description，正在刷新" else description,
            modifier = if (refreshing) modifier else
                GlanceModifier.size(48.dp).clickable(actionRunCallback<RefreshWidgetAction>()).padding(14.dp))
    }
}

@Composable
private fun ProviderImage(item: WidgetPlatformItem, size: Int = 26) {
    item.platform?.let { platform ->
        Image(ImageProvider(platformIcon(platform)), null,
            modifier = GlanceModifier.size(size.dp).background(ImageProvider(R.drawable.widget_logo_background))
                .cornerRadius(7.dp).padding(3.dp))
    }
}

@Composable
private fun OverviewWidgetContent(
    context: Context, items: List<WidgetPlatformItem>, configuration: WidgetConfiguration,
    refreshing: Boolean, configure: Action,
) {
    if (LocalSize.current.width < 220.dp) {
        Column(rootModifier()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("知余", GlanceModifier.defaultWeight().clickable(actionStartActivity(WidgetIntents.open(context))), textStyle(14, true))
            }
            items.take(2).forEach { item ->
                Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity(WidgetIntents.open(context, item.reference)))) {
                    Text(shortName(item), GlanceModifier.defaultWeight(), textStyle(11), maxLines = 1)
                    Text(item.mainText, style = textStyle(13, true, progressColor(item.mainPercent)))
                }
            }
            if (items.isEmpty()) Text("点击选择账户", GlanceModifier.clickable(configure), textStyle(11, color = WidgetColors.muted))
        }
        return
    }
    Column(rootModifier()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(WidgetIntents.open(context)))) {
                Text("知余 · 订阅总览", style = textStyle(16, true), maxLines = 1)
                Text(if (refreshing) "正在同步…" else "${items.size} 个账户", style = textStyle(11, color = WidgetColors.muted))
            }
            RefreshButton("刷新所选账户", refreshing)
        }
        if (items.isEmpty()) {
            EmptyWidget(configuration, configure, GlanceModifier.defaultWeight())
        } else {
            LazyColumn(GlanceModifier.fillMaxWidth().defaultWeight()) {
                items(items, itemId = { (it.accountKey ?: it.name).hashCode().toLong() }) { item ->
                    OverviewAccountRow(context, item)
                }
            }
            Text("最近同步 ${updatedTime(items.maxOfOrNull { it.updatedAt } ?: 0)} · 点击账户查看详情",
                style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
        }
    }
}

@Composable
private fun OverviewAccountRow(context: Context, item: WidgetPlatformItem) {
    val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    Row(GlanceModifier.fillMaxWidth().height(maxOf(58f, 38f * scale + 20f).dp)
        .clickable(actionStartActivity(WidgetIntents.open(context, item.reference))).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        ProviderImage(item)
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight()) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(item.name, GlanceModifier.defaultWeight(), textStyle(12, true), maxLines = 1)
                Spacer(GlanceModifier.width(6.dp))
                Text(item.mainText, style = textStyle(16, true, progressColor(item.mainPercent)), maxLines = 1)
            }
            Text(listOfNotNull(item.accountName, item.statusLabel ?: item.metricLabel).joinToString(" · "),
                style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
            if (item.mainPercent >= 0) {
                Spacer(GlanceModifier.height(3.dp))
                QuotaBar(item.mainPercent, item.elapsedPercent, item.metricLabel ?: item.name)
            }
        }
    }
}

@Composable
private fun DetailWidgetContent(
    context: Context, item: WidgetPlatformItem?, configuration: WidgetConfiguration,
    refreshing: Boolean, configure: Action,
) {
    Column(rootModifier()) {
        if (item == null) { EmptyWidget(configuration, configure); return@Column }
        val open = actionStartActivity(WidgetIntents.open(context, item.reference))
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val readingStyle = metricStyle(item.mainText, 25f, LocalSize.current.width.value - 132f,
            scale, color = progressColor(item.mainPercent))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(item.name, style = textStyle(13, true), maxLines = 1)
                Text(item.accountName, style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
            }
            RefreshButton("刷新账户", refreshing)
            Text(item.mainText, GlanceModifier.clickable(open), readingStyle, maxLines = 1)
        }
        val limit = min(if (LocalSize.current.height >= 190.dp) 5 else if (LocalSize.current.height >= 150.dp) 3 else 2,
            ((LocalSize.current.height.value - 72f * scale) / (24f * scale)).toInt().coerceAtLeast(1))
        val details = item.visibleDetails(limit)
        Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open), verticalAlignment = Alignment.CenterVertically) {
            if (details.isEmpty()) Text("暂无额度数据，点击账户检查连接", style = textStyle(11, color = WidgetColors.muted), maxLines = 2)
            details.forEach { DetailLine(it) }
        }
        Text(if (refreshing) "正在同步…" else listOfNotNull(
            item.statusLabel, "${updatedTime(item.updatedAt)} 更新",
            "另 ${item.details.size - details.size} 项".takeIf { item.details.size > details.size },
        ).joinToString(" · "), style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
    }
}

@Composable
private fun DetailLine(item: UsageItem) {
    Column(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(item.label.replace("｜", " · "), GlanceModifier.defaultWeight(), textStyle(11, color = WidgetColors.muted), maxLines = 1)
            Spacer(GlanceModifier.width(4.dp))
            Text(if (item.unlimited) "无限制" else if (item.percent >= 0) "${item.percent.toInt()}%" else item.valueText ?: "--",
                style = textStyle(11, true), maxLines = 1)
            if (item.percent >= 0 && !item.unlimited) item.resetCountdown?.let {
                Spacer(GlanceModifier.width(6.dp))
                Text(it.replace("小时", "时").replace("分钟", "分"), style = textStyle(9, color = WidgetColors.muted), maxLines = 1)
            }
        }
        if (item.percent >= 0 && !item.unlimited) {
            Spacer(GlanceModifier.height(3.dp))
            QuotaBar(item.percent, item.elapsedPercent, item.label)
        }
    }
}

@Composable
private fun CompactWidgetContent(
    context: Context, item: WidgetPlatformItem?, configuration: WidgetConfiguration,
    refreshing: Boolean, configure: Action,
) {
    Column(rootModifier()) {
        if (item == null) { EmptyWidget(configuration, configure); return@Column }
        val open = actionStartActivity(WidgetIntents.open(context, item.reference))
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val largeText = scale > 1.3f
        val heroHeight = (LocalSize.current.height.value - 24f - 28f * scale - 48f - if (largeText) 0f else 14f * scale).coerceAtLeast(16f)
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(shortName(item), style = textStyle(13, true), maxLines = 1)
                Text(item.accountName, style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
            }
        }
        Column(GlanceModifier.fillMaxWidth().defaultWeight().clickable(open)
            .semantics { contentDescription = "${item.accountName}，${item.metricLabel ?: "暂无数据"}，${item.mainText}" }, verticalAlignment = Alignment.CenterVertically) {
            Text(item.mainText, style = metricStyle(item.mainText, if (item.mainPercent < 0) 26f else 34f,
                LocalSize.current.width.value - 24f, scale, heroHeight, progressColor(item.mainPercent)), maxLines = 1)
            if (!largeText) Text(item.metricLabel ?: "暂无数据", style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
            if (!largeText && item.mainPercent >= 0 && LocalSize.current.height >= 190.dp) {
                Spacer(GlanceModifier.height(5.dp))
                QuotaBar(item.mainPercent, item.elapsedPercent, item.metricLabel ?: item.name)
            }
        }
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(GlanceModifier.defaultWeight().clickable(open)) {
                Text(if (refreshing) "正在同步…" else item.statusLabel ?: if (largeText) item.metricLabel ?: "暂无数据" else "${updatedTime(item.updatedAt)} 更新",
                    style = textStyle(10, color = WidgetColors.muted), maxLines = 1)
                if (!largeText) item.resetInfo?.let { Text(it, style = textStyle(9, color = WidgetColors.muted), maxLines = 2) }
            }
            RefreshButton("刷新账户", refreshing)
        }
    }
}

@Composable
private fun StripWidgetContent(
    context: Context, items: List<WidgetPlatformItem>, configuration: WidgetConfiguration,
    refreshing: Boolean, configure: Action,
) {
    Row(rootModifier(8), verticalAlignment = Alignment.CenterVertically) {
        if (items.isEmpty()) { EmptyWidget(configuration, configure, GlanceModifier.defaultWeight()); return@Row }
        val slots = if (LocalSize.current.width >= 300.dp) 3 else 2
        val scale = context.resources.configuration.fontScale.coerceAtLeast(1f)
        val showStatus = LocalSize.current.height >= 80.dp && scale <= 1.3f
        val metricWidth = (LocalSize.current.width.value - 16f - if (items.size > slots) 24f else 0f) / min(items.size, slots)
        val metricHeight = LocalSize.current.height.value - 16f - 12f * scale - if (showStatus) 12f * scale else 0f
        items.take(slots).forEach { item ->
            Column(GlanceModifier.defaultWeight().clickable(actionStartActivity(WidgetIntents.open(context, item.reference)))
                .semantics { contentDescription = "${item.name}，${item.accountName}，${item.mainText}，${item.statusLabel ?: ""}" }) {
                Text(shortName(item), style = textStyle(10, true), maxLines = 1)
                Text(item.mainText, style = metricStyle(item.mainText, 18f, metricWidth, scale, metricHeight, progressColor(item.mainPercent)), maxLines = 1)
                if (showStatus) Text(if (refreshing) "同步中" else item.statusLabel ?: "${updatedTime(item.updatedAt)} 更新",
                    style = textStyle(9, color = WidgetColors.muted), maxLines = 1)
            }
        }
        if (items.size > slots) Text("+${items.size - slots}", GlanceModifier.width(24.dp)
            .clickable(actionStartActivity(WidgetIntents.open(context))), textStyle(10, true, WidgetColors.muted))
    }
}

@Composable
private fun EmptyWidget(configuration: WidgetConfiguration, configure: Action, modifier: GlanceModifier = GlanceModifier.fillMaxSize()) {
    Column(modifier.clickable(configure), verticalAlignment = Alignment.CenterVertically) {
        Text("知余", style = textStyle(16, true))
        Text(when {
            configuration.mode == WidgetSelectionMode.PINNED -> "暂无状态栏固定账户"
            configuration.accountKeys.isNotEmpty() -> "账户已移除，请重新选择"
            else -> "点击选择监控账户"
        }, style = textStyle(11, color = WidgetColors.muted), maxLines = 2)
        Text("配置小组件", style = textStyle(11, true, WidgetColors.accent))
    }
}

@Composable
private fun QuotaBar(percent: Float, elapsed: Float?, label: String) {
    val used = percent.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    val time = elapsed?.takeIf { it.isFinite() }?.coerceIn(0f, 100f)
    Box(GlanceModifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp)
        .semantics { contentDescription = "$label，已用 ${used.toInt()}%" + (time?.let { "，时间已过 ${it.toInt()}%" } ?: "") }) {
        if (time != null) LinearProgressIndicator(time / 100f, GlanceModifier.fillMaxSize(),
            color = ColorProvider(R.color.widget_elapsed), backgroundColor = WidgetColors.track)
        LinearProgressIndicator(used / 100f, GlanceModifier.fillMaxSize(), color = progressColor(used),
            backgroundColor = if (time != null) WidgetColors.transparent else WidgetColors.track)
    }
}

fun progressColor(percent: Float): ColorProvider = ColorProvider(when {
    percent < 0 -> R.color.widget_text
    percent < 80 -> R.color.widget_green
    percent < 95 -> R.color.widget_amber
    else -> R.color.widget_red
})

private fun shortName(item: WidgetPlatformItem): String = when (item.platform) {
    Platform.ZEN -> "Zen"
    Platform.OPENCODE_GO -> "Go"
    else -> item.name
}

private fun updatedTime(time: Long): String = if (time <= 0) "尚未同步" else DateTimeFormatter.ofPattern("HH:mm")
    .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(time))
