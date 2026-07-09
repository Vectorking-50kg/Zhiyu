package funapp.ctrlcv.zhiyu.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class BrandThemeConfig(
    val cardCornerRadius: Dp = 20.dp,
    val cardInnerCornerRadius: Dp = 4.dp,
    val cardItemSpacing: Dp = 2.dp,
    val cardBorderWidth: Dp = 0.dp,
    val cardBorderAlpha: Float = 0f,
    val cardElevation: Dp = 0.dp,
    val cardPadding: Dp = 18.dp,
    val progressBarHeight: Dp = 10.dp,
    val progressBarCornerRadius: Dp = 5.dp,
    val useShadowElevation: Boolean = false,
    val buttonCornerRadius: Dp = 8.dp,
    val sectionTitleWeight: Int = 500,
    // 进度条是否以双段渲染：深色段=用量百分比，浅色段=窗口已过去的时间百分比。
    // 仅在 UsageItem.elapsedPercent 有值时生效，其余情况回退为单段进度条。
    val progressBarShowTimeSegment: Boolean = false,
)
