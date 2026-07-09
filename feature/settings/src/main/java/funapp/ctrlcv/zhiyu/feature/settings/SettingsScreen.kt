package funapp.ctrlcv.zhiyu.feature.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.ui.components.CardGroup
import funapp.ctrlcv.zhiyu.core.ui.theme.CustomColors
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalBrandConfig
import funapp.ctrlcv.zhiyu.core.ui.theme.PresetThemes

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuth: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val pendingExportJson = remember { mutableStateOf<String?>(null) }
    val pendingNotificationToast = remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val json = pendingExportJson.value
        pendingExportJson.value = null
        if (uri == null || json == null) return@rememberLauncherForActivityResult
        try {
            context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
            viewModel.onExportWritten()
        } catch (e: Exception) {
            viewModel.onExportHandled()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val json = context.contentResolver.openInputStream(uri)?.use {
                it.readBytes().toString(Charsets.UTF_8)
            } ?: return@rememberLauncherForActivityResult
            viewModel.importData(json)
        } catch (e: Exception) {
            viewModel.importData("")
        }
    }

    // 授权通过后待回放的开关动作：常驻通知与各提醒开关共用同一个权限申请入口
    val pendingPermissionGrant = remember { mutableStateOf<(() -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingPermissionGrant.value
        pendingPermissionGrant.value = null
        if (granted) {
            action?.invoke()
        } else {
            pendingNotificationToast.value = "未授予通知权限，无法发送通知"
        }
    }

    val withNotificationPermission: (() -> Unit) -> Unit = { action ->
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingPermissionGrant.value = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    val onToggleNotification: (Boolean) -> Unit = { enable ->
        if (!enable) viewModel.setPersistentNotificationEnabled(false)
        else withNotificationPermission { viewModel.setPersistentNotificationEnabled(true) }
    }

    LaunchedEffect(pendingNotificationToast.value) {
        val msg = pendingNotificationToast.value ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        pendingNotificationToast.value = null
    }

    LaunchedEffect(uiState.exportJson) {
        val json = uiState.exportJson ?: return@LaunchedEffect
        pendingExportJson.value = json
        viewModel.onExportHandled()
        exportLauncher.launch("zhiyu_backup_${System.currentTimeMillis()}.json")
    }

    LaunchedEffect(uiState.backupMessage) {
        val msg = uiState.backupMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearBackupMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = CustomColors.topBarColors,
            )
        },
        containerColor = CustomColors.topBarColors.containerColor,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
                start = 8.dp,
                end = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item("appearance") {
                AppearanceSection(
                    colorMode = uiState.colorMode,
                    themeId = uiState.themeId,
                    onClickColorMode = { viewModel.showColorModeDialog() },
                )
            }

            item("themePicker") {
                ThemePickerSection(
                    selectedId = uiState.themeId,
                    onSelect = { viewModel.setThemeId(it) },
                )
            }

            item("notification") {
                NotificationSection(
                    enabled = uiState.persistentNotificationEnabled,
                    pinned = uiState.pinnedPlatforms,
                    configured = uiState.loggedInPlatforms + uiState.configuredApiPlatforms,
                    onToggleEnabled = onToggleNotification,
                    onTogglePlatform = { viewModel.togglePinnedPlatform(it) },
                )
            }

            item("alerts") {
                AlertNotificationSection(
                    usageAlertEnabled = uiState.usageAlertEnabled,
                    resetReminderEnabled = uiState.resetReminderEnabled,
                    sessionExpiredAlertEnabled = uiState.sessionExpiredAlertEnabled,
                    onToggleUsageAlert = { enable ->
                        if (!enable) viewModel.setUsageAlertEnabled(false)
                        else withNotificationPermission { viewModel.setUsageAlertEnabled(true) }
                    },
                    onToggleResetReminder = { enable ->
                        if (!enable) viewModel.setResetReminderEnabled(false)
                        else withNotificationPermission { viewModel.setResetReminderEnabled(true) }
                    },
                    onToggleSessionExpiredAlert = { enable ->
                        if (!enable) viewModel.setSessionExpiredAlertEnabled(false)
                        else withNotificationPermission { viewModel.setSessionExpiredAlertEnabled(true) }
                    },
                )
            }

            item("webAccounts") {
                WebAccountSection(
                    loggedIn = uiState.loggedInPlatforms,
                    onClickPlatform = onNavigateToAuth,
                )
            }

            item("apiKeys") {
                ApiKeySection(
                    configured = uiState.configuredApiPlatforms,
                    onClickPlatform = { viewModel.showApiKeyDialog(it) },
                    onClearPlatform = { viewModel.clearApiKey(it) },
                )
            }

            item("backup") {
                BackupSection(
                    onClickExport = { viewModel.prepareExport() },
                    onClickImport = { viewModel.showImportConfirm() },
                )
            }

            item("about") {
                AboutSection()
            }
        }
    }

    if (uiState.showColorModeDialog) {
        ColorModeDialog(
            current = uiState.colorMode,
            onSelect = { viewModel.setColorMode(it) },
            onDismiss = { viewModel.dismissColorModeDialog() }
        )
    }

    uiState.apiKeyDialog?.let { dialog ->
        ApiKeyDialog(
            state = dialog,
            onDismiss = { viewModel.dismissApiKeyDialog() },
            onApiKeyChange = { viewModel.updateApiKey(it) },
            onConfirm = { viewModel.saveApiKey() }
        )
    }

    if (uiState.showImportConfirm) {
        ImportConfirmDialog(
            onDismiss = { viewModel.dismissImportConfirm() },
            onConfirm = {
                viewModel.dismissImportConfirm()
                importLauncher.launch(arrayOf("application/json", "*/*"))
            }
        )
    }
}

