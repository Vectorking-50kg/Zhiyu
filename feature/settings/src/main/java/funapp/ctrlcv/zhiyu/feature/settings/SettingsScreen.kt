package funapp.ctrlcv.zhiyu.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.Platform

// Preset theme display info (id, displayName, primary light color, primary dark color)
private val PRESET_THEME_COLORS = listOf(
    Triple("zhiyu", "知余", Color(0xFF4A6FD4)),
    Triple("sakura", "樱花", Color(0xFF8E4955)),
    Triple("ocean", "海洋", Color(0xFF116682)),
    Triple("spring", "春绿", Color(0xFF4C662B)),
    Triple("autumn", "秋金", Color(0xFF735C0C)),
    Triple("black", "纯黑", Color(0xFF606060)),
)

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
                title = {
                    Text(
                        text = "设置",
                        style = MaterialTheme.typography.headlineSmall
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // ── 外观 ────────────────────────────────────────────────────
            SectionLabel("外观")

            ListItem(
                headlineContent = { Text("颜色模式") },
                supportingContent = {
                    Text(
                        text = when (uiState.colorMode) {
                            ColorMode.SYSTEM -> "跟随系统"
                            ColorMode.LIGHT -> "浅色"
                            ColorMode.DARK -> "深色"
                        },
                        color = MaterialTheme.colorScheme.primary
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { viewModel.showColorModeDialog() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("主题") },
                supportingContent = {
                    ThemePicker(
                        selectedId = uiState.themeId,
                        onSelect = { viewModel.setThemeId(it) }
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 账号管理（WebView 登录平台）───────────────────────────────
            SectionLabel("账号管理")

            val webLoginPlatforms = Platform.entries.filter { !it.requiresApiKey }
            webLoginPlatforms.forEachIndexed { index, platform ->
                val hasAccount = uiState.loggedInPlatforms.contains(platform)
                ListItem(
                    headlineContent = { Text(platform.displayName) },
                    supportingContent = {
                        Text(
                            text = if (hasAccount) "已登录" else "未登录｜点击登录",
                            color = if (hasAccount)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Login,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.Outlined.ChevronRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    modifier = Modifier.clickable { onNavigateToAuth(platform.key) }
                )
                if (index < webLoginPlatforms.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── API 密钥配置平台 ────────────────────────────────────────
            SectionLabel("API 密钥")

            val apiKeyPlatforms = Platform.entries.filter { it.requiresApiKey }
            apiKeyPlatforms.forEachIndexed { index, platform ->
                val isConfigured = uiState.configuredApiPlatforms.contains(platform)
                ListItem(
                    headlineContent = { Text(platform.displayName) },
                    supportingContent = {
                        Text(
                            text = if (isConfigured) "已配置｜点击修改" else "未配置｜点击添加 API 密钥",
                            color = if (isConfigured)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Outlined.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingContent = {
                        if (isConfigured) {
                            IconButton(onClick = { viewModel.clearApiKey(platform) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "清除",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    modifier = Modifier.clickable { viewModel.showApiKeyDialog(platform) }
                )
                if (index < apiKeyPlatforms.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ── 数据备份 ─────────────────────────────────────────────────
            SectionLabel("数据备份")

            ListItem(
                headlineContent = { Text("导出备份") },
                supportingContent = { Text("将账号和密钥导出为 JSON 文件") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.FileUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { viewModel.prepareExport() }
            )

            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

            ListItem(
                headlineContent = { Text("导入备份") },
                supportingContent = { Text("从备份文件恢复账号和密钥") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.FileDownload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                modifier = Modifier.clickable { viewModel.showImportConfirm() }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── 关于 ────────────────────────────────────────────────────
            SectionLabel("关于")

            ListItem(
                headlineContent = { Text("知余 v1.0.0") },
                supportingContent = { Text("所有数据仅存储在本设备") },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.height(innerPadding.calculateBottomPadding()))
        }
    }

    // ── 颜色模式选择弹窗 ─────────────────────────────────────────────────
    if (uiState.showColorModeDialog) {
        ColorModeDialog(
            current = uiState.colorMode,
            onSelect = { viewModel.setColorMode(it) },
            onDismiss = { viewModel.dismissColorModeDialog() }
        )
    }

    // ── API Key 配置弹窗 ─────────────────────────────────────────────────
    uiState.apiKeyDialog?.let { dialog ->
        ApiKeyDialog(
            state = dialog,
            onDismiss = { viewModel.dismissApiKeyDialog() },
            onApiKeyChange = { viewModel.updateApiKey(it) },
            onConfirm = { viewModel.saveApiKey() }
        )
    }

    // ── 导入确认弹窗 ─────────────────────────────────────────────────────
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
private fun ThemePicker(
    selectedId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PRESET_THEME_COLORS.forEach { (id, name, primaryColor) ->
            val isSelected = id == selectedId
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(40.dp)
                    .then(
                        if (isSelected) Modifier.border(
                            2.dp,
                            MaterialTheme.colorScheme.primary,
                            CircleShape
                        ) else Modifier
                    )
                    .clip(CircleShape)
                    .clickable { onSelect(id) }
            ) {
                Canvas(modifier = Modifier.size(40.dp)) {
                    drawCircle(color = primaryColor)
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = name,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
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
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp)
                    ) {
                        RadioButton(
                            selected = current == mode,
                            onClick = { onSelect(mode) }
                        )
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
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
    onConfirm: () -> Unit
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
    onConfirm: () -> Unit
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
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
                        imeAction = ImeAction.Done
                    )
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
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
    )
}
