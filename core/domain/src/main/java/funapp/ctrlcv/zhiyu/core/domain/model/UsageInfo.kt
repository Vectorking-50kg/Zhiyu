package funapp.ctrlcv.zhiyu.core.domain.model

data class UsageInfo(
    val platform: Platform,
    val items: List<UsageItem>,
    val planLabel: String? = null,
    val resetInfo: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
    val stale: Boolean = false,
    // ChatGPT 限额「重置卡」（rate-limit reset credits）：使用后可立即重置 5 小时 / 周限额的官方道具。
    // 仅 ChatGPT 平台会填充；其余平台为 null。
    val resetCredits: ResetCredits? = null,
    val accountId: String? = null,
    val providerAccountId: String? = null,
    val refreshFailure: UsageFailure? = null
)

/**
 * ChatGPT 重置卡账户状态。
 *
 * @param availableCount 当前可用张数。
 * @param credits 每张卡的明细（含到期时间），按到期时间升序；官方未下发明细时为空。
 */
data class ResetCredits(
    val availableCount: Int,
    val credits: List<ResetCredit> = emptyList()
)

/**
 * 单张 ChatGPT 重置卡。
 *
 * @param expiresAt 到期时间（epoch 毫秒），过期后不可用。
 * @param grantedAt 发放时间（epoch 毫秒），用于估算剩余寿命；官方未下发时为 null。
 */
data class ResetCredit(
    val expiresAt: Long,
    val grantedAt: Long? = null
)

data class UsageItem(
    val label: String,
    // -1f = 纯信息行（不显示进度条，用 valueText 展示）; 0-100 = 百分比
    val percent: Float,
    val resetCountdown: String? = null,
    // 当 percent < 0 时作为主要展示内容（如 "¥10.50"）；
    // 当 percent >= 0 时作为进度条下方的补充说明
    val valueText: String? = null,
    // MiniMax Token Plan 专用：该窗口为无上限（无限制），不展示百分比进度
    val unlimited: Boolean = false,
    // MiniMax Token Plan 专用：boost 提升后的总额度百分比（如 200 表示「总额度 200%」），仅在有提升时展示
    val boostPercent: Int? = null,
    // 该限额窗口已经过去的时间比例（0-100）；仅在窗口总时长固定已知（5 小时 / 7 天）时提供，
    // 所有主题共用的双层进度条数据（深色=用量，浅色=时间）
    val elapsedPercent: Float? = null,
    /** Absolute provider reset time in epoch milliseconds, retained in cached snapshots. */
    val resetAt: Long? = null,
    val windowDurationSeconds: Long? = null,
    val windowId: String? = null
)
