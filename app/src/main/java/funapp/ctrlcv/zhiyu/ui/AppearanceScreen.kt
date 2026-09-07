package funapp.ctrlcv.zhiyu.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import funapp.ctrlcv.zhiyu.core.ui.components.ScaledAlertDialog as AlertDialog
import androidx.compose.material3.Slider
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import funapp.ctrlcv.zhiyu.core.domain.model.AppearanceSettings
import funapp.ctrlcv.zhiyu.core.domain.model.BottomBarStyle
import funapp.ctrlcv.zhiyu.core.domain.model.ColorMode
import funapp.ctrlcv.zhiyu.core.domain.model.ThemeColorSource
import funapp.ctrlcv.zhiyu.core.domain.model.ThemePalette
import funapp.ctrlcv.zhiyu.core.domain.model.UiStyle
import funapp.ctrlcv.zhiyu.core.ui.components.IconAction
import funapp.ctrlcv.zhiyu.core.ui.components.SettingGroup
import funapp.ctrlcv.zhiyu.core.ui.components.SettingRow
import funapp.ctrlcv.zhiyu.core.ui.components.TextAction
import funapp.ctrlcv.zhiyu.core.ui.components.UiButton
import funapp.ctrlcv.zhiyu.core.ui.components.UiSwitch
import funapp.ctrlcv.zhiyu.core.ui.components.UiText
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcon
import funapp.ctrlcv.zhiyu.core.ui.icons.AppIcons
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearancePreferences
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalAppearanceSettings
import funapp.ctrlcv.zhiyu.core.ui.theme.LocalMonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.MonitorPalette
import funapp.ctrlcv.zhiyu.core.ui.theme.monitorTextStyle
import funapp.ctrlcv.zhiyu.core.ui.theme.rememberAppearancePalette
import java.util.Locale
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Slider as MiuixSlider

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val settings = LocalAppearanceSettings.current
    val preferences = LocalAppearancePreferences.current
    val c = LocalMonitorPalette.current
    val dark = when (settings.colorMode) {
        ColorMode.SYSTEM -> isSystemInDarkTheme()
        ColorMode.LIGHT -> false
        ColorMode.DARK -> true
    }
    val preview = rememberAppearancePalette(settings, dark)
    var confirmReset by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(c.background).safeDrawingPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(AppIcons.ArrowBack, "返回设置", onBack)
            UiText("主题与外观", 22, 30, 600, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            TextAction("恢复默认", { confirmReset = true }, size = 12)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            AppearanceSection("界面风格", "同一份账户数据，两种界面风格") {
                val roomyText = LocalDensity.current.fontScale >= 1.4f
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    val stacked = maxWidth < 320.dp || roomyText
                    if (stacked) {
                        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            UiStyle.entries.forEach { style ->
                                StyleOption(settings, style, dark, Modifier.fillMaxWidth()) {
                                    preferences.update { it.copy(uiStyle = style) }
                                }
                            }
                        }
                    } else {
                        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            UiStyle.entries.forEach { style ->
                                StyleOption(settings, style, dark, Modifier.weight(1f)) {
                                    preferences.update { it.copy(uiStyle = style) }
                                }
                            }
                        }
                    }
                }
            }

            AppearanceSection("主题预览", "配色会即时应用到整个应用") {
                PalettePreview(preview, settings)
            }

            AppearanceSection("颜色模式") {
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ColorMode.entries.forEach { mode ->
                        AppearanceChoice(mode.label(), settings.colorMode == mode) {
                            preferences.update { it.copy(colorMode = mode) }
                        }
                    }
                }
                SettingGroup {
                    SettingRow("纯黑背景", "在深色模式下使用纯黑背景", icon = AppIcons.DarkMode, trailing = {
                        UiSwitch("纯黑背景", settings.pureBlack) { enabled ->
                            preferences.update { it.copy(pureBlack = enabled) }
                        }
                    })
                }
            }

            AppearanceSection("主题配色") {
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeColorSource.entries.forEach { source ->
                        AppearanceChoice(
                            source.title(), settings.colorSource == source,
                            enabled = source != ThemeColorSource.SYSTEM || Build.VERSION.SDK_INT >= 31,
                        ) { preferences.update { it.copy(colorSource = source) } }
                    }
                }
                UiText(
                    when {
                        Build.VERSION.SDK_INT < 31 -> "系统莫奈需要 Android 12 或更高版本，可使用默认配色或自选颜色。"
                        settings.colorSource == ThemeColorSource.SYSTEM -> "跟随系统壁纸的主题色，壁纸配色变化时自动更新。"
                        settings.colorSource == ThemeColorSource.CUSTOM -> "选择一个主色，自动生成协调的浅色与深色色板。"
                        else -> "使用当前界面风格的默认色板。"
                    }, 12, 19, color = c.muted,
                )
                if (settings.colorSource == ThemeColorSource.CUSTOM) {
                    SeedColorEditor(settings.seedColor) { color ->
                        preferences.update { it.copy(seedColor = color, colorSource = ThemeColorSource.CUSTOM) }
                    }
                }
            }

            AppearanceSection("色彩风格", "对系统莫奈和自选颜色生效") {
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemePalette.entries.forEach { palette ->
                        AppearanceChoice(palette.title(), settings.palette == palette, settings.colorSource != ThemeColorSource.DEFAULT) {
                            preferences.update { it.copy(palette = palette) }
                        }
                    }
                }
            }

            AppearanceSection("底部导航栏") {
                FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottomBarStyle.entries.forEach { style ->
                        AppearanceChoice(
                            style.title(), settings.bottomBarStyle == style,
                            enabled = style != BottomBarStyle.GLASS || Build.VERSION.SDK_INT >= 33,
                        ) { preferences.update { it.copy(bottomBarStyle = style) } }
                    }
                }
                UiText(
                    when {
                        Build.VERSION.SDK_INT < 33 -> "液态玻璃需要 Android 13 或更高版本，当前设备支持标准与悬浮样式。"
                        settings.bottomBarStyle == BottomBarStyle.GLASS -> "透过底栏呈现页面内容，带有模糊与折射效果。"
                        settings.bottomBarStyle == BottomBarStyle.FIXED -> "导航栏贴合屏幕底部，清晰易用。"
                        else -> "导航栏悬浮于内容之上，保留舒适的底部间距。"
                    }, 12, 19, color = c.muted,
                )
            }

            AppearanceSection("界面缩放", "同时调整文字与控件大小，保留系统字体比例") {
                ScaleControl(settings) { scale -> preferences.update { it.copy(scale = scale) } }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { UiText("恢复默认外观？", 20, 28, 600) },
            text = { UiText("将重置界面风格、配色、底栏和缩放。账户与监控设置不受影响。", 14, 23) },
            confirmButton = {
                TextAction("恢复默认", {
                    preferences.update { AppearanceSettings() }
                    confirmReset = false
                }, color = c.primary, size = 14)
            },
            dismissButton = { TextAction("取消", { confirmReset = false }, size = 14) },
            containerColor = c.sheet,
        )
    }
}

