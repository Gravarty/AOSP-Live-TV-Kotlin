package com.android.tv.dvr

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Hilt: DVR-Schnittstellen auf die Implementierungen abbilden. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DvrModule {
    @Binds abstract fun bindDvrDataManager(impl: DvrDataManagerImpl): DvrDataManager
    @Binds abstract fun bindWritableDvrDataManager(impl: DvrDataManagerImpl): WritableDvrDataManager
}
