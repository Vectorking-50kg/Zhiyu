package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

class UsageWidget : GlanceAppWidget() {

    override val sizeMode = SizeMode.Responsive(
        setOf(SMALL, MEDIUM, LARGE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = WidgetDataStore.read(context)
        provideContent {
            GlanceTheme {
                when (LocalSize.current) {
                    SMALL -> SmallWidgetContent(data)
                    MEDIUM -> MediumWidgetContent(data)
                    else -> LargeWidgetContent(data)
                }
            }
        }
    }

    companion object {
        val SMALL = DpSize(120.dp, 120.dp)
        val MEDIUM = DpSize(200.dp, 120.dp)
        val LARGE = DpSize(320.dp, 160.dp)
    }
}

@Composable
fun SmallWidgetContent(data: WidgetUsageData) {
    val platform = data.items.firstOrNull()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.background),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        if (platform != null) {
            Text(
                text = platform.name,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = "${platform.mainPercent.toInt()}%",
                style = TextStyle(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor(platform.mainPercent)
                )
            )
        } else {
            Text(text = "未登录", style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
fun MediumWidgetContent(data: WidgetUsageData) {
    val platform = data.items.firstOrNull()
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.background),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        if (platform != null) {
            Text(
                text = platform.name,
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = "${platform.mainPercent.toInt()}%",
                style = TextStyle(
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = progressColor(platform.mainPercent)
                )
            )
            Spacer(GlanceModifier.height(4.dp))
            platform.resetInfo?.let {
                Text(
                    text = it,
                    style = TextStyle(fontSize = 10.sp, color = ColorProvider(Color.Gray))
                )
            }
        } else {
            Text(text = "未登录", style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
fun LargeWidgetContent(data: WidgetUsageData) {
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .padding(12.dp)
            .background(GlanceTheme.colors.background),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        data.items.forEachIndexed { index, item ->
            PlatformColumn(item)
            if (index < data.items.size - 1) {
                Spacer(GlanceModifier.width(8.dp))
            }
        }
        if (data.items.isEmpty()) {
            Text(text = "请先登录平台账号", style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
fun PlatformColumn(item: WidgetPlatformItem) {
    Column(
        modifier = GlanceModifier.fillMaxHeight().width(100.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally
    ) {
        Text(
            text = item.name,
            style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = "${item.mainPercent.toInt()}%",
            style = TextStyle(
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = progressColor(item.mainPercent)
            )
        )
        Spacer(GlanceModifier.height(4.dp))
        item.resetInfo?.let {
            Text(
                text = it,
                style = TextStyle(fontSize = 9.sp, color = ColorProvider(Color.Gray))
            )
        }
    }
}

fun progressColor(percent: Float): ColorProvider = when {
    percent < 80f -> ColorProvider(Color(0xFF22C55E))
    percent < 90f -> ColorProvider(Color(0xFFEAB308))
    else -> ColorProvider(Color(0xFFEF4444))
}
