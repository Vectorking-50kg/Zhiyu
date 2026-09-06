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
    // Shared across themes: time and usage fills use the same dimensions.
    val progressBarHeight: Dp = 10.dp,
    val progressBarCornerRadius: Dp = 5.dp,
    val useShadowElevation: Boolean = false,
    val buttonCornerRadius: Dp = 8.dp,
    val sectionTitleWeight: Int = 500,
)
