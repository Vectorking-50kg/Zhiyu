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
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.platform.LocalDensity
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
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.ui.R
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorStyle
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalBaseDensity
import funapp.ctrlcv.zhiyu.core.ui.theme.monitorTextStyle
import top.yukonga.miuix.kmp.basic.Button as MiuixButton
import top.yukonga.miuix.kmp.basic.ButtonDefaults as MiuixButtonDefaults
import top.yukonga.miuix.kmp.basic.Switch as MiuixSwitch
import top.yukonga.miuix.kmp.basic.Text as MiuixText

@Composable
fun UiText(
    text: String, size: Int = 14, line: Int = (size * 1.5).toInt(), weight: Int = 400,
    color: Color = LocalMonitorPalette.current.text, modifier: Modifier = Modifier,
    tracking: Float = 0f, align: TextAlign = TextAlign.Start, maxLines: Int = Int.MAX_VALUE,
    decoration: TextDecoration? = null,
) {
    val style = monitorTextStyle(size, line, weight, tracking).copy(fontFamily = LocalMonitorStyle.current.fontFamily)
    if (LocalAppearanceSettings.current.uiStyle == UiStyle.MIUIX) {
        MiuixText(text, modifier, color, style = style, textAlign = align,
            maxLines = maxLines, overflow = TextOverflow.Ellipsis, textDecoration = decoration)
    } else Text(text, modifier, color, style = style,
        textAlign = align, maxLines = maxLines, overflow = TextOverflow.Ellipsis, textDecoration = decoration)
}

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
    Platform.CHATGPT -> "5 小时、每周用量与重置卡"
    Platform.CLAUDE -> "5 小时、每周及模型用量"
    Platform.CURSOR -> "套餐用量、Auto 与 API 用量"
    Platform.ZEN -> "账户余额"
    Platform.MINIMAX -> "Token Plan 限额"
    Platform.AIHUBMIX -> "账户余额、请求次数"
    Platform.DEEPSEEK -> "账户余额"
}

@Composable
fun IconAction(@DrawableRes icon: Int, label: String, onClick: () -> Unit,
    modifier: Modifier = Modifier, size: Dp = 44.dp, iconSize: Dp = 22.dp,
    tint: Color = LocalMonitorPalette.current.muted, enabled: Boolean = true,
    iconModifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        AppIcon(icon, null, size = iconSize, tint = tint, modifier = iconModifier)
    }
}

@Composable
fun PageTitle(title: String, subtitle: String = "", statusDot: Boolean = false, action: (@Composable () -> Unit)? = null) {
    val c = LocalMonitorPalette.current
    Row(Modifier.fillMaxWidth().heightIn(min = if (action == null) 40.dp else 44.dp), verticalAlignment = Alignment.CenterVertically) {
        UiText(title, LocalMonitorStyle.current.titleSize, 40, 600, tracking = -1f, modifier = Modifier.weight(1f))
        action?.invoke()
    }
    if (subtitle.isNotBlank()) {
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (statusDot) { Box(Modifier.size(5.dp).background(c.green, CircleShape)); Spacer(Modifier.width(6.dp)) }
            UiText(subtitle, 12, 20, color = c.muted)
        }
    }
}

