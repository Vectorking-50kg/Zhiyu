package funapp.ctrlcv.zhiyu.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.*
import funapp.ctrlcv.zhiyu.core.ui.components.*
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import kotlin.math.ceil

@Composable
fun OverviewScreen(state: MonitorState, vm: MonitorViewModel, scroll: ScrollState, bottomPadding: Dp) {
    val c = LocalMonitorPalette.current
    if (state.accounts.isEmpty()) {
        WelcomeScreen(scroll, bottomPadding, vm::showProviders, {
            vm.setAccountTab(1); vm.selectPage(MonitorPage.ACCOUNTS)
        })
        return
    }
    val visible = state.accounts.filter { it.visible }
    val shown = visible.filter { state.homeFilter == 0 || (state.homeFilter == 2) == it.platform.isBalanceProvider() }
    val attention = state.accounts.filter { it.attention }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = bottomPadding)) {
        PageTitle("知余", "${if (state.refreshing) "正在同步账户…" else updatedLabel(state.lastUpdated, state.now)} · 额度与余额，一目了然", statusDot = true) {
            IconAction(AppIcons.Refresh, "刷新所有账户", vm::refresh, enabled = !state.refreshing)
        }
        Spacer(Modifier.height(22.dp))
        Row(Modifier.fillMaxWidth().height(if (attention.isNotEmpty()) 18.dp else 24.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText(state.accounts.size.toString(), 12, 18, 600)
            UiText(" 个账户已添加", 12, 18, color = c.muted, modifier = Modifier.weight(1f))
            if (attention.isNotEmpty()) Row(Modifier.clip(RoundedCornerShape(6.dp)).background(c.amberSoft)
                .clickable(role = Role.Button) {
                    vm.setAccountTab(0); vm.setSearch(if (attention.size == 1) attention.first().platform.displayName else "")
                    vm.selectPage(MonitorPage.ACCOUNTS)
                }, verticalAlignment = Alignment.CenterVertically) {
                UiText("${attention.size} 个账户待处理", 10, 18, 500, c.amber)
                AppIcon(AppIcons.ChevronRight, null, size = 12.dp, tint = c.amber)
            } else Box(Modifier.clip(RoundedCornerShape(6.dp)).background(c.greenSoft).padding(horizontal = 8.dp, vertical = 4.dp)) {
                UiText("全部连接正常", 10, 16, 500, c.green)
            }
        }
        Spacer(Modifier.height(15.dp))
        FilterTabs(listOf("全部 ${visible.size}", "套餐额度 ${visible.count { !it.platform.isBalanceProvider() }}", "账户余额 ${visible.count { it.platform.isBalanceProvider() }}"), state.homeFilter, vm::setHomeFilter)
        Spacer(Modifier.height(19.dp))
        if (shown.isEmpty()) EmptyOverview(vm::showProviders)
        else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            shown.forEach { row ->
                QuotaCard(row, state.now, { vm.showPanel(PanelKind.DETAIL, row.platform, row.account.id) }, { vm.configure(row.platform, row.account.id) })
            }
        }
        Spacer(Modifier.height(25.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            UiText("在", 11, 21, color = c.muted)
            UiText("账户页", 11, 21, color = c.text, decoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                modifier = Modifier.clickable(role = Role.Button) { vm.selectPage(MonitorPage.ACCOUNTS) })
            UiText("管理显示与连接", 11, 21, color = c.muted)
        }
    }
}

fun updatedLabel(updated: Long, now: Long): String = when {
    updated <= 0 -> "尚未同步"
    now - updated < 60_000 -> "刚刚更新"
    now - updated < 3_600_000 -> "${(now - updated) / 60_000} 分钟前更新"
    else -> "${(now - updated) / 3_600_000} 小时前更新"
}

fun planLabel(row: MonitoredAccount): String = when (row.platform) {
    Platform.ZEN -> "Zen"
    Platform.MINIMAX -> "Token Plan"
    Platform.AIHUBMIX, Platform.DEEPSEEK -> "API"
    else -> row.usage?.planLabel?.takeIf { it.isNotBlank() } ?: row.account.planType.ifBlank { "账户" }
}

fun mainReading(row: MonitoredAccount): String {
    if (row.platform.isBalanceProvider()) {
        val text = row.usage?.items?.firstOrNull { it.label == "余额" || it.label == "账户余额" }?.valueText ?: return "--"
        val prefix = text.takeWhile { !it.isDigit() && it != '-' }
        val amount = text.removePrefix(prefix).replace(",", "").toDoubleOrNull()
        return if (amount != null && amount.isFinite()) "$prefix${"%.2f".format(java.util.Locale.US, amount)}" else text
    }
    return row.usage?.items?.filter { it.percent >= 0 && !it.unlimited && it.percent.isFinite() }?.maxOfOrNull { it.percent }
        ?.let { "${it.toInt()}%" } ?: "--"
}

