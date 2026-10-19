package funapp.ctrlcv.zhiyu

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.widget.RemoteViews
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorStyle
import funapp.ctrlcv.zhiyu.core.ui.theme.ZhiyuTheme
import funapp.ctrlcv.zhiyu.feature.widget.*
import funapp.ctrlcv.zhiyu.feature.widget.R as WidgetR

class WidgetGalleryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ZhiyuTheme { WidgetGalleryScreen(::finish, ::pin) } }
    }

    private fun pin(kind: WidgetKind) {
        val manager = AppWidgetManager.getInstance(this)
        if (!manager.isRequestPinAppWidgetSupported) {
            Toast.makeText(this, "请长按桌面空白处，在小组件列表中选择知余", Toast.LENGTH_LONG).show()
            return
        }
        // The launcher fills the actual appWidgetId into this explicit callback intent.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0
        val callback = PendingIntent.getActivity(this, kind.ordinal, WidgetIntents.configure(this), flags)
        try {
            if (!manager.requestPinAppWidget(ComponentName(this, UsageWidgets.receiver(kind)), null, callback)) {
                Toast.makeText(this, "当前桌面未接受添加请求，请从桌面小组件列表添加", Toast.LENGTH_LONG).show()
            }
        } catch (_: Exception) {
            Toast.makeText(this, "请从桌面的小组件列表添加知余", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
private fun WidgetGalleryScreen(onBack: () -> Unit, onPin: (WidgetKind) -> Unit) {
    val c = LocalMonitorPalette.current
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(AppIcons.ArrowBack, "返回", onBack)
            UiText("桌面小组件", 17, 25, 600)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = LocalMonitorStyle.current.pagePadding)) {
            Spacer(Modifier.height(10.dp)); UiText("桌面上的知余", 25, 35, 600)
            Spacer(Modifier.height(7.dp)); UiText("选择适合你的布局。每个小组件可独立选择账户，颜色跟随系统深浅色。", 13, 23, color = c.muted)
            Spacer(Modifier.height(20.dp))
            WidgetKind.entries.forEach { kind ->
                val (layout, height) = when (kind) {
                    WidgetKind.OVERVIEW -> WidgetR.layout.widget_preview_overview to 230.dp
                    WidgetKind.DETAIL -> WidgetR.layout.widget_preview_detail to 150.dp
                    WidgetKind.COMPACT -> WidgetR.layout.widget_preview_compact to 180.dp
                    WidgetKind.STRIP -> WidgetR.layout.widget_preview_strip to 82.dp
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    UiText(kind.title, 17, 25, 600, modifier = Modifier.weight(1f))
                    UiText(kind.sizeLabel, 12, 20, color = c.muted)
                }
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    AndroidView(factory = { RemoteViews(context.packageName, layout).apply(it, null) },
                        modifier = (if (kind == WidgetKind.COMPACT) Modifier.width(180.dp) else Modifier.fillMaxWidth()).height(height))
                }
                Spacer(Modifier.height(10.dp)); UiText(kind.description, 12, 21, color = c.muted)
                Spacer(Modifier.height(11.dp)); UiButton("添加${kind.title}", { onPin(kind) }, secondary = true)
                Spacer(Modifier.height(25.dp))
            }
            UiText("预览为示例数据。添加后选择账户；点击数值查看详情，长按桌面上的组件可重新配置。桌面网格不同，实际占格可能略有差异。", 11, 20, color = c.muted)
            Spacer(Modifier.height(24.dp))
        }
    }
}
