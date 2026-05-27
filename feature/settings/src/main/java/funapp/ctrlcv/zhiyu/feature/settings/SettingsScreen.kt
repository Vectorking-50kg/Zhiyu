package funapp.ctrlcv.zhiyu.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import funapp.ctrlcv.zhiyu.core.domain.model.Platform

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateToAuth: (String) -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

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
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = innerPadding.calculateTopPadding())
        ) {
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

    // ── API Key 配置弹窗 ─────────────────────────────────────────────────
    uiState.apiKeyDialog?.let { dialog ->
        ApiKeyDialog(
            state = dialog,
            onDismiss = { viewModel.dismissApiKeyDialog() },
            onApiKeyChange = { viewModel.updateApiKey(it) },
            onConfirm = { viewModel.saveApiKey() }
        )
    }
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
