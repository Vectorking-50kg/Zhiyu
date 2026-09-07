package funapp.ctrlcv.zhiyu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import funapp.ctrlcv.zhiyu.core.ui.components.IconAction
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette

@Composable
fun RefreshAction(refreshing: Boolean, onRefresh: () -> Unit) {
    // The transition leaves composition as soon as the real refresh finishes (including errors).
    val rotation: State<Float>? = if (refreshing) {
        rememberInfiniteTransition(label = "account refresh").animateFloat(
            initialValue = 0f, targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(800, easing = LinearEasing), RepeatMode.Restart),
            label = "refresh rotation",
        )
    } else null
    val colors = LocalMonitorPalette.current
    IconAction(
        AppIcons.Refresh, "刷新所有账户", onRefresh,
        enabled = !refreshing, tint = if (refreshing) colors.primary else colors.muted,
        modifier = Modifier.semantics { stateDescription = if (refreshing) "正在刷新" else "可以刷新" },
        iconModifier = Modifier.graphicsLayer { rotationZ = rotation?.value ?: 0f },
    )
}
