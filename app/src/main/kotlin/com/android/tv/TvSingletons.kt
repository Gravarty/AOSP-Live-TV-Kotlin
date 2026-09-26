package com.android.tv

import android.content.Context
import com.android.tv.common.util.Clock
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.ProgramDataManager
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.recorder.RecordingScheduler
import com.android.tv.dvr.RecordingStorageStatusManager
import com.android.tv.data.PreviewDataManager
import com.android.tv.util.SetupUtils
import com.android.tv.util.TvInputManagerHelper
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher

/** App-weite Singletons (wie im Original von TvApplication implementiert). */
interface TvSingletons {
    fun getTvInputManagerHelper(): TvInputManagerHelper
    fun getChannelDataManager(): ChannelDataManager
    fun getProgramDataManager(): ProgramDataManager
    fun getClock(): Clock
    fun getDbDispatcher(): CoroutineDispatcher
    fun getSetupUtils(): SetupUtils
    fun getInputSessionManager(): InputSessionManager
    fun getMainActivityWrapper(): MainActivityWrapper
    fun getPreviewDataManager(): PreviewDataManager
    /** null ohne DVR. */
    fun getDvrManager(): DvrManager?
    fun getDvrDataManager(): DvrDataManager
    fun getDvrScheduleManager(): DvrScheduleManager?
    fun getDvrWatchedPositionManager(): DvrWatchedPositionManager
    fun getRecordingScheduler(): RecordingScheduler?
    fun getRecordingStorageStatusManager(): RecordingStorageStatusManager

    companion object {
        @JvmStatic
        fun getSingletons(context: Context): TvSingletons = context.applicationContext as TvSingletons
    }
}

@Module
@InstallIn(SingletonComponent::class)
object ClockModule {
    @Provides
    fun provideClock(): Clock = Clock.SYSTEM
}
