package funapp.ctrlcv.zhiyu.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import funapp.ctrlcv.zhiyu.core.data.repository.UsageRepositoryImpl
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    @Singleton
    abstract fun bindUsageRepository(impl: UsageRepositoryImpl): UsageRepository
}
