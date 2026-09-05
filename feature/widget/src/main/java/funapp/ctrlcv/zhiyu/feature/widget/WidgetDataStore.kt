package funapp.ctrlcv.zhiyu.feature.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository

object WidgetDataStore {
    // No network or credential access: use the app's account-scoped snapshots.
    fun read(context: Context): WidgetUsageData = EntryPointAccessors.fromApplication(
        context.applicationContext, WidgetDataEntryPoint::class.java
    ).usageRepository().getCachedUsage().toWidgetUsageData()
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetDataEntryPoint {
    fun usageRepository(): UsageRepository
}
