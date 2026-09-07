package funapp.ctrlcv.zhiyu.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import funapp.ctrlcv.zhiyu.BuildConfig
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MonitorApp(vm: MonitorViewModel, onAppearance: () -> Unit, onAuthorize: (Platform, String?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val c = LocalMonitorPalette.current
    val appearance = LocalAppearanceSettings.current
    val backdrop = rememberGlassBackdrop()
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val snackbars = remember { SnackbarHostState() }
    val overviewScroll = rememberScrollState()
    val accountScroll = rememberScrollState()
    val settingsScroll = rememberScrollState()
    val scroll = when (state.page) { MonitorPage.OVERVIEW -> overviewScroll; MonitorPage.ACCOUNTS -> accountScroll; MonitorPage.SETTINGS -> settingsScroll }
    var permissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) permissionAction?.invoke() else vm.message("未开启通知权限，仍可在概览查看")
        permissionAction = null
    }
    val requestNotifications: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) action()
        else { permissionAction = action; permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val json = state.exportJson
        if (uri == null || json == null) vm.clearExport()
        else scope.launch {
            try {
                withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openOutputStream(uri)).use { it.write(json.toByteArray()) } }
                vm.message("备份已导出")
            } catch (_: Exception) { vm.message("备份写入失败，请重试") }
            finally { vm.clearExport() }
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try {
                val json = withContext(Dispatchers.IO) { checkNotNull(context.contentResolver.openInputStream(uri)).bufferedReader().use { it.readText() } }
                vm.importBackup(json)
            } catch (_: Exception) { vm.message("无法读取备份文件") }
        }
    }
    LaunchedEffect(state.exportJson) { if (state.exportJson != null) export.launch("zhiyu-backup.json") }
    LaunchedEffect(state.page, state.editor == null) { keyboard?.hide() }
    LaunchedEffect(vm) {
        vm.events.collect { event -> when (event) {
            is MonitorEvent.Authorize -> onAuthorize(event.platform, event.accountId)
            is MonitorEvent.Message -> launch {
                snackbars.currentSnackbarData?.dismiss()
                if (snackbars.showSnackbar(event.text, if (event.undo) "撤销" else null) == SnackbarResult.ActionPerformed) vm.undoRemove()
            }
        } }
    }
    DisposableEffect(owner, vm) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) vm.reload() }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = state.panel == null && (state.editor != null || state.page != MonitorPage.OVERVIEW)) {
        if (state.editor != null) vm.closeEditor() else vm.selectPage(MonitorPage.OVERVIEW)
    }
    val statusInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().coerceAtLeast(32.dp)
    val navigationInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val floating = appearance.bottomBarStyle != BottomBarStyle.FIXED
    val glass = appearance.bottomBarStyle == BottomBarStyle.GLASS && Build.VERSION.SDK_INT >= 31
    val bottomPadding = navigationInset + if (floating) 104.dp else 96.dp
    Box(Modifier.fillMaxSize().background(c.background)) {
        Box(Modifier.fillMaxSize().then(if (glass) Modifier.glassBackdropSource(backdrop) else Modifier)
            .background(c.background).padding(top = statusInset)) {
            if (state.editor != null) AccountEditorScreen(state, vm, requestNotifications)
            else when (state.page) {
                MonitorPage.OVERVIEW -> OverviewScreen(state, vm, overviewScroll, bottomPadding)
                MonitorPage.ACCOUNTS -> AccountsScreen(state, vm, accountScroll, bottomPadding)
                MonitorPage.SETTINGS -> MonitorSettingsScreen(state, vm, settingsScroll, bottomPadding, onAppearance)
            }
        }
        if (state.editor == null) {
            if (floating && !glass) Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(bottomPadding)
                .background(Brush.verticalGradient(0f to Color.Transparent, .8f to c.background, 1f to c.background)))
            MonitorNavigation(state.page, vm::selectPage, { scope.launch { scroll.animateScrollTo(0) } }, backdrop,
                Modifier.align(Alignment.BottomCenter).padding(bottom = navigationInset + if (floating) 16.dp else 0.dp))
        }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(navigationInset)
            .background(if (!floating && state.editor == null) c.toolbar else c.background))
        SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(start = 22.dp, end = 22.dp, bottom = navigationInset + 91.dp)) { data ->
            Snackbar(data, shape = RoundedCornerShape(12.dp), containerColor = c.primary, contentColor = c.onPrimary, actionColor = c.onPrimary)
        }
    }
    state.panel?.let { panel ->
        MonitorPanelHost(panel, state, vm, requestNotifications) { vm.dismissPanel(); import.launch(arrayOf("application/json")) }
    }
}

