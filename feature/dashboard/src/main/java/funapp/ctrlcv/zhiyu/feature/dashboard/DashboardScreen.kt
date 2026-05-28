package funapp.ctrlcv.zhiyu.feature.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import funapp.ctrlcv.zhiyu.core.ui.theme.CustomColors
import funapp.ctrlcv.zhiyu.feature.dashboard.components.UsageCard
import funapp.ctrlcv.zhiyu.feature.dashboard.components.UsageCardList
import funapp.ctrlcv.zhiyu.feature.dashboard.components.UsageCardWaterfall

enum class LayoutMode { DETAILED, LIST, WATERFALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToAuth: (String) -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var layoutModeOrdinal by rememberSaveable { mutableIntStateOf(0) }
    val layoutMode = LayoutMode.values()[layoutModeOrdinal]

    val infiniteTransition = rememberInfiniteTransition(label = "refresh")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Scaffold(
        topBar = {
            DashboardTopBar(
                isRefreshing = uiState.isRefreshing,
                rotation = rotation,
                onRefresh = { viewModel.refresh() },
                layoutMode = layoutMode,
                onToggleLayout = {
                    layoutModeOrdinal = (layoutModeOrdinal + 1) % LayoutMode.values().size
                },
                scrollBehavior = scrollBehavior
            )
        },
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = CustomColors.topBarColors.containerColor
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = when (layoutMode) {
                LayoutMode.WATERFALL -> GridCells.Adaptive(minSize = 150.dp)
                else -> GridCells.Fixed(1)
            },
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(
                when (layoutMode) {
                    LayoutMode.LIST -> 6.dp
                    LayoutMode.WATERFALL -> 8.dp
                    else -> 10.dp
                }
            ),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.lastUpdated > 0) {
                item(key = "last_updated", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = "上次更新：${formatTimeSince(uiState.lastUpdated)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
                    )
                }
            }

            items(uiState.usageList, key = { "${it.platform.key}_${it.updatedAt}" }) { usage ->
                when (layoutMode) {
                    LayoutMode.DETAILED -> UsageCard(usageInfo = usage)
                    LayoutMode.LIST -> UsageCardList(usageInfo = usage)
                    LayoutMode.WATERFALL -> UsageCardWaterfall(usageInfo = usage)
                }
            }

            if (uiState.usageList.isEmpty() && !uiState.isRefreshing) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "暂无数据，请先在设置中登录平台账号",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (uiState.error != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = uiState.error.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }

    uiState.authRequired?.let { platform ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissAuthRequired() },
            title = { Text("${platform.displayName} 登录已失效") },
            text = { Text("无法获取使用数据，请重新登录 ${platform.displayName} 以继续同步额度。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissAuthRequired()
                    onNavigateToAuth(platform.key)
                }) { Text("去登录") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissAuthRequired() }) { Text("稍后") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DashboardTopBar(
    isRefreshing: Boolean,
    rotation: Float,
    onRefresh: () -> Unit,
    layoutMode: LayoutMode,
    onToggleLayout: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior
) {
    LargeTopAppBar(
        title = {
            Text(
                text = "知余",
                fontWeight = FontWeight.Bold,
            )
        },
        actions = {
            IconButton(onClick = onToggleLayout) {
                Icon(
                    imageVector = when (layoutMode) {
                        LayoutMode.DETAILED -> Icons.Outlined.ViewAgenda
                        LayoutMode.LIST -> Icons.Outlined.ViewList
                        LayoutMode.WATERFALL -> Icons.Outlined.GridView
                    },
                    contentDescription = when (layoutMode) {
                        LayoutMode.DETAILED -> "详细布局"
                        LayoutMode.LIST -> "列表布局"
                        LayoutMode.WATERFALL -> "瀑布流布局"
                    },
                )
            }
            IconButton(onClick = onRefresh) {
                Icon(
                    imageVector = Icons.Outlined.Refresh,
                    contentDescription = "刷新",
                    modifier = if (isRefreshing) Modifier.rotate(rotation) else Modifier,
                )
            }
        },
        colors = CustomColors.topBarColors,
        scrollBehavior = scrollBehavior
    )
}

private fun formatTimeSince(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    return when {
        minutes < 1 -> "刚刚"
        minutes < 60 -> "${minutes} 分钟前"
        else -> "${minutes / 60} 小时前"
    }
}
