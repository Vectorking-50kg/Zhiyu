package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorStyle

@Composable
fun AccountEditorScreen(state: MonitorState, vm: MonitorViewModel, requestNotifications: (() -> Unit) -> Unit) {
    val draft = state.editor ?: return
    val c = LocalMonitorPalette.current
    val row = state.accounts.firstOrNull { it.platform == draft.platform && it.account.id == draft.accountId }
    val adding = row == null
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
        .padding(top = 8.dp, bottom = (WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + 20.dp).coerceAtLeast(44.dp))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(51.dp), verticalAlignment = Alignment.CenterVertically) {
            IconAction(AppIcons.ArrowBack, "返回", vm::closeEditor)
            Spacer(Modifier.width(5.dp)); UiText(if (adding) "添加监控" else "账户设置", 16, 24, 500, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.padding(horizontal = LocalMonitorStyle.current.pagePadding)) {
        ProviderHero(draft.platform, row)
        Spacer(Modifier.height(22.dp))
        FormInput("账户名称", draft.name, { value -> vm.updateDraft { it.copy(name = value.take(32)) } }, "给这个账户起个名字")
        Spacer(Modifier.height(18.dp))
        if (draft.usesCredentialForm) {
            if (draft.platform == Platform.DEVIN) {
                FormInput("Devin 组织 ID", draft.organizationId,
                    { value -> vm.updateDraft { it.copy(organizationId = value) } }, "org_…",
                    hint = "填写额度请求的 x-cog-org-id 值。")
                Spacer(Modifier.height(18.dp))
            }
            val label = draft.platform.manualTokenLabel
                ?: if (draft.platform == Platform.MINIMAX) "Token Plan API Key" else "API Key"
            FormInput(label,
                draft.credential, { value -> vm.updateDraft { it.copy(credential = value) } },
                if (adding) "请输入 ${draft.platform.manualTokenLabel ?: "API Key"}" else "留空则保留当前凭据",
                password = true,
                hint = when (draft.platform) {
                    Platform.MINIMAX -> "使用 Token Plan 专属密钥，与普通按量 API Key 不同。"
                    Platform.OPENCODE_GO -> "在 OpenCode 控制台开通 Go 后，复制对应账户的 API Key。Go 订阅额度与 Zen 账户余额可分别添加。"
                    Platform.DEVIN, Platform.GITHUB_COPILOT -> null
                    else -> "密钥仅用于连接当前供应商。"
                })
        } else {
            UiText("连接方式", 11, 17, 500, c.muted); Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(13.dp)).background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(13.dp)).padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.Language, null, size = 18.dp, tint = c.muted)
                Spacer(Modifier.width(9.dp))
                val method = when (draft.platform) {
                    Platform.DEVIN -> "通过 Devin 网页登录"
                    Platform.GITHUB_COPILOT -> "GitHub 设备码授权"
                    Platform.ANTIGRAVITY -> "Google 账号授权"
                    else -> "官方网页登录"
                }
                UiText(method, 12, 18, modifier = Modifier.weight(1f))
                if (!adding) SmallAction("重新连接", vm::authorize)
            }
        }
        if (draft.platform.manualTokenLabel != null) {
            Spacer(Modifier.height(8.dp))
            TextAction(if (draft.manualTokenEntry) "使用${draft.platform.connectionMethod()}" else "手动输入令牌", {
                vm.updateDraft { it.copy(manualTokenEntry = !it.manualTokenEntry, credential = "") }
            }, Modifier.height(40.dp), c.muted)
        }
        Spacer(Modifier.height(22.dp)); SectionCaption("显示与提醒"); Spacer(Modifier.height(11.dp))
        SettingGroup {
            SettingRow("在概览显示", "保留在额度与余额总览中", trailing = {
                UiSwitch("在概览显示", draft.visible) { checked -> vm.updateDraft { it.copy(visible = checked) } }
            }); SettingDivider()
            SettingRow("开启监控", "允许同步这个账户的用量", trailing = {
                UiSwitch("开启监控", draft.monitoring) { checked -> vm.updateDraft { it.copy(monitoring = checked) } }
            }); SettingDivider()
            val balance = draft.platform.isBalanceProvider()
            SettingRow(if (balance) "余额提醒" else "用量提醒", if (balance) "余额用尽时通知我" else "额度接近用尽时通知我", trailing = {
                UiSwitch(if (balance) "余额提醒" else "用量提醒", draft.alerts) { checked -> vm.updateDraft { it.copy(alerts = checked) } }
            }); SettingDivider()
            SettingRow("固定到状态栏", "在常驻通知中显示", trailing = {
                UiSwitch("固定到状态栏", draft.pinned) { checked ->
                    if (checked) requestNotifications { vm.updateDraft { it.copy(pinned = true) } }
                    else vm.updateDraft { it.copy(pinned = false) }
                }
            })
        }
        state.formError?.let { Spacer(Modifier.height(8.dp)); UiText(it, 11, 19, color = c.red) }
        Spacer(Modifier.height(22.dp))
        UiButton(if (state.saving) "正在验证…" else if (adding) if (draft.usesCredentialForm) "验证并添加" else "连接账户" else "保存设置",
            vm::saveEditor, enabled = !state.saving)
        Spacer(Modifier.height(13.dp))
        UiText("连接凭据加密保存在本设备", 10, 19, color = c.subtle, align = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (!adding) { Spacer(Modifier.height(12.dp)); TextAction("移除监控", { vm.showPanel(PanelKind.REMOVE, draft.platform, draft.accountId) }, Modifier.fillMaxWidth().height(44.dp), c.red) }
        }
    }
}

@Composable
fun ProviderHero(platform: Platform, row: MonitoredAccount? = null) {
    val c = LocalMonitorPalette.current
    val shape = LocalMonitorStyle.current.cardShape
    Column(Modifier.fillMaxWidth().clip(shape).background(c.surface)
        .border(1.dp, c.line, shape).padding(21.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        ProviderLogo(platform, 46.dp); Spacer(Modifier.height(10.dp))
        UiText(platform.displayName, 22, 30, 600, tracking = -.5f); Spacer(Modifier.height(4.dp))
        UiText(platform.monitorDescription(), 12, 20, color = c.muted, align = TextAlign.Center); Spacer(Modifier.height(12.dp))
        if (row != null) StatusBadge(row.status, row.attention, row.paused, compact = false)
        else Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.soft).padding(horizontal = 8.dp, vertical = 4.dp)) {
            UiText("通过${platform.connectionMethod()}连接", 10, 16, 500, c.muted)
        }
    }
}

@Composable
fun SmallAction(label: String, onClick: () -> Unit) {
    val c = LocalMonitorPalette.current
    androidx.compose.foundation.layout.Box(Modifier.clip(RoundedCornerShape(8.dp)).background(c.soft)
        .border(1.dp, c.line, RoundedCornerShape(8.dp)).then(Modifier), contentAlignment = Alignment.Center) {
        TextAction(label, onClick, Modifier.height(32.dp).padding(horizontal = 4.dp), c.text, 11)
    }
}
