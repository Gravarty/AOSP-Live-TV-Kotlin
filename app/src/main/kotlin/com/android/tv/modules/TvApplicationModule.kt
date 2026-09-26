package com.android.tv.modules

import android.content.ContentResolver
import android.content.Context
import com.android.tv.common.concurrent.NamedThreadFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher

/** Einzelner DB-Thread "tv-app-db" wie im Original. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DbExecutor

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DbDispatcher

@Module
@InstallIn(SingletonComponent::class)
object TvApplicationModule {
    @Provides
    @Singleton
    @DbExecutor
    fun provideDbExecutor(): Executor = Executors.newSingleThreadExecutor(NamedThreadFactory("tv-app-db"))

    @Provides
    @Singleton
    @DbDispatcher
    fun provideDbDispatcher(@DbExecutor executor: Executor): CoroutineDispatcher = executor.asCoroutineDispatcher()

    @Provides
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver = context.contentResolver
}