@Composable
fun usageColor(percent: Float?): Color {
    val c = LocalMonitorPalette.current
    return when { percent == null -> c.text; percent < 70 -> c.green; percent < 90 -> c.amber; else -> c.red }
}

@Composable
fun QuotaCard(row: MonitoredAccount, now: Long, onDetail: () -> Unit, onConfigure: () -> Unit) {
    val c = LocalMonitorPalette.current
    val usage = row.usage
    val items = usage?.items.orEmpty()
    val maximum = items.filter { it.percent >= 0 && !it.unlimited }.maxOfOrNull { it.percent }
    val shown = if (row.platform == Platform.CHATGPT) items.filter { it.percent >= 0 }.take(2)
        else if (row.platform == Platform.CURSOR) items.filter { it.percent >= 0 }.take(1)
        else items
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(c.surface)
        .border(1.dp, c.line, RoundedCornerShape(20.dp)).padding(19.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
            ProviderLogo(row.platform)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                UiText(row.platform.displayName, 16, 23, 600, tracking = -.35f)
                Spacer(Modifier.height(2.dp))
                UiText("${planLabel(row)} · ${row.account.displayName}", 11, 16, color = c.muted, maxLines = 1)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                UiText(if (row.platform.isBalanceProvider()) "可用余额" else "最高已用", 9, 14, color = c.muted)
                Spacer(Modifier.height(1.dp))
                UiText(mainReading(row), 29, 33, 600, if (row.platform.isBalanceProvider()) c.text else usageColor(maximum), tracking = -1f)
            }
        }
        Spacer(Modifier.height(19.dp))
        if (items.isEmpty()) UiText(if (row.attention) "等待重新连接后同步" else "正在等待首次同步", 12, 20, color = c.muted)
        else if (row.platform.isBalanceProvider()) {
            val lines = if (row.platform == Platform.DEEPSEEK) items.filter { it.label != "账户余额" } else items
            InfoLines(lines.map { it.label to (it.valueText ?: "--") })
        } else Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { shown.forEach { UsageMetric(it) } }
        Spacer(Modifier.height(16.dp)); SettingDivider(); Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().height(24.dp), verticalAlignment = Alignment.CenterVertically) {
            val credits = usage?.resetCredits?.availableCount ?: 0
            if (row.platform == Platform.CURSOR) {
                val parts = items.drop(1).filter { it.percent >= 0 }.joinToString(" · ") { "${it.label.removeSuffix(" 用量")} ${it.percent.toInt()}%" }
                UiText(parts, 11, 17, color = c.muted, modifier = Modifier.weight(1f))
            } else {
                AppIcon(if (credits > 0) AppIcons.Autorenew else if (row.platform.isBalanceProvider()) AppIcons.Shield else AppIcons.Schedule,
                    null, size = 13.dp, tint = c.muted)
                Spacer(Modifier.width(5.dp))
                UiText(if (credits > 0) "$credits 张重置卡可用" else if (row.platform.isBalanceProvider()) "账户余额" else "${items.count { it.percent >= 0 }} 个额度窗口",
                    11, 17, color = c.muted, modifier = Modifier.weight(1f))
            }
            Row(Modifier.height(24.dp).clickable(role = Role.Button, onClick = onDetail), verticalAlignment = Alignment.CenterVertically) {
                UiText("查看详情", 11, 17)
                Spacer(Modifier.width(5.dp)); AppIcon(AppIcons.ChevronRight, null, size = 13.dp, tint = c.text)
            }
        }
        if (row.attention || row.paused) {
            Spacer(Modifier.height(13.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(c.amberSoft).padding(horizontal = 10.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                AppIcon(AppIcons.Warning, null, size = 14.dp, tint = c.amber)
                val text = when { row.needsLogin -> if (row.platform.requiresApiKey) "密钥需要更新，正在显示缓存" else "登录已过期，正在显示缓存"; row.paused -> "监控已暂停，显示上次同步数据"; else -> "暂时无法同步，正在显示缓存" }
                UiText(text, 10, 17, color = c.amber, modifier = Modifier.weight(1f))
                if (row.attention) Box(Modifier.height(24.dp).clickable(role = Role.Button, onClick = onConfigure), contentAlignment = Alignment.Center) {
                    UiText(if (row.platform.requiresApiKey) "更新密钥" else "重新连接", 10, 17, 500, c.amber)
                }
            }
        }
    }
}