@Composable
private fun AppearanceSection(title: String, description: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            UiText(title, 15, 22, 600)
            description?.let { UiText(it, 12, 19, color = LocalMonitorPalette.current.muted) }
        }
        content()
    }
}

@Composable
private fun AppearanceChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val c = LocalMonitorPalette.current
    val shape = RoundedCornerShape(12.dp)
    val foreground = (if (selected) c.text else c.muted).copy(alpha = if (enabled) 1f else .5f)
    Row(
        Modifier.heightIn(min = 44.dp).clip(shape)
            .background(if (selected) c.indicator else c.surface)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) c.primary else c.line, shape)
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selected) AppIcon(AppIcons.Check, null, size = 16.dp, tint = foreground)
        UiText(label, 12, 19, if (selected) 600 else 400, foreground)
    }
}

@Composable
private fun StyleOption(settings: AppearanceSettings, style: UiStyle, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = rememberAppearancePalette(settings.copy(uiStyle = style), dark)
    val c = LocalMonitorPalette.current
    val selected = settings.uiStyle == style
    val shape = RoundedCornerShape(if (style == UiStyle.MIUIX) 24.dp else 16.dp)
    Column(
        modifier.clip(shape).background(c.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) c.primary else c.line, shape)
            .selectable(selected, role = Role.RadioButton, onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(colors.background)
                .padding(12.dp).clearAndSetSemantics {},
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(38.dp).height(5.dp).background(colors.text, RoundedCornerShape(5.dp)))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(if (style == UiStyle.MIUIX) 12.dp else 8.dp))
                    .background(colors.surface).padding(9.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(18.dp).background(colors.primary, if (style == UiStyle.MIUIX) CircleShape else RoundedCornerShape(5.dp)))
                Box(Modifier.weight(1f).height(5.dp).background(colors.line, RoundedCornerShape(5.dp)))
            }
            Box(Modifier.fillMaxWidth(.7f).height(6.dp).background(colors.indicator, RoundedCornerShape(5.dp)))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            UiText(if (style == UiStyle.MATERIAL) "Material" else "Miuix", 15, 22, 600, modifier = Modifier.weight(1f))
            if (selected) AppIcon(AppIcons.CheckCircle, null, size = 20.dp, tint = c.primary)
        }
        UiText(if (style == UiStyle.MATERIAL) "清晰、简洁的原生风格" else "柔和分组与弹性控件", 11, 18, color = c.muted)
    }
}

