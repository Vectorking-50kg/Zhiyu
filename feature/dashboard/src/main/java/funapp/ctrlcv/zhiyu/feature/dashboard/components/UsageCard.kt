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
import androidx.compose.ui.unit.sp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem

@Composable
fun UsageCard(usageInfo: UsageInfo) {
    val maxPercent = usageInfo.items.maxOfOrNull { it.percent } ?: 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header
            CardHeader(platform = usageInfo.platform, maxPercent = maxPercent)

            Spacer(modifier = Modifier.height(16.dp))

            // Progress items
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                usageInfo.items.forEach { item ->
                    ProgressItem(item = item)
                }
            }

            // Footer with reset info
            usageInfo.resetInfo?.let { resetInfo ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(12.dp))
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
private fun CardHeader(platform: Platform, maxPercent: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Platform icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(getPlatformIconBg(platform)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = getPlatformIconText(platform),
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    color = getPlatformIconColor(platform)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = platform.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(getPlatformTagBg(platform))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = getPlanLabel(platform),
                        fontSize = 10.sp,
                        color = getPlatformTagColor(platform)
                    )
                }
            }
        }

        Text(
            text = "${maxPercent.toInt()}%",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            color = getSemanticColor(maxPercent)
        )
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = buildString {
                    append("${item.percent.toInt()}%")
                    item.resetCountdown?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        LinearProgressIndicator(
            progress = { (item.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .alpha(if (isDanger) alpha else 1f),
            color = getSemanticColor(item.percent),
            trackColor = MaterialTheme.colorScheme.surfaceVariant
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
}

private fun getPlatformIconColor(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> Color(0xFF1A1A1A)
    Platform.CHATGPT -> Color.White
    Platform.CURSOR -> Color.White
}

private fun getPlatformIconText(platform: Platform): String = when (platform) {
    Platform.CLAUDE -> "AI\\"
    Platform.CHATGPT -> "GP"
    Platform.CURSOR -> "Cu"
}

@Composable
private fun getPlatformTagBg(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> MaterialTheme.colorScheme.surfaceVariant
    Platform.CHATGPT -> Color(0xFF10A37F).copy(alpha = 0.1f)
    Platform.CURSOR -> Color(0xFF3D72E1).copy(alpha = 0.1f)
}

@Composable
private fun getPlatformTagColor(platform: Platform): Color = when (platform) {
    Platform.CLAUDE -> MaterialTheme.colorScheme.onSurfaceVariant
    Platform.CHATGPT -> Color(0xFF10A37F)
    Platform.CURSOR -> Color(0xFF3D72E1)
}

private fun getPlanLabel(platform: Platform): String = when (platform) {
    Platform.CLAUDE -> "Pro"
    Platform.CHATGPT -> "Plus"
    Platform.CURSOR -> "Pro"
}
