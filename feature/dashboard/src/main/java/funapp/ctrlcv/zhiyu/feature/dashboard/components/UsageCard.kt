package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
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

            usageInfo.resetCredits?.let { resetCredits ->
                if (resetCredits.availableCount > 0 || resetCredits.credits.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(10.dp))
                    ResetCreditsSection(resetCredits = resetCredits)
                }
            }

            usageInfo.resetInfo?.let { resetInfo ->
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(
                        icon = AppIcons.CalendarMonth,
                        contentDescription = null,
                        size = 16.dp,
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

        UsageProgressBar(
            item = item,
            height = brandConfig.progressBarHeight,
            cornerRadius = brandConfig.progressBarCornerRadius,
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

/**
 * 所有主题共用的用量进度条。时间层与用量层同高、同圆角、同起点，仅长度不同；
 * 用量层覆盖在时间层上方。缺少时间进度或无限额度时只显示用量层。
 */
@Composable
private fun UsageProgressBar(item: UsageItem, height: Dp, cornerRadius: Dp) {
    val usagePercent = if (item.unlimited) 100f
        else item.percent.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    val timePercent = item.elapsedPercent
        ?.takeIf { !item.unlimited && it.isFinite() }
        ?.coerceIn(0f, 100f)
    val shape = RoundedCornerShape(cornerRadius)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val usageColor = if (item.unlimited) MaterialTheme.colorScheme.primary
        else getSemanticColor(usagePercent)
    // Match the approved HTML: 45% usage color + 55% white, blended in sRGB.
    val timeColor = Color(
        red = usageColor.red * 0.45f + 0.55f,
        green = usageColor.green * 0.45f + 0.55f,
        blue = usageColor.blue * 0.45f + 0.55f,
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
            .clip(shape)
            .background(trackColor)
            .semantics {
                contentDescription = item.label
                progressBarRangeInfo = ProgressBarRangeInfo(usagePercent / 100f, 0f..1f)
                stateDescription = when {
                    item.unlimited -> "无限制"
                    timePercent != null -> "用量 ${usagePercent.toInt()}%，时间已过 ${timePercent.toInt()}%"
                    else -> "用量 ${usagePercent.toInt()}%"
                }
            }
    ) {
        timePercent?.let { elapsed ->
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = elapsed / 100f)
                    .clip(shape)
                    .background(timeColor)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = usagePercent / 100f)
                .clip(shape)
                .background(usageColor)
        )
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
    Platform.CHATGPT -> R.drawable.ic_brand_chatgpt
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
    Platform.ZEN -> "Zen"
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