@Composable
private fun PalettePreview(c: MonitorPalette, settings: AppearanceSettings) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(c.background).border(1.dp, c.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UiText("概览", 19, 27, 600, c.text, modifier = Modifier.weight(1f))
            AppIcon(AppIcons.Palette, null, size = 22.dp, tint = c.primary)
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(if (settings.uiStyle == UiStyle.MIUIX) 20.dp else 14.dp))
                .background(c.surface).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            UiText("账户用量", 13, 20, 500, c.text)
            Box(Modifier.fillMaxWidth().height(10.dp).clip(CircleShape).background(c.track).clearAndSetSemantics {}) {
                Box(Modifier.fillMaxWidth(.78f).fillMaxHeight().background(c.primary.copy(alpha = .3f), CircleShape))
                Box(Modifier.fillMaxWidth(.52f).fillMaxHeight().background(c.primary, CircleShape))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                UiText("用量进度", 10, 17, color = c.text, modifier = Modifier.weight(1f))
                UiText("时间进度", 10, 17, color = c.muted, modifier = Modifier.weight(1f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("主色" to c.primary, "容器" to c.indicator, "背景" to c.background).forEach { (label, color) ->
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(Modifier.size(14.dp).background(color, CircleShape).border(1.dp, c.line, CircleShape))
                    UiText(label, 10, 17, color = c.muted)
                }
            }
        }
        Row(
            Modifier.align(Alignment.CenterHorizontally)
                .fillMaxWidth(if (settings.bottomBarStyle == BottomBarStyle.FIXED) 1f else .8f)
                .clip(RoundedCornerShape(if (settings.bottomBarStyle == BottomBarStyle.FIXED) 6.dp else 24.dp))
                .background(c.toolbar.copy(alpha = if (settings.bottomBarStyle == BottomBarStyle.GLASS) .75f else 1f))
                .padding(horizontal = 12.dp, vertical = 8.dp).clearAndSetSemantics {},
            horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(AppIcons.Dashboard, AppIcons.Group, AppIcons.Settings).forEachIndexed { index, icon ->
                Box(Modifier.size(30.dp).background(if (index == 0) c.indicator else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    AppIcon(icon, null, size = 18.dp, tint = if (index == 0) c.text else c.muted)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeedColorEditor(seed: Int, onApply: (Int) -> Unit) {
    val c = LocalMonitorPalette.current
    val keyboard = LocalSoftwareKeyboardController.current
    var hex by rememberSaveable(seed) { mutableStateOf(String.format(Locale.ROOT, "#%06X", seed and 0xFFFFFF)) }
    var invalid by rememberSaveable(seed) { mutableStateOf(false) }
    val applyColor = {
        val digits = hex.trim().removePrefix("#")
        val rgb = digits.takeIf { it.length == 6 && it.all { char -> char in '0'..'9' || char.lowercaseChar() in 'a'..'f' } }?.toIntOrNull(16)
        invalid = rgb == null
        if (rgb != null) {
            onApply(rgb or 0xFF000000.toInt())
            keyboard?.hide()
        }
    }
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        listOf(0xFF4A7696, 0xFF43745E, 0xFF756099, 0xFFAD6677, 0xFFAD783F, 0xFF587F88, 0xFF5569AD, 0xFF7A7B55).forEach { value ->
            val color = Color(value)
            val selected = value.toInt() == seed
            Box(
                Modifier.size(44.dp).clip(CircleShape).selectable(selected, role = Role.RadioButton) { onApply(value.toInt()) }
                    .semantics { contentDescription = String.format(Locale.ROOT, "主色 #%06X", value and 0xFFFFFF) }
                    .padding(5.dp).background(color, CircleShape), contentAlignment = Alignment.Center,
            ) {
                if (selected) AppIcon(AppIcons.Check, null, size = 19.dp, tint = if (color.luminance() > .5f) Color.Black else Color.White)
            }
        }
    }
    UiText("自定义颜色", 12, 19, 500)
    val shape = RoundedCornerShape(12.dp)
    BasicTextField(
        value = hex,
        onValueChange = { if (it.length <= 9) { hex = it.uppercase(Locale.ROOT); invalid = false } },
        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(shape).background(c.surface)
            .border(1.dp, if (invalid) c.red else c.line, shape).padding(horizontal = 14.dp, vertical = 13.dp)
            .semantics { contentDescription = "自定义颜色，十六进制色值"; if (invalid) error("请输入六位十六进制颜色，例如 #4A7696") },
        singleLine = true, textStyle = monitorTextStyle(14, 22).copy(color = c.text), cursorBrush = SolidColor(c.primary),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { applyColor() }),
    )
    UiText(if (invalid) "请输入六位十六进制颜色，例如 #4A7696" else "格式：#RRGGBB", 11, 18, color = if (invalid) c.red else c.muted)
    UiButton("应用颜色", { applyColor() }, secondary = true)
}

@Composable
private fun ScaleControl(settings: AppearanceSettings, onCommit: (Float) -> Unit) {
    val c = LocalMonitorPalette.current
    var draft by remember(settings.scale) { mutableFloatStateOf(settings.scale) }
    val latestDraft by rememberUpdatedState(draft)
    val latestCommit by rememberUpdatedState(onCommit)
    val scaleRange = AppearanceSettings.MIN_SCALE..AppearanceSettings.MAX_SCALE
    SettingGroup {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                UiText("${(draft * 100).roundToInt()}%", 24, 33, 600, modifier = Modifier.weight(1f))
                TextAction("恢复 100%", { draft = 1f; onCommit(1f) }, color = c.primary)
            }
            if (settings.uiStyle == UiStyle.MIUIX) {
                // Miuix 0.3.4 exposes only a changing-value callback. Observe release without
                // consuming the slider's gesture so a density change cannot move it mid-drag.
                Box(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Final)
                                } while (event.changes.any { it.pressed })
                                latestCommit(latestDraft)
                            }
                        }
                        .semantics {
                            contentDescription = "界面缩放"
                            progressBarRangeInfo = ProgressBarRangeInfo(draft, scaleRange)
                            setProgress { requested ->
                                if (!requested.isFinite()) false else {
                                    draft = requested.coerceIn(scaleRange)
                                    onCommit(draft)
                                    true
                                }
                            }
                        }, contentAlignment = Alignment.Center,
                ) {
                    MiuixSlider(
                        progress = draft, onProgressChange = { draft = it },
                        minValue = scaleRange.start, maxValue = scaleRange.endInclusive,
                        modifier = Modifier.fillMaxWidth(), height = 30.dp, effect = true,
                    )
                }
            } else {
                Slider(
                    value = draft, onValueChange = { draft = it }, valueRange = scaleRange,
                    onValueChangeFinished = { onCommit(draft) },
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "界面缩放" },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                UiText("80% · 紧凑", 11, 18, color = c.muted)
                UiText("120% · 宽大", 11, 18, color = c.muted)
            }
            UiText("松开滑块后应用。", 11, 18, color = c.muted)
        }
    }
}

private fun ThemeColorSource.title() = when (this) {
    ThemeColorSource.DEFAULT -> "默认配色"
    ThemeColorSource.SYSTEM -> "系统莫奈"
    ThemeColorSource.CUSTOM -> "自选颜色"
}

private fun ThemePalette.title() = when (this) {
    ThemePalette.TONAL_SPOT -> "柔和"
    ThemePalette.NEUTRAL -> "中性"
    ThemePalette.VIBRANT -> "鲜明"
    ThemePalette.EXPRESSIVE -> "灵动"
    ThemePalette.RAINBOW -> "彩虹"
    ThemePalette.FRUIT_SALAD -> "缤纷"
    ThemePalette.MONOCHROME -> "单色"
    ThemePalette.FIDELITY -> "忠实"
    ThemePalette.CONTENT -> "原色"
}

private fun BottomBarStyle.title() = when (this) {
    BottomBarStyle.FIXED -> "标准"
    BottomBarStyle.FLOATING -> "悬浮"
    BottomBarStyle.GLASS -> "液态玻璃"
}