@Composable
fun UsageMetric(item: UsageItem) {
    val c = LocalMonitorPalette.current
    if (item.percent < 0) { InfoLines(listOf(item.label to (item.valueText ?: "--"))); return }
    val used = if (item.unlimited) 100f else item.percent.takeIf { it.isFinite() }?.coerceIn(0f, 100f) ?: 0f
    val time = item.elapsedPercent?.takeIf { it.isFinite() && !item.unlimited }?.coerceIn(0f, 100f)
    val color = if (item.unlimited) c.primary else usageColor(used)
    Column {
        Row(Modifier.fillMaxWidth().heightIn(min = 17.dp), verticalAlignment = Alignment.CenterVertically) {
            UiText(item.label.replace("｜", " · "), 12, 17, color = c.muted, modifier = Modifier.weight(1f), maxLines = 1)
            Spacer(Modifier.width(5.dp))
            UiText(if (item.unlimited) "无限制" else "${item.percent.toInt()}%", 11, 17, 600)
            if (!item.unlimited) item.resetCountdown?.let {
                Spacer(Modifier.width(7.dp))
                UiText(it.replace("小时", "时").replace("分钟", "分"), 11, 17, color = c.muted, maxLines = 1)
            }
        }
        Spacer(Modifier.height(7.dp))
        Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(c.track).semantics {
            contentDescription = item.label
            progressBarRangeInfo = ProgressBarRangeInfo(used / 100f, 0f..1f)
            stateDescription = if (item.unlimited) "无限制" else "用量 ${used.toInt()}%" + (time?.let { "，时间已过 ${it.toInt()}%" } ?: "")
        }) {
            time?.let {
                Box(Modifier.fillMaxHeight().fillMaxWidth(it / 100f).clip(RoundedCornerShape(5.dp))
                    .background(Color(color.red * .45f + .55f, color.green * .45f + .55f, color.blue * .45f + .55f)))
            }
            Box(Modifier.fillMaxHeight().fillMaxWidth(used / 100f).clip(RoundedCornerShape(5.dp)).background(color))
        }
        (item.boostPercent?.let { "总额度 $it%" } ?: item.valueText)?.let {
            Spacer(Modifier.height(5.dp)); UiText(it, 10, 15, color = c.muted)
        }
    }
}

@Composable
fun InfoLines(lines: List<Pair<String, String>>, gap: Dp = 9.dp) {
    val c = LocalMonitorPalette.current
    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
        lines.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth().heightIn(min = 16.5.dp), verticalAlignment = Alignment.CenterVertically) {
                UiText(label.replace("累计请求次数", "累计请求"), 11, 16, color = c.muted, modifier = Modifier.weight(1f))
                UiText(if (label.contains("请求")) value.substringBefore(" ").toLongOrNull()?.let { "%,d 次".format(java.util.Locale.US, it) } ?: value else value, 11, 16, 500)
            }
        }
    }
}

@Composable
fun EmptyOverview(onAdd: () -> Unit) {
    AccountEmptyState("还没有显示的账户", "添加一个监控，或在账户设置中打开概览显示。", "添加监控", onAdd)
}

@Composable
fun AccountEmptyState(title: String, description: String, actionLabel: String? = null, onAction: () -> Unit = {}) {
    val c = LocalMonitorPalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(64.dp).background(c.soft, RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.Group, null, size = 24.dp, tint = c.muted)
        }
        Spacer(Modifier.height(20.dp)); UiText(title, 17, 26, 500)
        Spacer(Modifier.height(10.dp)); UiText(description, 12, 23, color = c.muted, align = TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        if (actionLabel != null) UiButton(actionLabel, onAction)
    }
}

@Composable
fun WelcomeScreen(scroll: ScrollState, bottomPadding: Dp, onAdd: () -> Unit, onBrowse: () -> Unit) {
    val c = LocalMonitorPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val pageHeight = maxHeight
        Column(Modifier.fillMaxWidth().heightIn(min = pageHeight).verticalScroll(scroll)
            .padding(start = 20.dp, end = 20.dp, top = 21.dp, bottom = bottomPadding)) {
            PageTitle("知余", "你的 AI 额度与余额，一处掌握")
            Spacer(Modifier.height(22.dp))
            Column(Modifier.fillMaxWidth().heightIn(min = (pageHeight - 108.dp - bottomPadding).coerceAtLeast(521.dp))
                .padding(top = 20.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                OnboardingIllustration()
                Spacer(Modifier.height(24.dp)); UiText("从第一个账户开始", 24, 34, 600, tracking = -.6f)
                Spacer(Modifier.height(12.dp)); UiText("连接你正在使用的 AI 账户，\n随时查看余额、用量和重置时间。", 13, 23, color = c.muted, align = TextAlign.Center)
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(horizontalArrangement = Arrangement.spacedBy((-5).dp)) {
                        listOf(Platform.CHATGPT, Platform.CLAUDE, Platform.CURSOR, Platform.DEEPSEEK, Platform.MINIMAX).forEach { p ->
                            Box(Modifier.size(31.dp).background(c.surface, CircleShape).border(2.dp, c.background, CircleShape), contentAlignment = Alignment.Center) {
                                ProviderLogo(p, 19.dp, framed = false)
                            }
                        }
                    }
                    Spacer(Modifier.width(10.dp)); UiText("支持 7 个 AI 供应商", 11, 17, color = c.muted)
                }
                Spacer(Modifier.height(29.dp))
                Column(Modifier.widthIn(max = 310.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    UiButton("添加第一个账户", onAdd, Modifier.height(49.dp), icon = AppIcons.Add)
                    Spacer(Modifier.height(5.dp)); TextAction("查看支持的 AI", onBrowse, Modifier.height(44.dp))
                }
                Spacer(Modifier.height(11.dp)); UiText("使用已有账户登录，或通过 API 密钥连接", 10, 18, color = c.muted)
            }
        }
    }
}

