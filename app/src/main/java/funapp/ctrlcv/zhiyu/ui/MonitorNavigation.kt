package funapp.ctrlcv.zhiyu.ui

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.ui.components.GlassBackdropState
import funapp.ctrlcv.zhiyu.core.ui.components.UiText
import funapp.ctrlcv.zhiyu.core.ui.components.glassBackdrop
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette

private val navigationItems = listOf(
    Triple(MonitorPage.OVERVIEW, "概览", AppIcons.GridView),
    Triple(MonitorPage.ACCOUNTS, "账户", AppIcons.Group),
    Triple(MonitorPage.SETTINGS, "设置", AppIcons.Settings),
)

@Composable
fun MonitorNavigation(
    page: MonitorPage, onSelect: (MonitorPage) -> Unit, onReselect: () -> Unit,
    backdrop: GlassBackdropState, modifier: Modifier = Modifier,
) {
    val c = LocalMonitorPalette.current
    val appearance = LocalAppearanceSettings.current
    val miuix = appearance.uiStyle == UiStyle.MIUIX
    if (appearance.bottomBarStyle == BottomBarStyle.FIXED && !miuix) {
        NavigationBar(modifier, containerColor = c.toolbar, tonalElevation = 0.dp, windowInsets = WindowInsets(0)) {
            navigationItems.forEach { (tab, label, icon) ->
                NavigationBarItem(selected = page == tab, onClick = { if (page == tab) onReselect() else onSelect(tab) },
                    icon = { AppIcon(icon, null, size = 24.dp) },
                    label = { UiText(label, 11, 16, if (page == tab) 600 else 400, if (page == tab) c.primary else c.muted) },
                    modifier = Modifier.semantics { contentDescription = label },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = c.primary, indicatorColor = c.indicator,
                        unselectedIconColor = c.muted, selectedTextColor = c.primary, unselectedTextColor = c.muted))
            }
        }
        return
    }

    val floating = appearance.bottomBarStyle != BottomBarStyle.FIXED
    val glass = appearance.bottomBarStyle == BottomBarStyle.GLASS && Build.VERSION.SDK_INT >= 31
    val shape = RoundedCornerShape(if (miuix || glass) 36.dp else 28.dp)
    val container = if (floating) modifier.width(if (miuix || glass) 264.dp else 240.dp)
        .shadow(if (glass) 8.dp else 2.dp, shape, ambientColor = Color(0x3017262A), spotColor = Color(0x3017262A))
        .then(if (glass) Modifier.glassBackdrop(backdrop, shape, c.toolbar.copy(alpha = .48f)) else Modifier.background(c.toolbar, shape))
    else modifier.fillMaxWidth().background(c.toolbar)

    BoxWithConstraints(container.heightIn(min = if (floating) 72.dp else 68.dp).selectableGroup()) {
        val horizontalPadding = if (floating) 8.dp else 16.dp
        val itemWidth = (maxWidth - horizontalPadding * 2) / navigationItems.size
        val selectedIndex = navigationItems.indexOfFirst { it.first == page }.coerceAtLeast(0)
        val selectedX by animateDpAsState(itemWidth * selectedIndex,
            spring(dampingRatio = .8f, stiffness = 420f), label = "navigation selection")
        if (floating && (miuix || glass)) {
            Box(Modifier.padding(start = horizontalPadding, top = 8.dp)
                .offset { IntOffset(selectedX.roundToPx(), 0) }.width(itemWidth).height(56.dp)
                .then(if (glass) Modifier.glassBackdrop(backdrop, RoundedCornerShape(28.dp), c.primary.copy(alpha = .10f))
                    else Modifier.background(c.indicator, RoundedCornerShape(28.dp))))
        }
        Row(Modifier.fillMaxWidth().heightIn(min = if (floating) 72.dp else 68.dp).padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically) {
            navigationItems.forEach { (tab, label, icon) ->
                val active = page == tab
                val interactions = remember { MutableInteractionSource() }
                val pressed by interactions.collectIsPressedAsState()
                val pressScale by animateFloatAsState(if (pressed) .94f else 1f, label = "navigation press")
                Column(Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(24.dp))
                    // Double-tap detection defers every single tap until its timeout expires.
                    .selectable(selected = active, interactionSource = interactions, indication = null, role = Role.Tab,
                        onClick = { if (active) onReselect() else onSelect(tab) })
                    .semantics { contentDescription = label }
                    .graphicsLayer { scaleX = pressScale; scaleY = pressScale },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(40.dp).background(
                        if (active && !miuix && !glass) c.indicator else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center) {
                        AppIcon(icon, null, size = 24.dp, tint = if (active) c.primary else c.muted)
                    }
                    UiText(label, 11, 16, if (active) 600 else 400, if (active) c.primary else c.muted)
                }
            }
        }
    }
}
