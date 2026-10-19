package funapp.ctrlcv.zhiyu

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorStyle
import funapp.ctrlcv.zhiyu.core.ui.theme.ZhiyuTheme
import funapp.ctrlcv.zhiyu.feature.widget.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

class WidgetConfigurationActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val kind = UsageWidgets.kindFor(this, widgetId) ?: run { finish(); return }
        enableEdgeToEdge()
        setContent {
            ZhiyuTheme {
                WidgetConfigurationScreen(widgetId, kind, onBack = ::finish, onSaved = {
                    setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                    finish()
                }, onOpenApp = { startActivity(WidgetIntents.open(this)) })
            }
        }
    }
}

@Composable
private fun WidgetConfigurationScreen(widgetId: Int, kind: WidgetKind, onBack: () -> Unit, onSaved: () -> Unit, onOpenApp: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = LocalMonitorPalette.current
    val store = remember { WidgetConfigurationStore(context) }
    val scope = rememberCoroutineScope()
    var data by remember { mutableStateOf<WidgetUsageData?>(null) }
    var mode by rememberSaveable { mutableStateOf(WidgetConfiguration.defaultFor(kind).mode) }
    var selected by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, widgetId) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && loaded) scope.launch {
                try {
                    data = withContext(Dispatchers.IO) { WidgetDataStore.read(context) }
                    selected = ArrayList(selected.filter { key -> data?.items?.any { it.accountKey == key } == true })
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = "暂时无法读取账户，请返回后重试" }
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(widgetId) {
        try {
            val (usage, settings) = withContext(Dispatchers.IO) { WidgetDataStore.read(context) to store.read(widgetId, kind) }
            data = usage
            if (!loaded) {
                mode = settings.mode
                selected = ArrayList(settings.accountKeys.filter { key -> usage.items.any { it.accountKey == key } })
                loaded = true
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { error = "暂时无法读取账户，请返回后重试" }
    }
    Column(Modifier.fillMaxSize().background(c.background).windowInsetsPadding(WindowInsets.systemBars)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(AppIcons.ArrowBack, "取消配置", onBack)
            UiText("配置小组件", 17, 25, 600, modifier = Modifier.weight(1f))
            UiText(kind.sizeLabel, 12, 20, color = c.muted)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = LocalMonitorStyle.current.pagePadding)) {
            Spacer(Modifier.height(16.dp))
            UiText(kind.title, 25, 35, 600)
            Spacer(Modifier.height(7.dp)); UiText(kind.description, 13, 22, color = c.muted)
            Spacer(Modifier.height(22.dp))
            if (!kind.singleAccount) {
                SectionCaption("显示哪些账户"); Spacer(Modifier.height(10.dp))
                SettingGroup {
                    listOf(WidgetSelectionMode.ALL to "全部账户", WidgetSelectionMode.PINNED to "跟随状态栏固定", WidgetSelectionMode.CUSTOM to "自选账户")
                        .forEachIndexed { index, (choice, label) ->
                            if (index > 0) SettingDivider()
                            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).selectable(mode == choice, role = Role.RadioButton,
                                onClick = { mode = choice; error = null }).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(mode == choice, null)
                                Spacer(Modifier.width(8.dp)); UiText(label, 14, 22)
                            }
                        }
                }
                Spacer(Modifier.height(20.dp))
            }
            val choosing = kind.singleAccount || mode == WidgetSelectionMode.CUSTOM
            SectionCaption(if (choosing) if (kind.singleAccount) "选择一个账户" else "选择监控账户" else "账户预览")
            Spacer(Modifier.height(10.dp))
            val accounts = data?.items.orEmpty().filter { mode != WidgetSelectionMode.PINNED || it.pinned }
            when {
                data == null && error == null -> UiText("正在读取账户…", 13, 22, color = c.muted)
                data?.items?.isEmpty() == true -> {
                    UiText("先在知余中添加一个账户，再为小组件选择订阅。", 14, 24, color = c.muted)
                    Spacer(Modifier.height(14.dp)); UiButton("打开知余添加账户", onOpenApp, secondary = true)
                }
                accounts.isEmpty() -> UiText("还没有固定到状态栏的账户，可在账户设置中开启。", 13, 22, color = c.muted)
                else -> SettingGroup {
                    accounts.forEachIndexed { index, item ->
                        if (index > 0) SettingDivider()
                        val key = item.accountKey ?: return@forEachIndexed
                        val checked = !choosing || key in selected
                        val toggle: (Boolean) -> Unit = { enabled ->
                            selected = if (kind.singleAccount) arrayListOf(key)
                            else ArrayList(if (enabled) selected + key else selected - key)
                            error = null
                        }
                        val interaction = when {
                            !choosing -> Modifier
                            kind.singleAccount -> Modifier.selectable(checked, role = Role.RadioButton, onClick = { toggle(true) })
                            else -> Modifier.toggleable(checked, role = Role.Checkbox, onValueChange = toggle)
                        }
                        Row(Modifier.fillMaxWidth().heightIn(min = 70.dp).then(interaction).padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (choosing) {
                                if (kind.singleAccount) RadioButton(checked, null) else Checkbox(checked, null)
                                Spacer(Modifier.width(4.dp))
                            }
                            item.platform?.let { ProviderLogo(it, 28.dp) }; Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                UiText(item.name, 13, 21, 600)
                                UiText(item.accountName, 11, 18, color = c.muted, maxLines = 2)
                            }
                            Spacer(Modifier.width(7.dp)); UiText(item.mainText, 14, 22, 600)
                        }
                    }
                }
            }
            Spacer(Modifier.height(15.dp))
            UiText(if (kind == WidgetKind.STRIP) "紧凑监控栏显示 2–3 个账户，更多账户可点击「+」查看。" else "账户选择仅用于这个小组件，长按桌面上的组件可重新配置。", 11, 20, color = c.muted)
            Spacer(Modifier.height(14.dp))
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = LocalMonitorStyle.current.pagePadding, vertical = 14.dp)) {
            error?.let { UiText(it, 12, 20, color = c.red); Spacer(Modifier.height(8.dp)) }
            UiButton(if (saving) "正在保存…" else "保存小组件", enabled = data != null && !saving && data?.items?.isNotEmpty() == true, onClick = {
                if ((kind.singleAccount || mode == WidgetSelectionMode.CUSTOM) && selected.isEmpty()) {
                    error = "请选择${if (kind.singleAccount) "一个" else "至少一个"}账户"
                } else scope.launch {
                    saving = true; error = null
                    try {
                        withContext(Dispatchers.IO) {
                            store.save(widgetId, WidgetConfiguration(if (kind.singleAccount) WidgetSelectionMode.CUSTOM else mode, selected.toSet()))
                            UsageWidgets.update(context, widgetId)
                        }
                        onSaved()
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = "暂时无法保存小组件，请重试" }
                    finally { saving = false }
                }
            })
        }
    }
}
