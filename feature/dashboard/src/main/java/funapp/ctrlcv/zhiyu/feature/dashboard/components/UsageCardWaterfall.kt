package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalBrandConfig

@Composable
fun UsageCardWaterfall(usageInfo: UsageInfo) {
    val maxPercent = usageInfo.items.filter { it.percent >= 0f }.maxOfOrNull { it.percent }
    val balanceText = when (usageInfo.platform) {
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
            // 上半部分：图标（左）+ 标题和套餐类型（右）
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(id = getPlatformIconRes(usageInfo.platform)),
                    contentDescription = usageInfo.platform.displayName,
                    modifier = Modifier.size(32.dp),
                    tint = Color.Unspecified
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier = Modifier.heightIn(min = 32.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = usageInfo.platform.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
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

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(12.dp))

            // 下半部分：数值居中
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when {
                    balanceText != null -> Text(
                        text = balanceText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    maxPercent != null -> Text(
                        text = "${maxPercent.toInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = getSemanticColor(maxPercent)
                    )
                }
            }

            usageInfo.resetCredits?.takeIf { it.availableCount > 0 }?.let { resetCredits ->
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "重置卡 ×${resetCredits.availableCount}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            }
        }
    }
}