@Composable
private fun MonitorPanelHost(panel: MonitorPanel, state: MonitorState, vm: MonitorViewModel,
    requestNotifications: (() -> Unit) -> Unit, onImport: () -> Unit) {
    val c = LocalMonitorPalette.current
    val row = state.accounts.firstOrNull { it.platform == panel.platform && (panel.accountId == null || it.account.id == panel.accountId) }
    val title = when (panel.kind) {
        PanelKind.PROVIDERS -> "添加监控"; PanelKind.DETAIL -> panel.platform?.displayName ?: "账户详情"
        PanelKind.NOTIFICATIONS -> "通知与提醒"
        PanelKind.REFRESH -> "刷新策略"; PanelKind.PRIVACY -> "数据与隐私"; PanelKind.ABOUT -> "关于"
        PanelKind.REMOVE -> "移除监控？"
        PanelKind.SUCCESS -> "连接完成"; PanelKind.IMPORT -> "导入备份"
    }
    val subtitle = when (panel.kind) {
        PanelKind.PROVIDERS -> "选择一个供应商，连接你的 AI 账户。"
        PanelKind.DETAIL -> row?.let { "${planLabel(it)} · ${it.account.displayName}" }
        PanelKind.NOTIFICATIONS -> "全局偏好，单个账户仍可独立设置。"
        PanelKind.REFRESH -> "选择后台刷新间隔，概览也可以随时手动刷新。"
        PanelKind.PRIVACY -> "管理账户配置与应用偏好。"
        PanelKind.ABOUT -> "v${BuildConfig.VERSION_NAME}"
        else -> null
    }
    MonitorSheet(title, subtitle, vm::dismissPanel) {
        when (panel.kind) {
            PanelKind.PROVIDERS -> ProviderPicker(state, vm)
            PanelKind.DETAIL -> row?.let { AccountDetail(it, state.now) { vm.configure(it.platform, it.account.id) } }
            PanelKind.REFRESH -> listOf(15L, 30L, 60L).forEach { RadioOption("$it 分钟", state.refreshMinutes == it) { vm.setRefreshMinutes(it) } }
            PanelKind.NOTIFICATIONS -> {
                SettingGroup {
                    SettingRow("开启通知", "在允许的情况下发送用量提醒", AppIcons.Notifications, trailing = {
                        UiSwitch("开启通知", state.notifications) { if (it) requestNotifications { vm.setNotifications(true) } else vm.setNotifications(false) }
                    }); SettingDivider()
                    SettingRow("额度重置提醒", "确认额度重置后通知我", AppIcons.Refresh, trailing = { UiSwitch("额度重置提醒", state.resetAlerts, vm::setResetAlerts) }); SettingDivider()
                    SettingRow("登录失效提醒", "需要重新连接账户时提醒", AppIcons.Key, trailing = { UiSwitch("登录失效提醒", state.sessionAlerts, vm::setSessionAlerts) })
                }
                Spacer(Modifier.height(12.dp)); UiText("用量达到 80% 时提醒，达到 95% 时再次提醒。余额与具体账户的提醒可在账户页设置。", 12, 23, color = c.muted)
            }
            PanelKind.PRIVACY -> {
                SettingGroup {
                    SettingRow("导出备份", "保存账户连接与配置", AppIcons.FileUpload, onClick = { vm.prepareExport() }); SettingDivider()
                    SettingRow("导入备份", "从备份文件恢复账户", AppIcons.FileDownload, onClick = { vm.showPanel(PanelKind.IMPORT) })
                }
                Spacer(Modifier.height(12.dp)); UiText("登录信息和密钥加密保存在本设备。备份文件包含登录凭据，请妥善保存，只导入来源可信的备份。", 12, 23, color = c.muted)
            }
            PanelKind.IMPORT -> {
                UiText("导入会合并备份中的账户与密钥，已有数据不会被删除。请确保文件来源可信。", 12, 23, color = c.muted)
                Spacer(Modifier.height(22.dp)); UiButton("选择文件", onImport)
            }
            PanelKind.ABOUT -> {
                Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ApplicationIcon()
                    Spacer(Modifier.height(16.dp)); UiText("知余", 22, 30, 600); Spacer(Modifier.height(8.dp))
                }
            }
            PanelKind.REMOVE -> {
                UiText("${panel.platform?.displayName} 将从概览和账户列表中移除。你可以随时重新添加。", 12, 23, color = c.muted)
                Spacer(Modifier.height(22.dp)); UiButton("移除监控", vm::removeAccount)
                Spacer(Modifier.height(9.dp)); UiButton("保留账户", vm::dismissPanel, secondary = true)
            }
            PanelKind.SUCCESS -> {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.padding(top = 12.dp, bottom = 18.dp).size(64.dp).background(c.greenSoft, CircleShape), contentAlignment = Alignment.Center) { AppIcon(AppIcons.Check, null, size = 28.dp, tint = c.green) }
                    UiText("${panel.platform?.displayName} 已加入监控", 21, 32, 500)
                    Spacer(Modifier.height(8.dp)); UiText("你可以在概览查看余额和用量，\n也可以随时回到账户页修改配置。", 12, 22, color = c.muted, align = TextAlign.Center)
                }
                Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(13.dp)).background(c.soft).padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    UiText(if (panel.platform?.isBalanceProvider() == true) "当前可用余额" else "当前最高已用", 12, 18, modifier = Modifier.weight(1f))
                    UiText(row?.let(::mainReading) ?: "等待同步", 23, 34, 600, tracking = -.5f)
                }
                Spacer(Modifier.height(20.dp)); UiButton("去概览查看", { vm.selectPage(MonitorPage.OVERVIEW) })
                Spacer(Modifier.height(9.dp)); UiButton("返回账户", { vm.selectPage(MonitorPage.ACCOUNTS) }, secondary = true)
            }
        }
    }
}

@Composable
private fun RadioOption(label: String, chosen: Boolean, onClick: () -> Unit) {
    val c = LocalMonitorPalette.current
    Row(Modifier.fillMaxWidth().height(56.dp).selectable(chosen, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        UiText(label, 13, 20, modifier = Modifier.weight(1f))
        if (chosen) AppIcon(AppIcons.CheckCircle, null, size = 20.dp, tint = c.primary)
        else Box(Modifier.size(20.dp).border(1.5.dp, c.line, CircleShape))
    }
    SettingDivider()
}
