package funapp.ctrlcv.zhiyu.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.ui.R
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.monitorTextStyle

@Composable
fun UiText(
    text: String, size: Int = 14, line: Int = (size * 1.5).toInt(), weight: Int = 400,
    color: Color = LocalMonitorPalette.current.text, modifier: Modifier = Modifier,
    tracking: Float = 0f, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE,
    decoration: TextDecoration? = null,
) = Text(text, modifier, color, style = monitorTextStyle(size, line, weight, tracking),
    textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textDecoration = decoration)

@DrawableRes
fun platformIcon(platform: Platform): Int = when (platform) {
    Platform.CHATGPT -> R.drawable.ic_brand_chatgpt
    Platform.CLAUDE -> R.drawable.ic_brand_anthropic
    Platform.CURSOR -> R.drawable.ic_brand_cursor
    Platform.ZEN -> R.drawable.ic_brand_opencode
    Platform.MINIMAX -> R.drawable.ic_brand_minimax
    Platform.AIHUBMIX -> R.drawable.ic_brand_aihubmix
    Platform.DEEPSEEK -> R.drawable.ic_brand_deepseek
}

@Composable
fun ProviderLogo(platform: Platform, size: Dp = 34.dp, modifier: Modifier = Modifier, framed: Boolean = true) {
    Image(painterResource(platformIcon(platform)), null, modifier.size(size).then(
        if (platform == Platform.CURSOR || platform == Platform.ZEN)
            if (framed) Modifier.clip(RoundedCornerShape(8.dp)).background(Color.White).padding(3.dp).clip(RoundedCornerShape(5.dp))
            else Modifier.clip(RoundedCornerShape(4.dp)).background(Color.White)
        else Modifier
    ))
}

fun Platform.isBalanceProvider() = this in setOf(Platform.ZEN, Platform.AIHUBMIX, Platform.DEEPSEEK)
fun Platform.monitorDescription() = when (this) {
    Platform.CHATGPT -> "5 小时、每周额度与重置卡"
    Platform.CLAUDE -> "5 小时、每周及模型额度"
    Platform.CURSOR -> "订阅用量、Auto 与 API 额度"
    Platform.ZEN -> "账户余额"
    Platform.MINIMAX -> "Token Plan 限额与 Boost 状态"
    Platform.AIHUBMIX -> "余额、消费金额与请求次数"
    Platform.DEEPSEEK -> "账户余额与充值、赠送明细"
}

@Composable
fun IconAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 22.dp,
    tint: Color = LocalMonitorPalette.current.muted, enabled: Boolean = true) {
    Box(modifier.size(size).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AppIcon(icon, null, size = iconSize, tint = tint)
    }
}

@Composable
fun PageTitle(title: String, subtitle: String, statusDot: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val c = LocalMonitorPalette.current
    Row(Modifier.fillMaxWidth().height(if (action == null) 40.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(title, 30, 40, 600, tracking = -1f, modifier = Modifier.weight(1f))
        action?.invoke()
    }
    Spacer(Modifier.height(5.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (statusDot) { Box(Modifier.size(5.dp).background(c.green, CircleShape)); Spacer(Modifier.width(6.dp)) }
        UiText(subtitle, 12, 20, color = c.muted)
    }
}

@Composable
fun UiButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    secondary: Boolean = false, enabled: Boolean = true, @DrawableRes icon: Int? = null) {
    val c = LocalMonitorPalette.current
    val shape = RoundedCornerShape(12.dp)
    val foreground = if (secondary) c.text else c.onPrimary
    Row(modifier.fillMaxWidth().heightIn(min = if (secondary) 46.dp else 48.dp)
        .clip(shape).background(if (secondary) c.surface else c.primary.copy(alpha = if (enabled) 1f else .45f))
        .then(if (secondary) Modifier.border(1.dp, c.line, shape) else Modifier)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 18.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        icon?.let { AppIcon(it, null, size = 18.dp, tint = foreground); Spacer(Modifier.width(8.dp)) }
        UiText(label, if (secondary) 12 else 13, 20, 500, foreground)
    }
}

@Composable
fun TextAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    color: Color = LocalMonitorPalette.current.muted, size: Int = 12) {
    Box(modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(8.dp))
        .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
        UiText(label, size, 18, 500, color)
    }
}

@Composable
fun FilterTabs(items: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalMonitorPalette.current
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(c.soft).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        items.forEachIndexed { index, label ->
            Box(Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp))
                .background(if (index == selected) c.surface else Color.Transparent)
                .selectable(index == selected, role = Role.Tab) { onSelect(index) }, contentAlignment = Alignment.Center) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val count = label.substringAfterLast(" ").toIntOrNull()
                    val foreground = if (index == selected) c.text else c.muted
                    UiText(if (count == null) label else label.substringBeforeLast(" "), 12, 18, if (index == selected) 600 else 400, foreground)
                    if (count != null) { Spacer(Modifier.width(4.dp)); UiText(count.toString(), 10, 18, if (index == selected) 600 else 400, foreground.copy(alpha = .7f)) }
                }
            }
        }
    }
}

@Composable
fun SearchInput(value: String, onValue: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val c = LocalMonitorPalette.current
    Row(modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(10.dp)).background(c.soft)
        .padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        AppIcon(AppIcons.Search, null, size = 17.dp, tint = c.subtle)
        Spacer(Modifier.width(8.dp))
        BasicTextField(value, onValue, Modifier.weight(1f).semantics { contentDescription = placeholder },
            textStyle = monitorTextStyle(12, 22).copy(color = c.text), singleLine = true,
            cursorBrush = SolidColor(c.primary), decorationBox = { field ->
                Box { if (value.isEmpty()) UiText(placeholder, 12, 22, color = c.subtle); field() }
            })
    }
}