@Composable
fun OnboardingIllustration() {
    val c = LocalMonitorPalette.current
    Box(Modifier.size(248.dp, 166.dp)) {
        Column(Modifier.offset(x = 45.dp, y = 5.dp).size(184.dp, 110.dp).rotate(7f)
            .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1817262A), spotColor = Color(0x1817262A)).background(c.soft, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(24.dp)) {
            Skeleton(90.dp); Spacer(Modifier.height(15.dp)); Skeleton(53.dp)
        }
        Column(Modifier.offset(x = 6.dp, y = 33.dp).size(218.dp, 120.dp).rotate(-4f)
            .shadow(4.dp, RoundedCornerShape(18.dp), ambientColor = Color(0x1817262A), spotColor = Color(0x1817262A)).background(c.surface, RoundedCornerShape(18.dp))
            .border(1.dp, c.line, RoundedCornerShape(18.dp)).padding(21.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { ProviderLogo(Platform.CHATGPT, 25.dp); Spacer(Modifier.width(11.dp)); Skeleton(72.dp) }
            Spacer(Modifier.height(15.dp))
            Box(Modifier.fillMaxWidth().height(8.dp).background(c.track, RoundedCornerShape(5.dp))) {
                Box(Modifier.fillMaxWidth(.64f).fillMaxHeight().background(c.green.copy(alpha = .65f), RoundedCornerShape(5.dp)))
            }
            Spacer(Modifier.height(12.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Skeleton(53.dp); Skeleton(30.dp) }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 2.dp).size(43.dp).shadow(3.dp, RoundedCornerShape(14.dp), ambientColor = Color(0x5017262A), spotColor = Color(0x5017262A))
            .background(c.primary, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            AppIcon(AppIcons.Add, null, size = 23.dp, tint = c.onPrimary)
        }
    }
}
@Composable private fun Skeleton(width: Dp) = Box(Modifier.size(width, 7.dp).background(LocalMonitorPalette.current.line, RoundedCornerShape(5.dp)))

@Composable
fun AccountDetail(row: MonitoredAccount, now: Long, onConfigure: () -> Unit) {
    val c = LocalMonitorPalette.current
    val items = row.usage?.items.orEmpty()
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            UiText(if (row.platform.isBalanceProvider()) "可用余额" else "最高已用", 11, 16, color = c.muted, modifier = Modifier.heightIn(min = 16.5.dp))
            Spacer(Modifier.height(3.dp)); UiText(mainReading(row), 42, 63, 600, usageColor(if (row.platform.isBalanceProvider()) null else items.filter { it.percent >= 0 }.maxOfOrNull { it.percent }), tracking = -1.5f)
        }
        ProviderLogo(row.platform, 42.dp)
    }
    if (row.platform.isBalanceProvider()) {
        val lines = if (row.platform == Platform.DEEPSEEK) items.filter { it.label != "账户余额" } else items
        InfoLines(lines.map { it.label to (it.valueText ?: "--") })
    } else {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) { items.filter { it.percent >= 0 }.forEach { UsageMetric(it) } }
        Spacer(Modifier.height(12.dp)); UiText("深色表示用量 · 浅色表示时间进度", 10, 15, color = c.muted)
        val extra = items.filter { it.percent < 0 }.map { it.label to (it.valueText ?: "--") }
        val credits = row.usage?.resetCredits?.credits.orEmpty()
        if (extra.isNotEmpty() || credits.isNotEmpty()) {
            Spacer(Modifier.height(20.dp)); SettingDivider(); Spacer(Modifier.height(15.dp)); UiText("订阅与重置卡", 12, 18, 500); Spacer(Modifier.height(13.dp))
            InfoLines(extra + credits.mapIndexed { i, credit -> "重置卡 ${i + 1}" to if (credit.expiresAt <= now) "已到期" else "${ceil((credit.expiresAt - now) / 86400000.0).toInt()} 天后到期" }, gap = 12.dp)
        }
    }
    Spacer(Modifier.height(22.dp)); UiButton("配置这个账户", onConfigure, secondary = true)
}
