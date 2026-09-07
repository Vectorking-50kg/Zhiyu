package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.BuildConfig
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorStyle
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle

fun ColorMode.label() = when (this) { ColorMode.SYSTEM -> "跟随系统"; ColorMode.LIGHT -> "浅色"; ColorMode.DARK -> "深色" }

@Composable
fun MonitorSettingsScreen(state: MonitorState, vm: MonitorViewModel, scroll: ScrollState, bottomPadding: Dp, onAppearance: () -> Unit) {
    val pagePadding = LocalMonitorStyle.current.pagePadding
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = pagePadding, end = pagePadding, top = 21.dp, bottom = bottomPadding)) {
        PageTitle("设置")
        Spacer(Modifier.height(22.dp)); SectionCaption("应用偏好"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("主题与外观", "风格、主题色、底栏与界面缩放", icon = AppIcons.Palette,
                value = if (LocalAppearanceSettings.current.uiStyle == UiStyle.MIUIX) "Miuix" else "Material", onClick = onAppearance); SettingDivider()
            SettingRow("通知与提醒", first = false, icon = AppIcons.Notifications, value = if (state.notifications) "已开启" else "已关闭", onClick = { vm.showPanel(PanelKind.NOTIFICATIONS) }); SettingDivider()
            SettingRow("刷新策略", first = false, icon = AppIcons.Schedule, value = "${state.refreshMinutes} 分钟", onClick = { vm.showPanel(PanelKind.REFRESH) })
        }
        Spacer(Modifier.height(22.dp)); SectionCaption("数据与应用"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("数据与隐私", icon = AppIcons.Shield, onClick = { vm.showPanel(PanelKind.PRIVACY) }); SettingDivider()
            SettingRow("关于", first = false, icon = AppIcons.Info, value = "v${BuildConfig.VERSION_NAME}", onClick = { vm.showPanel(PanelKind.ABOUT) })
        }
    }
}