@Composable
fun FormInput(label: String, value: String, onValue: (String) -> Unit, placeholder: String = "",
    password: Boolean = false, modifier: Modifier = Modifier, hint: String? = null) {
    val c = LocalMonitorPalette.current
    var reveal by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        UiText(label, 11, 17, 500, c.muted)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(49.dp).clip(RoundedCornerShape(11.dp))
            .background(c.surface).border(1.dp, c.line, RoundedCornerShape(11.dp)).padding(start = 14.dp, end = if (password) 4.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            BasicTextField(value, onValue, Modifier.weight(1f).semantics { contentDescription = label },
                textStyle = monitorTextStyle(13, 20).copy(color = c.text), singleLine = true,
                keyboardOptions = if (password) KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Password) else KeyboardOptions.Default,
                visualTransformation = if (password && !reveal) PasswordVisualTransformation() else VisualTransformation.None,
                cursorBrush = SolidColor(c.primary), decorationBox = { field ->
                    Box { if (value.isEmpty()) UiText(placeholder, 13, 20, color = c.subtle, maxLines = 1); field() }
                })
            if (password) IconAction(if (reveal) AppIcons.VisibilityOff else AppIcons.Visibility,
                if (reveal) "隐藏密钥" else "显示密钥", { reveal = !reveal }, size = 42.dp, iconSize = 18.dp)
        }
        hint?.let { Spacer(Modifier.height(7.dp)); UiText(it, 10, 18, color = c.muted) }
    }
}

@Composable
fun SettingGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = LocalMonitorPalette.current
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(c.surface)
        .border(1.dp, c.line, RoundedCornerShape(16.dp)).padding(1.dp), content = content)
}

@Composable
fun SettingDivider() = HorizontalDivider(color = LocalMonitorPalette.current.line)

@Composable
fun SettingRow(title: String, description: String? = null, @DrawableRes icon: Int? = null,
    value: String? = null, onClick: (() -> Unit)? = null, first: Boolean = true, trailing: (@Composable () -> Unit)? = null) {
    val c = LocalMonitorPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = if (first) 67.dp else 66.dp)
        .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
        .padding(15.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        icon?.let { AppIcon(it, null, size = 21.dp, tint = c.muted) }
        Column(Modifier.weight(1f)) {
            UiText(title, 13, 21)
            description?.let { Spacer(Modifier.height(2.dp)); UiText(it, 10, 17, color = c.muted) }
        }
        value?.let { UiText(it, 11, 17, color = c.muted) }
        if (trailing != null) trailing() else if (onClick != null) AppIcon(AppIcons.ChevronRight, null, size = 15.dp, tint = c.subtle)
    }
}

@Composable
fun UiSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val c = LocalMonitorPalette.current
    Box(Modifier.size(44.dp, 27.dp).clip(RoundedCornerShape(17.dp))
        .background(if (checked) c.primary else c.soft)
        .border(1.5.dp, if (checked) c.primary else c.muted, RoundedCornerShape(17.dp))
        .toggleable(checked, role = Role.Switch, onValueChange = onChecked)
        .semantics { contentDescription = label }.padding(horizontal = 4.5.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart) {
        Box(Modifier.size(if (checked) 19.dp else 17.dp).background(if (checked) c.onPrimary else c.muted, CircleShape))
    }
}

@Composable
fun SectionCaption(title: String, count: String? = null, modifier: Modifier = Modifier) {
    val c = LocalMonitorPalette.current
    Row(modifier.fillMaxWidth().height(if (count == null) 15.dp else 16.5.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(title, if (count == null) 10 else 11, if (count == null) 15 else 17, color = c.muted, modifier = Modifier.weight(1f))
        count?.let { UiText(it, 10, 16, color = c.muted) }
    }
}

@Composable
fun StatusBadge(label: String, attention: Boolean = false, paused: Boolean = false, modifier: Modifier = Modifier, compact: Boolean = true) {
    val c = LocalMonitorPalette.current
    val color = if (attention) c.amber else if (paused) c.muted else c.green
    Row(modifier.clip(RoundedCornerShape(6.dp)).background(if (attention) c.amberSoft else if (paused) c.soft else c.greenSoft)
        .padding(horizontal = if (compact) 6.dp else 8.dp, vertical = if (compact) 3.dp else 4.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(Modifier.size(4.dp).background(color, CircleShape))
        UiText(label, if (compact) 9 else 10, 16, 500, color, maxLines = 1)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorSheet(title: String, subtitle: String? = null, onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit) {
    val c = LocalMonitorPalette.current
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val height = LocalConfiguration.current.screenHeightDp.dp - 54.dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.sheet, tonalElevation = 0.dp,
        scrimColor = Color(0x6009131B), dragHandle = null,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        Column(Modifier.fillMaxWidth().heightIn(max = height)) {
            Box(Modifier.padding(top = 10.dp, bottom = 8.dp).align(Alignment.CenterHorizontally)
                .size(36.dp, 4.dp).background(c.line, RoundedCornerShape(6.dp)))
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).height(46.dp), verticalAlignment = Alignment.CenterVertically) {
                UiText(title, 23, 32, 600, tracking = -.5f, modifier = Modifier.weight(1f))
                IconAction(AppIcons.Close, "关闭面板", onDismiss)
            }
            subtitle?.let { UiText(it, 11, 19, color = c.muted, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 5.dp)) }
            Spacer(Modifier.height(12.dp))
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp).navigationBarsPadding().imePadding().padding(bottom = 12.dp), content = content)
        }
    }
}
