package funapp.ctrlcv.zhiyu

import android.app.Application
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import funapp.ctrlcv.zhiyu.core.data.cache.UsageCache
import funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences
import funapp.ctrlcv.zhiyu.core.domain.model.Account
import funapp.ctrlcv.zhiyu.core.domain.model.Platform
import funapp.ctrlcv.zhiyu.core.domain.model.ResetCredit
import funapp.ctrlcv.zhiyu.core.domain.model.ResetCredits
import funapp.ctrlcv.zhiyu.core.domain.model.UsageInfo
import funapp.ctrlcv.zhiyu.core.domain.model.UsageItem
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.storage.SecureTokenStore

/**
 * Populates the isolated demo application with representative production-shaped states.
 * The demo variant has its own application id, so no real account or credential is touched.
 */
internal object DemoUsageSeeder {
    fun seed(application: Application) {
        val dependencies = EntryPointAccessors.fromApplication(
            application,
            DemoUsageSeederDependencies::class.java,
        )
        val cache = dependencies.usageCache()
        val accountStore = dependencies.accountStore()
        val tokenStore = dependencies.secureTokenStore()
        val notificationPreferences = dependencies.notificationPreferences()
        val now = System.currentTimeMillis()
        demoUsage(now).forEach { usage -> cache.save(usage.platform, usage) }
        seedSettings(accountStore, tokenStore, notificationPreferences)
    }

    private fun seedSettings(
        accountStore: AccountStore,
        tokenStore: SecureTokenStore,
        notificationPreferences: NotificationPreferences,
    ) {
        // Keep one row per platform so UsageRepository returns every cached demo card.
        // The account id intentionally differs from the API settings' "default" token slot:
        // refreshes therefore fall back to cache without sending placeholder credentials.
        Platform.displayOrder.forEach { platform ->
            accountStore.saveAccount(
                Account(
                    id = "demo",
                    platform = platform,
                    displayName = "${platform.displayName} Demo",
                    planType = when (platform) {
                        Platform.CLAUDE -> "Max 5×"
                        Platform.CHATGPT -> "Plus"
                        Platform.CURSOR -> "Pro"
                        else -> if (platform.requiresApiKey) "API" else ""
                    },
                )
            )
        }

        // API settings read token presence directly. These are inert placeholders in the
        // separate demo app and are deliberately not valid credential-shaped values.
        tokenStore.save(Platform.MINIMAX, "default", "demo-placeholder")
        tokenStore.save(Platform.DEEPSEEK, "default", "demo-placeholder")

        notificationPreferences.persistentEnabled = true
        notificationPreferences.usageAlertEnabled = true
        notificationPreferences.resetReminderEnabled = true
        notificationPreferences.sessionExpiredAlertEnabled = false
        Platform.displayOrder.forEach { notificationPreferences.setPinned(it, false) }
        notificationPreferences.setPinned(Platform.CHATGPT, true)
        notificationPreferences.setPinned(Platform.MINIMAX, true)
    }

    private fun demoUsage(now: Long): List<UsageInfo> = listOf(
        UsageInfo(
            platform = Platform.CHATGPT,
            planLabel = "Plus",
            items = listOf(
                UsageItem("5 小时限额", 84f, resetCountdown = "2小时12分钟后重置", elapsedPercent = 56f),
                UsageItem("周限额", 48f, resetCountdown = "4天后重置", elapsedPercent = 3f / 7f * 100f),
                UsageItem("Code Review｜周", 17f, resetCountdown = "6天后重置", elapsedPercent = 1f / 7f * 100f),
                UsageItem("续订时间", -1f, valueText = "21天后"),
            ),
            resetCredits = ResetCredits(
                availableCount = 2,
                credits = listOf(
                    ResetCredit(expiresAt = now + 9L * DAY_MS),
                    ResetCredit(expiresAt = now + 23L * DAY_MS),
                ),
            ),
            updatedAt = now - 45_000,
        ),
        UsageInfo(
            platform = Platform.CLAUDE,
            planLabel = "Max 5×",
            items = listOf(
                UsageItem("5 小时限额", 62f, resetCountdown = "1小时48分钟后重置", elapsedPercent = 64f),
                UsageItem("周限额｜所有模型", 78f, resetCountdown = "3天后重置", elapsedPercent = 4f / 7f * 100f),
                UsageItem("周限额｜Opus", 93f, resetCountdown = "3天后重置", elapsedPercent = 4f / 7f * 100f),
            ),
            updatedAt = now,
        ),
        UsageInfo(
            platform = Platform.CURSOR,
            planLabel = "Pro",
            items = listOf(
                UsageItem(
                    "本周期用量",
                    37f,
                    resetCountdown = "12天后重置",
                    valueText = "$18.50 / $50.00",
                ),
                UsageItem("Auto 用量", 68f),
                UsageItem("API 用量", 12f),
            ),
            updatedAt = now - 2 * 60_000,
        ),
        UsageInfo(
            platform = Platform.ZEN,
            items = listOf(
                UsageItem("账户余额", -1f, valueText = "$18.42"),
            ),
            updatedAt = now - 3 * 60_000,
        ),
        UsageInfo(
            platform = Platform.MINIMAX,
            items = listOf(
                UsageItem("5 小时限额", 0f, unlimited = true),
                UsageItem(
                    "周限额",
                    71f,
                    resetCountdown = "2天后重置",
                    boostPercent = 200,
                    elapsedPercent = 5f / 7f * 100f,
                ),
            ),
            updatedAt = now - 4 * 60_000,
        ),
        UsageInfo(
            platform = Platform.AIHUBMIX,
            items = listOf(
                UsageItem("余额", -1f, valueText = "$126.8000"),
                UsageItem("已消费", -1f, valueText = "$73.2000"),
                UsageItem("累计请求次数", -1f, valueText = "12486 次"),
            ),
            updatedAt = now - 5 * 60_000,
        ),
        UsageInfo(
            platform = Platform.DEEPSEEK,
            items = listOf(
                UsageItem("账户余额", -1f, valueText = "¥86.32"),
                UsageItem("赠送余额", -1f, valueText = "¥12.00"),
                UsageItem("充值余额", -1f, valueText = "¥74.32"),
            ),
            resetInfo = "账户可用",
            updatedAt = now - 6 * 60_000,
        ),
    )

    private const val DAY_MS = 24L * 60 * 60 * 1_000
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface DemoUsageSeederDependencies {
    fun usageCache(): UsageCache
    fun accountStore(): AccountStore
    fun secureTokenStore(): SecureTokenStore
    fun notificationPreferences(): NotificationPreferences
}
