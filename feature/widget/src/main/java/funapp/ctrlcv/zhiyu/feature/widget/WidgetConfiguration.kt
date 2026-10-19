package funapp.ctrlcv.zhiyu.feature.widget

import funapp.ctrlcv.zhiyu.core.domain.model.Platform

enum class WidgetKind(val title: String, val sizeLabel: String, val description: String, val singleAccount: Boolean) {
    OVERVIEW("订阅总览", "4 × 4", "把多个账户的额度与余额集中在一起，列表可滚动。", false),
    DETAIL("订阅详情", "4 × 2", "查看一个账户的主要额度窗口、重置时间或余额明细。", true),
    COMPACT("订阅卡片", "2 × 2", "突出一个账户的主指标与下一次重置时间。", true),
    STRIP("紧凑监控栏", "4 × 1", "一行查看常用账户，适合放在桌面顶部。", false),
}

enum class WidgetSelectionMode { ALL, PINNED, CUSTOM }

data class WidgetConfiguration(
    val mode: WidgetSelectionMode = WidgetSelectionMode.ALL,
    val accountKeys: Set<String> = emptySet(),
) {
    companion object {
        fun defaultFor(kind: WidgetKind) = WidgetConfiguration(
            mode = if (kind.singleAccount) WidgetSelectionMode.CUSTOM else WidgetSelectionMode.ALL,
        )
    }
}

data class WidgetAccountReference(val platform: Platform, val accountId: String) {
    val key: String get() = "${platform.key}/$accountId"

    companion object {
        fun parse(key: String): WidgetAccountReference? {
            val platform = Platform.entries.firstOrNull { it.key == key.substringBefore('/') } ?: return null
            val id = key.substringAfter('/', "").takeIf { it.isNotBlank() } ?: return null
            return WidgetAccountReference(platform, id)
        }
    }
}

fun WidgetUsageData.selected(configuration: WidgetConfiguration, kind: WidgetKind): List<WidgetPlatformItem> {
    val selected = when {
        kind.singleAccount -> items.filter { it.accountKey in configuration.accountKeys }
        configuration.mode == WidgetSelectionMode.ALL -> items
        configuration.mode == WidgetSelectionMode.PINNED -> items.filter { it.pinned }
        else -> items.filter { it.accountKey in configuration.accountKeys }
    }
    return if (kind.singleAccount) selected.take(1) else selected
}
