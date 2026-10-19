package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import funapp.ctrlcv.zhiyu.core.storage.AccountStore
import funapp.ctrlcv.zhiyu.core.data.notification.NotificationPreferences

object WidgetDataStore {
    // No network or credential access: use the app's account-scoped snapshots.
    fun dependencies(context: Context): WidgetDataEntryPoint = EntryPointAccessors.fromApplication(
        context.applicationContext, WidgetDataEntryPoint::class.java
    )

    fun read(context: Context): WidgetUsageData {
        val dependencies = dependencies(context)
        return widgetUsageData(
            dependencies.accountStore().getAllAccounts(),
            dependencies.usageRepository().getCachedUsage(),
            dependencies.notificationPreferences().pinnedPlatforms(),
        )
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun usageRepository(): UsageRepository
    fun accountStore(): AccountStore
    fun notificationPreferences(): NotificationPreferences
}