@Composable
fun UiButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
    secondary: Boolean = false, enabled: Boolean = true, @DrawableRes icon: Int? = null) {
    val c = LocalMonitorPalette.current
    val shape = RoundedCornerShape(12.dp)
    val foreground = if (secondary) c.text else c.onPrimary
    val content: @Composable RowScope.() -> Unit = {
        icon?.let { AppIcon(it, null, size = 18.dp, tint = foreground); Spacer(Modifier.width(8.dp)) }
        UiText(label, if (secondary) 12 else 13, 20, 500, foreground)
    }
    val bounds = modifier.fillMaxWidth().heightIn(min = if (secondary) 46.dp else 48.dp)
    if (LocalAppearanceSettings.current.uiStyle == UiStyle.MIUIX) {
        MiuixButton(onClick, bounds, enabled, cornerRadius = 18.dp, minHeight = 48.dp,
            colors = if (secondary) MiuixButtonDefaults.buttonColors() else MiuixButtonDefaults.buttonColorsPrimary(),
            insideMargin = PaddingValues(horizontal = 18.dp, vertical = 12.dp), content = content)
    } else if (secondary) {
        OutlinedButton(onClick, bounds, enabled, shape = shape,
            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.surface, contentColor = foreground),
            border = androidx.compose.foundation.BorderStroke(1.dp, c.line),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), content = content)
    } else Button(onClick, bounds, enabled, shape = shape,
        colors = ButtonDefaults.buttonColors(containerColor = c.primary, contentColor = foreground),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp), content = content)
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
            Box(Modifier.weight(1f).heightIn(min = 34.dp).clip(RoundedCornerShape(8.dp))
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
            textStyle = monitorTextStyle(12, 22).copy(color = c.text, fontFamily = LocalMonitorStyle.current.fontFamily), singleLine = true,
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
                textStyle = monitorTextStyle(13, 20).copy(color = c.text, fontFamily = LocalMonitorStyle.current.fontFamily), singleLine = true,
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
    val shape = LocalMonitorStyle.current.groupShape
    Column(modifier.fillMaxWidth().clip(shape).background(c.surface)
        .border(1.dp, c.line, shape).padding(1.dp), content = content)
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
    val semantics = Modifier.semantics { contentDescription = label }
    if (LocalAppearanceSettings.current.uiStyle == UiStyle.MIUIX) MiuixSwitch(checked, onChecked, semantics)
    else Switch(checked, onChecked, semantics, colors = SwitchDefaults.colors(
        checkedTrackColor = c.primary, checkedThumbColor = c.onPrimary,
        uncheckedTrackColor = c.soft, uncheckedThumbColor = c.muted, uncheckedBorderColor = c.muted,
    ))
}

@Composable
fun SectionCaption(title: String, count: String? = null, modifier: Modifier = Modifier) {
    val c = LocalMonitorPalette.current
    Row(modifier.fillMaxWidth().heightIn(min = if (count == null) 15.dp else 16.5.dp).padding(horizontal = 3.dp), verticalAlignment = Alignment.CenterVertically) {
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
    val appDensity = LocalDensity.current
    val corner = CornerSize(with(appDensity) { 28.dp.toPx() })
    // Configuration dp remains at the system density; convert before applying the app's scale.
    val height = (LocalConfiguration.current.screenHeightDp * LocalBaseDensity.current.density / LocalDensity.current.density).dp - 54.dp
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state, containerColor = c.sheet, tonalElevation = 0.dp,
        scrimColor = Color(0x6009131B), dragHandle = null,
        shape = RoundedCornerShape(corner, corner, CornerSize(0.dp), CornerSize(0.dp)),
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) }) {
        // Dialog creates another Android Compose root, which supplies the device density again.
        CompositionLocalProvider(LocalDensity provides appDensity) {
            Column(Modifier.fillMaxWidth().heightIn(max = height)) {
                Box(Modifier.padding(top = 10.dp, bottom = 8.dp).align(Alignment.CenterHorizontally)
                    .size(36.dp, 4.dp).background(c.line, RoundedCornerShape(6.dp)))
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp).heightIn(min = 46.dp), verticalAlignment = Alignment.CenterVertically) {
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
}

/** Keep text and controls at the app's scale inside Android's separate alert-dialog root. */
@Composable
fun ScaledAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    containerColor: Color = LocalMonitorPalette.current.sheet,
) {
    val density = LocalDensity.current
    val corner = CornerSize(with(density) { 28.dp.toPx() })
    AlertDialog(
        onDismissRequest, modifier = modifier, containerColor = containerColor,
        shape = RoundedCornerShape(corner, corner, corner, corner),
        confirmButton = { CompositionLocalProvider(LocalDensity provides density, content = confirmButton) },
        dismissButton = dismissButton?.let { slot -> { CompositionLocalProvider(LocalDensity provides density, content = slot) } },
        icon = icon?.let { slot -> { CompositionLocalProvider(LocalDensity provides density, content = slot) } },
        title = title?.let { slot -> { CompositionLocalProvider(LocalDensity provides density, content = slot) } },
        text = text?.let { slot -> { CompositionLocalProvider(LocalDensity provides density, content = slot) } },
    )
}
