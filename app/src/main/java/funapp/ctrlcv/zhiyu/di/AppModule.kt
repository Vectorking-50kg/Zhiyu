package funapp.ctrlcv.zhiyu.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import funapp.ctrlcv.zhiyu.core.domain.usecase.GetAllUsageUseCase
import funapp.ctrlcv.zhiyu.core.domain.usecase.GetUsageUseCase
import funapp.ctrlcv.zhiyu.core.domain.usecase.UsageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGetUsageUseCase(repository: UsageRepository): GetUsageUseCase {
        return GetUsageUseCase(repository)
    }

    @Provides
    @Singleton
    fun provideGetAllUsageUseCase(repository: UsageRepository): GetAllUsageUseCase {
        return GetAllUsageUseCase(repository)
    }
}
