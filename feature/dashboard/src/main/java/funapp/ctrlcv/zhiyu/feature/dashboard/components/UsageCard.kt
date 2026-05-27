package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.feature.dashboard.R
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem

@Composable
fun UsageCard(usageInfo: UsageInfo) {
    val normalItems = usageInfo.items.filter { !it.collapsible }
    val collapsibleItems = usageInfo.items.filter { it.collapsible }
    val maxPercent: Float? = normalItems.filter { it.percent >= 0f }.maxOfOrNull { it.percent }
        ?: usageInfo.items.filter { it.percent >= 0f }.maxOfOrNull { it.percent }
    val balanceText: String? = when (usageInfo.platform) {
        Platform.AIHUBMIX -> usageInfo.items.firstOrNull { it.label == "余额" }?.valueText?.let { formatBalance(it) }
        Platform.DEEPSEEK -> usageInfo.items.firstOrNull { it.label == "账户余额" }?.valueText?.let { formatBalance(it) }
        else -> null
    }
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(
            width = 0.5.dp,
            color = Color(0xFFBDB0A4)
        )
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            CardHeader(usageInfo = usageInfo, maxPercent = maxPercent, balanceText = balanceText)

            if (normalItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    normalItems.forEach { item ->
                        if (item.percent >= 0f) ProgressItem(item = item) else InfoItem(item = item)
                    }
                }
            }

            if (collapsibleItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { expanded = !expanded }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "其它配额",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = if (expanded) "收起" else "展开",
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (expanded) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        collapsibleItems.forEach { item ->
                            ProgressItem(item = item)
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
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
                        modifier = Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(5.dp))
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
private fun CardHeader(usageInfo: UsageInfo, maxPercent: Float?, balanceText: String? = null) {
    val platform = usageInfo.platform
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(getPlatformIconBg(platform)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = getPlatformIconRes(platform)),
                    contentDescription = platform.displayName,
                    modifier = Modifier.size(if (platform == Platform.AIHUBMIX) 28.dp else 22.dp),
                    tint = if (platform == Platform.AIHUBMIX) Color.Unspecified else Color.White
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = getPlanLabel(usageInfo),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .border(
                            width = 0.75.dp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(3.dp)
                        )
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        when {
            balanceText != null -> Text(
                text = balanceText,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            maxPercent != null -> Text(
                text = "${maxPercent.toInt()}%",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = getSemanticColor(maxPercent)
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
                    "${item.usageCount}/${item.totalCount}  ${item.percent.toInt()}%"
                } else {
                    buildString {
                        append("${item.percent.toInt()}%")
                        item.resetCountdown?.let { append(" · $it") }
                    }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        LinearProgressIndicator(
            progress = { (item.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(if (isDanger) alpha else 1f),
            color = getSemanticColor(item.percent),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {}
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
    percent < 80f -> Color(0xFF4A9D6F)
    percent < 90f -> Color(0xFFD4A027)
    else -> Color(0xFFD94F4F)
}

@DrawableRes
private fun getPlatformIconRes(platform: Platform): Int = when (platform) {
    Platform.CLAUDE -> R.drawable.ic_brand_anthropic
    Platform.CHATGPT -> R.drawable.ic_brand_openai
    Platform.CURSOR -> R.drawable.ic_brand_cursor
    Platform.MINIMAX -> R.drawable.ic_brand_minimax
    Platform.AIHUBMIX -> R.drawable.ic_brand_aihubmix
    Platform.DEEPSEEK -> R.drawable.ic_brand_deepseek
}

private fun getPlatformIconBg(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> Color(0xFFCC785C)
    Platform.CHATGPT -> Color(0xFF10A37F)
    Platform.CURSOR -> Color(0xFF1A1A1A)
    Platform.MINIMAX -> Color(0xFF2D2D2D)
    Platform.AIHUBMIX -> Color(0xFFF5F5F5)
    Platform.DEEPSEEK -> Color(0xFF4362D6)
}

private fun getPlanLabel(usageInfo: UsageInfo): String = when (usageInfo.platform) {
    Platform.CLAUDE -> "Pro"
    Platform.CHATGPT -> usageInfo.items.firstOrNull { it.label == "套餐类型" }?.valueText ?: "Plus"
    Platform.CURSOR -> usageInfo.items.firstOrNull { it.label == "会员类型" }?.valueText ?: "Pro"
    Platform.MINIMAX -> "Token Plan"
    Platform.AIHUBMIX -> "API"
    Platform.DEEPSEEK -> "API"
}

private fun formatBalance(valueText: String): String {
    val prefix = when {
        valueText.startsWith("$") -> "$"
        valueText.startsWith("¥") -> "¥"
        else -> ""
    }
    val num = valueText.removePrefix(prefix).toDoubleOrNull() ?: return valueText
    return "$prefix${String.format("%.2f", num)}"
}
