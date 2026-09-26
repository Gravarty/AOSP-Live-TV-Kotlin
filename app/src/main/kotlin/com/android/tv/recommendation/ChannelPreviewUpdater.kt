package com.android.tv.recommendation

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.media.tv.TvInputManager
import android.util.Log
import androidx.tvprovider.media.tv.TvContractCompat
import com.android.tv.Starter
import com.android.tv.TvSingletons
import com.android.tv.data.PreviewDataManager
import com.android.tv.data.PreviewProgramContent
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Empfohlene laufende Sendungen als Vorschau-Kanal im Launcher (alle 10 min per JobScheduler).
 * Gesperrte Altersfreigaben sind ohne Systemrechte nicht lesbar; gesperrte Kanäle werden weiter
 * ausgelassen (Kindersicherung per öffentlicher API).
 */
class ChannelPreviewUpdater private constructor(private val context: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val previewDataManager: PreviewDataManager = TvSingletons.getSingletons(context).getPreviewDataManager()
    private var jobService: JobService? = null
    private var jobParams: JobParameters? = null
    private var needUpdateAfterRecommenderReady = false

    private val recommenderListener = object : Recommender.Listener {
        override fun onRecommenderReady() {
            if (needUpdateAfterRecommenderReady) {
                updatePreviewDataForChannelsImmediately()
                needUpdateAfterRecommenderReady = false
            }
        }

        override fun onRecommendationChanged() = updatePreviewDataForChannelsImmediately()
    }

    private val recommender = Recommender(context, recommenderListener, true).apply {
        registerEvaluator(RandomEvaluator(), 0.1, 0.1)
        registerEvaluator(FavoriteChannelEvaluator(), 0.5, 0.5)
        registerEvaluator(RoutineWatchEvaluator(), 1.0, 1.0)
    }

    /** Periodischen Job einplanen (einmalig). */
    fun startRoutineService() {
        val jobScheduler = context.getSystemService(JobScheduler::class.java)
        if (jobScheduler.getPendingJob(UPDATE_PREVIEW_PROGRAMS_JOB_ID) != null) return
        val job = JobInfo.Builder(UPDATE_PREVIEW_PROGRAMS_JOB_ID, ComponentName(context, ChannelPreviewUpdateService::class.java))
            .setPeriodic(ROUTINE_INTERVAL_MS)
            .setPersisted(true)
            .build()
        if (jobScheduler.schedule(job) < 0) Log.i(TAG, "JobScheduler failed to schedule the job")
    }

    internal fun onStartJob(service: JobService, params: JobParameters) {
        jobService = service
        jobParams = params
        updatePreviewDataForChannelsImmediately()
    }

    fun updatePreviewDataForChannelsImmediately() {
        if (!recommender.isReady) {
            needUpdateAfterRecommenderReady = true
            return
        }
        if (!previewDataManager.isLoadFinished) {
            previewDataManager.addListener(object : PreviewDataManager.PreviewDataListener {
                override fun onPreviewDataLoadFinished() {
                    previewDataManager.removeListener(this)
                    updatePreviewDataForChannels()
                }

                override fun onPreviewDataUpdateFinished() {}
            })
            return
        }
        updatePreviewDataForChannels()
    }

    internal fun onStopJob() {
        jobService = null
        jobParams = null
    }

    private fun updatePreviewDataForChannels() {
        scope.launch {
            val programs = withContext(Dispatchers.IO) {
                val result = HashSet<Program>()
                try {
                    for (channel in ArrayList(recommender.recommendChannels())) {
                        if (!channel.isPhysicalTunerChannel) continue
                        val program = Utils.getCurrentProgram(context, channel.id)
                        if (program != null && isChannelRecommendationApplicable(channel, program)) {
                            result.add(program)
                            if (result.size >= RECOMMENDATION_COUNT) break
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Can't update preview data", e)
                }
                result
            }
            updatePreviewDataForChannelsInternal(programs)
        }
    }

    /** Mit Poster, nicht gesperrt und nicht fast vorbei. */
    private fun isChannelRecommendationApplicable(channel: Channel, program: Program): Boolean {
        val programDurationMs = program.endTimeUtcMillis - program.startTimeUtcMillis
        if (programDurationMs <= 0) return false
        if (program.posterArtUri.isNullOrEmpty()) return false
        val parentalEnabled = context.getSystemService(TvInputManager::class.java)?.isParentalControlsEnabled == true
        if (parentalEnabled && channel.isLocked) return false
        val programLeftTimeMs = program.endTimeUtcMillis - System.currentTimeMillis()
        val programProgress = 100 - (programLeftTimeMs * 100 / programDurationMs).toInt()
        return programProgress < RECOMMENDATION_THRESHOLD_PROGRESS || programLeftTimeMs > RECOMMENDATION_THRESHOLD_LEFT_TIME_MS
    }

    private fun updatePreviewDataForChannelsInternal(programs: Set<Program>) {
        val defaultId = previewDataManager.getPreviewChannelId(PreviewDataManager.TYPE_DEFAULT_PREVIEW_CHANNEL.toLong())
        if (defaultId == PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
            // Zeile erst ab 5 Empfehlungen anlegen
            if (programs.size > MIN_COUNT_TO_ADD_ROW) {
                previewDataManager.createDefaultPreviewChannel { createdId ->
                    if (createdId != PreviewDataManager.INVALID_PREVIEW_CHANNEL_ID) {
                        TvContractCompat.requestChannelBrowsable(context, createdId)
                        updatePreviewProgramsForPreviewChannel(createdId, generateContents(createdId, programs))
                    }
                }
            } else {
                finishJob()
            }
        } else {
            updatePreviewProgramsForPreviewChannel(defaultId, generateContents(defaultId, programs))
        }
    }

    private fun generateContents(previewChannelId: Long, programs: Set<Program>): Set<PreviewProgramContent> =
        programs.mapNotNull { PreviewProgramContent.createFromProgram(context, previewChannelId, it) }.toSet()

    private fun updatePreviewProgramsForPreviewChannel(previewChannelId: Long, contents: Set<PreviewProgramContent>) {
        previewDataManager.updatePreviewProgramsForChannel(previewChannelId, contents, object : PreviewDataManager.PreviewDataListener {
            override fun onPreviewDataLoadFinished() {}
            override fun onPreviewDataUpdateFinished() {
                previewDataManager.removeListener(this)
                finishJob()
            }
        })
    }

    private fun finishJob() {
        val service = jobService
        val params = jobParams
        if (service != null && params != null) {
            service.jobFinished(params, false)
            jobService = null
            jobParams = null
        }
    }

    /** JobService für die periodische Aktualisierung. */
    class ChannelPreviewUpdateService : JobService() {
        private lateinit var updater: ChannelPreviewUpdater

        override fun onCreate() {
            Starter.start(this)
            updater = getInstance(this)
        }

        override fun onStartJob(params: JobParameters): Boolean {
            updater.onStartJob(this, params)
            return true
        }

        override fun onStopJob(params: JobParameters): Boolean {
            updater.onStopJob()
            return false
        }
    }

    companion object {
        private const val TAG = "ChannelPreviewUpdater"
        private const val UPDATE_PREVIEW_PROGRAMS_JOB_ID = 1000001
        private val ROUTINE_INTERVAL_MS = TimeUnit.MINUTES.toMillis(10)
        private val RECOMMENDATION_THRESHOLD_LEFT_TIME_MS = TimeUnit.MINUTES.toMillis(10)
        private const val RECOMMENDATION_THRESHOLD_PROGRESS = 90
        private const val RECOMMENDATION_COUNT = 6
        private const val MIN_COUNT_TO_ADD_ROW = 4
        private var instance: ChannelPreviewUpdater? = null

        @JvmStatic
        fun getInstance(context: Context): ChannelPreviewUpdater =
            instance ?: ChannelPreviewUpdater(context.applicationContext).also { instance = it }
    }
}