@Composable
private fun AppearanceSection(
    colorMode: ColorMode,
    themeId: String,
    onClickColorMode: () -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("外观") },
    ) {
        item(
            onClick = onClickColorMode,
            leadingContent = { Icon(Icons.Outlined.DarkMode, null) },
            headlineContent = { Text("颜色模式") },
            supportingContent = {
                Text(
                    when (colorMode) {
                        ColorMode.SYSTEM -> "跟随系统"
                        ColorMode.LIGHT -> "浅色"
                        ColorMode.DARK -> "深色"
                    }
                )
            },
            trailingContent = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
        item(
            leadingContent = { Icon(Icons.Outlined.Palette, null) },
            headlineContent = { Text("主题") },
            supportingContent = {
                Text(
                    PresetThemes.find { it.id == themeId }?.displayName ?: themeId
                )
            },
        )
    }
}

@Composable
private fun NotificationSection(
    enabled: Boolean,
    pinned: Set<Platform>,
    configured: Set<Platform>,
    onToggleEnabled: (Boolean) -> Unit,
    onTogglePlatform: (Platform) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("状态栏通知") },
    ) {
        item(
            onClick = { onToggleEnabled(!enabled) },
            leadingContent = { Icon(Icons.Outlined.Notifications, null) },
            headlineContent = { Text("常驻通知") },
            supportingContent = { Text("在状态栏持续显示所选平台的用量或余额") },
            trailingContent = {
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggleEnabled,
                )
            },
        )

        if (enabled) {
            if (configured.isEmpty()) {
                item(
                    leadingContent = { Icon(Icons.Outlined.Info, null) },
                    headlineContent = { Text("暂无可固定的平台") },
                    supportingContent = { Text("请先登录账号或配置 API 密钥") },
                )
            } else {
                // 按 Platform 枚举顺序展示，保持与状态栏通知一致
                Platform.entries.filter { it in configured }.forEach { platform ->
                    val isPinned = platform in pinned
                    item(
                        onClick = { onTogglePlatform(platform) },
                        leadingContent = {
                            Icon(
                                Icons.Outlined.PushPin,
                                null,
                                tint = if (isPinned) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        headlineContent = { Text(platform.displayName) },
                        supportingContent = {
                            Text(if (isPinned) "已固定到状态栏" else "未固定")
                        },
                        trailingContent = {
                            Switch(
                                checked = isPinned,
                                onCheckedChange = { onTogglePlatform(platform) },
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AlertNotificationSection(
    usageAlertEnabled: Boolean,
    resetReminderEnabled: Boolean,
    sessionExpiredAlertEnabled: Boolean,
    onToggleUsageAlert: (Boolean) -> Unit,
    onToggleResetReminder: (Boolean) -> Unit,
    onToggleSessionExpiredAlert: (Boolean) -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("提醒通知") },
    ) {
        item(
            onClick = { onToggleUsageAlert(!usageAlertEnabled) },
            leadingContent = { Icon(Icons.Outlined.NotificationsActive, null) },
            headlineContent = { Text("用量阈值提醒") },
            supportingContent = { Text("用量超过 80% 时提醒，超过 95% 时强提醒") },
            trailingContent = {
                Switch(
                    checked = usageAlertEnabled,
                    onCheckedChange = onToggleUsageAlert,
                )
            },
        )
        item(
            onClick = { onToggleResetReminder(!resetReminderEnabled) },
            leadingContent = { Icon(Icons.Outlined.Autorenew, null) },
            headlineContent = { Text("额度重置提醒") },
            supportingContent = { Text("限额接近用尽后，额度重置时提醒") },
            trailingContent = {
                Switch(
                    checked = resetReminderEnabled,
                    onCheckedChange = onToggleResetReminder,
                )
            },
        )
        item(
            onClick = { onToggleSessionExpiredAlert(!sessionExpiredAlertEnabled) },
            leadingContent = { Icon(Icons.AutoMirrored.Outlined.Logout, null) },
            headlineContent = { Text("登录过期提醒") },
            supportingContent = { Text("网页平台登录失效时提醒重新登录") },
            trailingContent = {
                Switch(
                    checked = sessionExpiredAlertEnabled,
                    onCheckedChange = onToggleSessionExpiredAlert,
                )
            },
        )
    }
}

@Composable
private fun ThemePickerSection(
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    val brandConfig = LocalBrandConfig.current
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(brandConfig.cardCornerRadius))
            .background(MaterialTheme.colorScheme.surfaceBright)
            .padding(horizontal = 12.dp, vertical = 14.dp),
    ) {
        ThemePicker(
            selectedId = selectedId,
            onSelect = onSelect,
        )
    }
}

@Composable
private fun WebAccountSection(
    loggedIn: Set<Platform>,
    onClickPlatform: (String) -> Unit,
) {
    val platforms = Platform.entries.filter { !it.requiresApiKey }
    if (platforms.isEmpty()) return
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("账号管理") },
    ) {
        platforms.forEach { platform ->
            val hasAccount = loggedIn.contains(platform)
            item(
                onClick = { onClickPlatform(platform.key) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Login,
                        contentDescription = null,
                        tint = if (hasAccount) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = { Text(platform.displayName) },
                supportingContent = {
                    Text(
                        text = if (hasAccount) "已登录" else "未登录｜点击登录",
                        color = if (hasAccount) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    Icon(
                        Icons.Outlined.ChevronRight,
                        null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Composable
private fun ApiKeySection(
    configured: Set<Platform>,
    onClickPlatform: (Platform) -> Unit,
    onClearPlatform: (Platform) -> Unit,
) {
    val platforms = Platform.entries.filter { it.requiresApiKey }
    if (platforms.isEmpty()) return
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("API 密钥") },
    ) {
        platforms.forEach { platform ->
            val isConfigured = configured.contains(platform)
            item(
                onClick = { onClickPlatform(platform) },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Key,
                        contentDescription = null,
                        tint = if (isConfigured) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                headlineContent = { Text(platform.displayName) },
                supportingContent = {
                    Text(
                        text = if (isConfigured) "已配置｜点击修改" else "未配置｜点击添加",
                        color = if (isConfigured) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingContent = {
                    if (isConfigured) {
                        IconButton(onClick = { onClearPlatform(platform) }) {
                            Icon(
                                Icons.Outlined.Delete,
                                "清除",
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    } else {
                        Icon(
                            Icons.Outlined.ChevronRight,
                            null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun BackupSection(
    onClickExport: () -> Unit,
    onClickImport: () -> Unit,
) {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("数据备份") },
    ) {
        item(
            onClick = onClickExport,
            leadingContent = { Icon(Icons.Outlined.FileUpload, null) },
            headlineContent = { Text("导出备份") },
            supportingContent = { Text("将账号和密钥导出为 JSON 文件") },
            trailingContent = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        item(
            onClick = onClickImport,
            leadingContent = { Icon(Icons.Outlined.FileDownload, null) },
            headlineContent = { Text("导入备份") },
            supportingContent = { Text("从备份文件恢复账号和密钥") },
            trailingContent = {
                Icon(
                    Icons.Outlined.ChevronRight,
                    null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

@Composable
private fun AboutSection() {
    CardGroup(
        modifier = Modifier.padding(horizontal = 8.dp),
        title = { Text("关于") },
    ) {
        item(
            leadingContent = { Icon(Icons.Outlined.Info, null) },
            headlineContent = { Text("知余 v1.0.0") },
            supportingContent = { Text("所有数据仅存储在本设备") },
        )
        item(
            leadingContent = { Icon(Icons.Outlined.Backup, null) },
            headlineContent = { Text("使用提示") },
            supportingContent = { Text("登录账号或配置 API 密钥后即可在首页查看额度") },
        )
    }
}

@Composable
private fun ThemePicker(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(PresetThemes, key = { it.id }) { theme ->
            val isSelected = theme.id == selectedId
            val scheme = theme.standardLight
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .width(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onSelect(theme.id) }
                    .padding(vertical = 4.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .then(
                            if (isSelected) Modifier.border(
                                2.dp,
                                MaterialTheme.colorScheme.primary,
                                CircleShape,
                            ) else Modifier
                        )
                        .clip(CircleShape)
                ) {
                    Canvas(modifier = Modifier.size(32.dp)) {
                        drawCircle(color = scheme.primary)
                    }
                    if (isSelected) {
                        Icon(
                            Icons.Outlined.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = theme.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ColorModeDialog(
    current: ColorMode,
    onSelect: (ColorMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = listOf(
        ColorMode.SYSTEM to "跟随系统",
        ColorMode.LIGHT to "浅色",
        ColorMode.DARK to "深色",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("颜色模式") },
        text = {
            Column {
                options.forEach { (mode, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp, horizontal = 4.dp),
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ImportConfirmDialog(
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("导入备份") },
        text = {
            Text(
                "导入将合并备份文件中的账号和密钥，已有数据不会被删除。\n\n备份文件包含敏感凭据，请确保文件来源可信。",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("选择文件") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun ApiKeyDialog(
    state: ApiKeyDialogState,
    onDismiss: () -> Unit,
    onApiKeyChange: (String) -> Unit,
    onConfirm: () -> Unit,
) {
    val platform = state.platform
    val hint = when (platform) {
        Platform.MINIMAX ->
            "请使用 Token Plan 专属 API Key（在 MiniMax 开放平台「Token Plan」页面获取，与普通按量 API Key 不同）"
        Platform.AIHUBMIX ->
            "在 AIHubMix 控制台的「令牌」页面创建访问令牌"
        Platform.DEEPSEEK ->
            "在 DeepSeek 开放平台的「API Keys」页面创建"
        else -> "请粘贴您的 API 密钥"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${platform.displayName} API 配置") },
        text = {
            Column {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.apiKey,
                    onValueChange = onApiKeyChange,
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = state.apiKey.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
