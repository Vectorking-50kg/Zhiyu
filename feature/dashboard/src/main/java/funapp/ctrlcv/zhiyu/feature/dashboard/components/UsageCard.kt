package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.feature.dashboard.R
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalBrandConfig

@Composable
fun UsageCard(usageInfo: UsageInfo) {
    val items = usageInfo.items
    val maxPercent: Float? = items.filter { it.percent >= 0f && !it.unlimited }.maxOfOrNull { it.percent }
    val balanceText: String? = when (usageInfo.platform) {
        Platform.AIHUBMIX -> usageInfo.items.firstOrNull { it.label == "余额" }?.valueText?.let { formatBalance(it) }
        Platform.DEEPSEEK, Platform.ZEN -> usageInfo.items.firstOrNull { it.label == "账户余额" }?.valueText?.let { formatBalance(it) }
        else -> null
    }
    val brandConfig = LocalBrandConfig.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (brandConfig.cardBorderWidth > 0.dp) Modifier.border(
                    width = brandConfig.cardBorderWidth,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = brandConfig.cardBorderAlpha),
                    shape = RoundedCornerShape(brandConfig.cardCornerRadius),
                ) else Modifier
            ),
        shape = RoundedCornerShape(brandConfig.cardCornerRadius),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceBright),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (brandConfig.useShadowElevation) 2.dp else 0.dp
        ),
    ) {
        Column(modifier = Modifier.padding(brandConfig.cardPadding)) {
            CardHeader(usageInfo = usageInfo, maxPercent = maxPercent, balanceText = balanceText)

            if (items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    items.forEach { item ->
                        if (item.percent >= 0f) ProgressItem(item = item) else InfoItem(item = item)
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
            Icon(
                painter = painterResource(id = getPlatformIconRes(platform)),
                contentDescription = platform.displayName,
                modifier = Modifier.size(32.dp),
                tint = Color.Unspecified
            )

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
    val brandConfig = LocalBrandConfig.current
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
                text = if (item.unlimited) {
                    "无限制"
                } else buildString {
                    append("${item.percent.toInt()}%")
                    item.resetCountdown?.let { append("｜$it") }
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = if (item.unlimited) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(7.dp))

        LinearProgressIndicator(
            progress = { if (item.unlimited) 1f else (item.percent / 100f).coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(brandConfig.progressBarHeight)
                .clip(RoundedCornerShape(brandConfig.progressBarCornerRadius)),
            color = if (item.unlimited) MaterialTheme.colorScheme.primary else getSemanticColor(item.percent),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {}
        )

        // boost 提升期间展示「总额度 X%」；其余平台沿用 valueText 作为补充说明
        val secondaryText: String? = item.boostPercent?.let { "总额度 $it%" } ?: item.valueText
        secondaryText?.let { value ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
    percent < 70f -> Color(0xFF4A9D6F)
    percent < 90f -> Color(0xFFD4A027)
    else -> Color(0xFFD94F4F)
}

@DrawableRes
internal fun getPlatformIconRes(platform: Platform): Int = when (platform) {
    Platform.CLAUDE -> R.drawable.ic_brand_anthropic
    Platform.CHATGPT -> R.drawable.ic_brand_openai
    Platform.CURSOR -> R.drawable.ic_brand_cursor
    Platform.ZEN -> R.drawable.ic_brand_opencode
    Platform.MINIMAX -> R.drawable.ic_brand_minimax
    Platform.AIHUBMIX -> R.drawable.ic_brand_aihubmix
    Platform.DEEPSEEK -> R.drawable.ic_brand_deepseek
}


internal fun getPlanLabel(usageInfo: UsageInfo): String = when (usageInfo.platform) {
    Platform.CLAUDE -> usageInfo.planLabel ?: "Unknown"
    Platform.CHATGPT -> usageInfo.planLabel ?: "Unknown"
    Platform.CURSOR -> usageInfo.planLabel ?: "Unknown"
    Platform.ZEN -> "按量计费"
    Platform.MINIMAX -> "Token Plan"
    Platform.AIHUBMIX -> "API"
    Platform.DEEPSEEK -> "API"
}

internal fun formatBalance(valueText: String): String {
    val prefix = when {
        valueText.startsWith("$") -> "$"
        valueText.startsWith("¥") -> "¥"
        else -> ""
    }
    val num = valueText.removePrefix(prefix).toDoubleOrNull() ?: return valueText
    return "$prefix${String.format("%.2f", num)}"
}
