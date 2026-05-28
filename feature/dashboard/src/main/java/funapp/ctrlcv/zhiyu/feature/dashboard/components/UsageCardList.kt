package funapp.ctrlcv.zhiyu.feature.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
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
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo

@Composable
fun UsageCardList(usageInfo: UsageInfo) {
    val maxPercent = usageInfo.items.filter { it.percent >= 0f }.maxOfOrNull { it.percent }
    val balanceText = when (usageInfo.platform) {
        Platform.AIHUBMIX -> usageInfo.items.firstOrNull { it.label == "余额" }?.valueText?.let { formatBalance(it) }
        Platform.DEEPSEEK -> usageInfo.items.firstOrNull { it.label == "账户余额" }?.valueText?.let { formatBalance(it) }
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFFBDB0A4))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(getPlatformIconBg(usageInfo.platform)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = getPlatformIconRes(usageInfo.platform)),
                        contentDescription = usageInfo.platform.displayName,
                        modifier = Modifier.size(if (usageInfo.platform == Platform.AIHUBMIX) 22.dp else 18.dp),
                        tint = if (usageInfo.platform == Platform.AIHUBMIX) Color.Unspecified else Color.White
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = usageInfo.platform.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            when {
                balanceText != null -> Text(
                    text = balanceText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                maxPercent != null -> Text(
                    text = "${maxPercent.toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = getSemanticColor(maxPercent)
                )
            }
        }
    }
}
