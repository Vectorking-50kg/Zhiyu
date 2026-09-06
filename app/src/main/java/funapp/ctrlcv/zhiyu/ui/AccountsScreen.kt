package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette

@Composable
fun AccountsScreen(state: MonitorState, vm: MonitorViewModel, scroll: ScrollState, bottomPadding: Dp) {
    val c = LocalMonitorPalette.current
    val all = state.accounts
    val listed = all.filter { matchesProvider(it.platform, state.search) || it.account.displayName.contains(state.search, true) }
    val supported = Platform.displayOrder.filter { matchesProvider(it, state.search) }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = bottomPadding)) {
        PageTitle("账户", "统一管理你的 AI 连接与监控") { IconAction(AppIcons.Add, "添加监控", vm::showProviders) }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(16.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(horizontal = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf(all.size to "已添加", all.count { !it.attention && !it.paused } to "连接正常", all.count { it.attention } to "待处理")
                .forEachIndexed { index, (number, label) ->
                    if (index > 0) Box(Modifier.width(1.dp).height(24.dp).background(c.line))
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        UiText(number.toString(), 22, 27, 600, if (index == 2 && number > 0) c.amber else c.text)
                        UiText(label, 10, 21, color = c.muted)
                    }
                }
        }
        Spacer(Modifier.height(19.dp))
        FilterTabs(listOf("已添加 ${all.size}", "全部支持 ${Platform.displayOrder.size}"), state.accountTab, vm::setAccountTab)
        Spacer(Modifier.height(19.dp)); SearchInput(state.search, vm::setSearch, "搜索供应商或账户")
        if (state.accountTab == 1) {
            Spacer(Modifier.height(22.dp)); SectionCaption("支持的 AI 供应商", "${supported.size} 个"); Spacer(Modifier.height(11.dp))
            if (supported.isEmpty()) EmptySearch()
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                supported.forEach { p -> AccountRow(p, all.firstOrNull { it.platform == p }, { vm.configure(p) }) }
            }
        } else if (listed.isEmpty()) {
            Spacer(Modifier.height(22.dp))
            AccountEmptyState("没有找到相关账户", "试试供应商名称，或查看全部支持的 AI。",
                if (state.search.isBlank()) "添加第一个监控" else null, vm::showProviders)
        } else {
            listOf(false to "网页登录", true to "API 密钥").forEach { (api, title) ->
                val group = listed.filter { it.platform.requiresApiKey == api }
                if (group.isNotEmpty()) {
                    Spacer(Modifier.height(22.dp)); SectionCaption(title, "${group.size} 个账户"); Spacer(Modifier.height(11.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                        group.forEach { row -> AccountRow(row.platform, row, { vm.configure(row.platform, row.account.id) }) }
                    }
                }
            }
        }
        Spacer(Modifier.height(19.dp))
        Row(Modifier.padding(horizontal = 5.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            AppIcon(AppIcons.Shield, null, size = 15.dp, tint = c.muted, modifier = Modifier.padding(top = 2.dp))
            UiText("在账户详情中配置连接方式、概览显示与提醒。\n登录信息由你掌控。", 10, 19, color = c.muted)
        }
    }
}

fun matchesProvider(platform: Platform, query: String): Boolean {
    val text = "${platform.displayName} ${platform.monitorDescription()} ${if (platform == Platform.CHATGPT) "OpenAI" else if (platform == Platform.CLAUDE) "Anthropic" else ""}"
    return text.contains(query.trim(), ignoreCase = true)
}

@Composable
fun AccountRow(platform: Platform, row: MonitoredAccount?, onClick: () -> Unit, compact: Boolean = false) {
    val c = LocalMonitorPalette.current
    val shape = RoundedCornerShape(if (compact) 12.dp else 16.dp)
    Row(Modifier.fillMaxWidth().height(if (compact) 70.dp else 84.dp).clip(shape).background(c.surface)
        .border(1.dp, c.line, shape).clickable(role = Role.Button, onClick = onClick)
        .padding(if (compact) 13.dp else 16.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        ProviderLogo(platform, if (compact) 30.dp else 34.dp)
        Column(Modifier.weight(1f)) {
            UiText(platform.displayName, if (compact) 13 else 14, 23, 600, tracking = -.35f)
            Spacer(Modifier.height(2.dp))
            UiText(row?.let { it.account.displayName + if (it.visible) "" else " · 概览隐藏" }
                ?: "${if (platform.requiresApiKey) "API 密钥" else "网页登录"} · ${platform.monitorDescription()}",
                if (compact) 10 else 11, 16, color = c.muted, maxLines = 1)
        }
        if (row != null) StatusBadge(row.status, row.attention, row.paused)
        else UiText("添加", 11, 17, 500)
        AppIcon(if (row == null) AppIcons.Add else AppIcons.ChevronRight, null, size = 17.dp, tint = c.subtle)
    }
}

@Composable
private fun EmptySearch() {
    AccountEmptyState("没有找到相关账户", "试试供应商名称，或查看全部支持的 AI。")
}

@Composable
fun ProviderPicker(state: MonitorState, vm: MonitorViewModel) {
    val listed = Platform.displayOrder.filter { matchesProvider(it, state.providerSearch) &&
        (state.providerFilter == 0 || (state.providerFilter == 2) == it.requiresApiKey) }
    Spacer(Modifier.height(3.dp)); SearchInput(state.providerSearch, vm::setProviderSearch, "搜索供应商")
    Spacer(Modifier.height(15.dp)); FilterTabs(listOf("全部", "网页登录", "API 密钥"), state.providerFilter, vm::setProviderFilter)
    val connected = state.accounts.map { it.platform }.toSet()
    val groups = if (Platform.CHATGPT in listed && Platform.CHATGPT in connected) listOf(true, false) else listOf(false, true)
    groups.forEach { isConnected ->
        val group = listed.filter { (it in connected) == isConnected }
        if (group.isNotEmpty()) {
            Spacer(Modifier.height(18.dp)); SectionCaption(if (isConnected) "已添加 · 点击配置" else "尚未添加", "${group.size}"); Spacer(Modifier.height(11.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                group.forEach { p -> AccountRow(p, state.accounts.firstOrNull { it.platform == p }, { vm.configure(p) }, compact = true) }
            }
        }
    }
    if (listed.isEmpty()) AccountEmptyState("没有找到这个供应商", "目前支持列表中的 7 个供应商，试试其他名称。")
}
