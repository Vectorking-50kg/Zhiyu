package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.BuildConfig
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette

fun ColorMode.label() = when (this) { ColorMode.SYSTEM -> "跟随系统"; ColorMode.LIGHT -> "浅色"; ColorMode.DARK -> "深色" }

@Composable
fun MonitorSettingsScreen(state: MonitorState, vm: MonitorViewModel, scroll: ScrollState, bottomPadding: Dp) {
    val c = LocalMonitorPalette.current
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = bottomPadding)) {
        PageTitle("设置", "应用偏好与数据管理")
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 21.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp)) {
            Box(Modifier.size(54.dp).background(c.primary, RoundedCornerShape(17.dp)), contentAlignment = Alignment.Center) {
                UiText("知", 30, 45, color = c.onPrimary)
            }
            Column { UiText("知余", 20, 28, 600); Spacer(Modifier.height(4.dp)); UiText("让每一份 AI 额度，都心中有数。", 11, 17, color = c.muted) }
        }
        Spacer(Modifier.height(22.dp)); SectionCaption("应用偏好"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("颜色模式", icon = AppIcons.DarkMode, value = state.colorMode.label(), onClick = { vm.showPanel(PanelKind.APPEARANCE) }); SettingDivider()
            SettingRow("通知与提醒", first = false, icon = AppIcons.Notifications, value = if (state.notifications) "已开启" else "已关闭", onClick = { vm.showPanel(PanelKind.NOTIFICATIONS) }); SettingDivider()
            SettingRow("刷新策略", first = false, icon = AppIcons.Schedule, value = "${state.refreshMinutes} 分钟", onClick = { vm.showPanel(PanelKind.REFRESH) })
        }
        Spacer(Modifier.height(22.dp)); SectionCaption("数据与应用"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("数据与隐私", icon = AppIcons.Shield, onClick = { vm.showPanel(PanelKind.PRIVACY) }); SettingDivider()
            SettingRow("关于知余", first = false, icon = AppIcons.Info, value = "v${BuildConfig.VERSION_NAME}", onClick = { vm.showPanel(PanelKind.ABOUT) })
        }
        Spacer(Modifier.height(22.dp)); SectionCaption("账户连接"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("管理账户", "登录、API 密钥、概览显示与单账户提醒", AppIcons.Group,
                "${state.accounts.size} 个", onClick = { vm.selectPage(MonitorPage.ACCOUNTS) })
        }
        Spacer(Modifier.height(25.dp)); UiText("你的账户，你的掌控。\n知余 · AI 额度与余额监控", 10, 19,
            color = c.subtle, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
