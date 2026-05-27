package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem

@Composable
fun UsageCard(usageInfo: UsageInfo) {
    // null 表示没有百分比条目（如 DeepSeek 全余额展示），不在卡片右上角显示 xx%
    val maxPercent: Float? = usageInfo.items.filter { it.percent >= 0f }.maxOfOrNull { it.percent }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            CardHeader(platform = usageInfo.platform, maxPercent = maxPercent)

            if (usageInfo.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    usageInfo.items.forEach { item ->
                        if (item.percent >= 0f) {
                            ProgressItem(item = item)
                        } else {
                            InfoItem(item = item)
                        }
                    }
                }
            }

            usageInfo.resetInfo?.let { resetInfo ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = resetInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CardHeader(platform: Platform, maxPercent: Float?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getPlatformIconBg(platform)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getPlatformIconText(platform),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Black,
                    color = getPlatformIconColor(platform)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                SuggestionChip(
                    onClick = {},
                    label = {
                        Text(
                            text = getPlanLabel(platform),
                            style = MaterialTheme.typography.labelSmall
                        )
                    },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = getPlatformChipBg(platform),
                        labelColor = getPlatformChipColor(platform)
                    ),
                    border = null
                )
            }
        }

        maxPercent?.let {
            Text(
                text = "${it.toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = getSemanticColor(it)
            )
        }
    }
}

@Composable
private fun ProgressItem(item: UsageItem) {
    val isDanger = item.percent >= 90f
    val infiniteTransition = rememberInfiniteTransition(label = "breathe")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isDanger) 0.4f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breatheAlpha"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (item.usageCount != null && item.totalCount != null) {
                    "用量: ${item.usageCount}/${item.totalCount}  已使用 ${item.percent.toInt()}%"
                } else {
                    buildString {
                        append("${item.percent.toInt()}%")
                        item.resetCountdown?.let { append(" · $it") }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { (item.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .alpha(if (isDanger) alpha else 1f),
            color = getSemanticColor(item.percent),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        if (item.timeRange != null || (item.usageCount != null && item.resetCountdown != null)) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.timeRange ?: "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
                item.resetCountdown?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            item.valueText?.let { value ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 纯信息行：用于余额、次数等不需要进度条的展示（percent == -1f） */
@Composable
private fun InfoItem(item: UsageItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = item.valueText ?: "--",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

fun getSemanticColor(percent: Float): Color = when {
    percent < 80f -> Color(0xFF22C55E)
    percent < 90f -> Color(0xFFEAB308)
    else -> Color(0xFFEF4444)
}

private fun getPlatformIconBg(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> Color(0xFFFFFFFF)
    Platform.CHATGPT -> Color(0xFF10A37F)
    Platform.CURSOR -> Color(0xFF3D72E1)
    Platform.MINIMAX -> Color(0xFF1A1A2E)
    Platform.AIHUBMIX -> Color(0xFF6C47FF)
    Platform.DEEPSEEK -> Color(0xFF4D6BFE)
}

private fun getPlatformIconColor(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> Color(0xFF1A1A1A)
    Platform.CHATGPT -> Color.White
    Platform.CURSOR -> Color.White
    Platform.MINIMAX -> Color.White
    Platform.AIHUBMIX -> Color.White
    Platform.DEEPSEEK -> Color.White
}

private fun getPlatformIconText(platform: Platform): String = when (platform) {
    Platform.CLAUDE -> "AI\\"
    Platform.CHATGPT -> "GP"
    Platform.CURSOR -> "Cu"
    Platform.MINIMAX -> "MM"
    Platform.AIHUBMIX -> "AH"
    Platform.DEEPSEEK -> "DS"
}

@Composable
private fun getPlatformChipBg(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> MaterialTheme.colorScheme.surfaceVariant
    Platform.CHATGPT -> Color(0xFF10A37F).copy(alpha = 0.12f)
    Platform.CURSOR -> Color(0xFF3D72E1).copy(alpha = 0.12f)
    Platform.MINIMAX -> Color(0xFF1A1A2E).copy(alpha = 0.12f)
    Platform.AIHUBMIX -> Color(0xFF6C47FF).copy(alpha = 0.12f)
    Platform.DEEPSEEK -> Color(0xFF4D6BFE).copy(alpha = 0.12f)
}

@Composable
private fun getPlatformChipColor(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> MaterialTheme.colorScheme.onSurfaceVariant
    Platform.CHATGPT -> Color(0xFF0D8C6B)
    Platform.CURSOR -> Color(0xFF2B5DC4)
    Platform.MINIMAX -> Color(0xFF1A1A2E)
    Platform.AIHUBMIX -> Color(0xFF5535D4)
    Platform.DEEPSEEK -> Color(0xFF3A52D4)
}

private fun getPlanLabel(platform: Platform): String = when (platform) {
    Platform.CLAUDE -> "Pro"
    Platform.CHATGPT -> "Plus"
    Platform.CURSOR -> "Pro"
    Platform.MINIMAX -> "API"
    Platform.AIHUBMIX -> "API"
    Platform.DEEPSEEK -> "API"
}
