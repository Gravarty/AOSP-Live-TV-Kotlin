package com.android.tv.dvr.provider

import android.content.Context
import android.database.Cursor
import android.media.tv.TvContract
import android.media.tv.TvContract.Programs
import android.net.Uri
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.PermissionUtils
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeasonEpisodeNumber
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.util.TvProviderUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Lädt zukünftige Folgen einer/mehrerer Serien aus dem EPG (optional inkl. laufender Sendung,
 * bereits geplanter und abgelehnter Folgen). AsyncProgramQueryTask → Coroutine.
 * Bugfix: Ohne Systemrecht ließ der Filter nur Sendungen MIT Aufnahmeverbot durch (Bedingung
 * war vertauscht) – jetzt wie mit Systemrecht nur Sendungen ohne Aufnahmeverbot.
 */
abstract class EpisodicProgramLoadTask(context: Context, seriesRecordings: Collection<SeriesRecording>) {
    constructor(context: Context, seriesRecording: SeriesRecording) : this(context, listOf(seriesRecording))

    private val context = context.applicationContext
    private val dataManager: DvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
    val seriesRecordings: List<SeriesRecording> = ArrayList(seriesRecordings)
    private var queryAllChannels = false
    private var loadCurrentProgram = false
    private var loadScheduledEpisode = false
    private var loadDisallowedProgram = false
    private var ignoreChannelOption = false
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val isRunning: Boolean get() = job?.isActive == true

    private fun checkNotStarted() = SoftPreconditions.checkState(job == null, TAG, "Can't change setting after execution.")

    fun setLoadCurrentProgram(v: Boolean) = apply { checkNotStarted(); loadCurrentProgram = v }
    fun setLoadScheduledEpisode(v: Boolean) = apply { checkNotStarted(); loadScheduledEpisode = v }
    fun setLoadDisallowedProgram(v: Boolean) = apply { checkNotStarted(); loadDisallowedProgram = v }
    fun setIgnoreChannelOption(v: Boolean) = apply { checkNotStarted(); ignoreChannelOption = v }

    fun execute() {
        if (!SoftPreconditions.checkState(job == null, TAG, "Can't execute task: the task is already running.")) return
        queryAllChannels = seriesRecordings.size > 1 ||
            seriesRecordings[0].channelOption == SeriesRecording.OPTION_CHANNEL_ALL || ignoreChannelOption
        val params = createSqlParams()
        val dbDispatcher = TvSingletons.getSingletons(context).getDbDispatcher()
        job = scope.launch {
            val programs = withContext(dbDispatcher) { query(params) }
            if (programs != null) onPostExecute(programs)
        }
    }

    fun cancel(mayInterruptIfRunning: Boolean) {
        val j = job ?: return
        if (j.isActive) {
            j.cancel()
            onCancelled(null)
        }
    }

    protected open fun onPostExecute(programs: List<Program>) {}
    protected open fun onCancelled(programs: List<Program>?) {}

    @WorkerThread
    private suspend fun query(params: SqlParams): List<Program>? {
        var projection = ProgramImpl.PROJECTION
        if (TvProviderUtils.checkSeriesIdColumn(context, Programs.CONTENT_URI)) {
            projection = TvProviderUtils.addExtraColumnsToProjection(projection, TvProviderUtils.EXTRA_PROGRAM_COLUMN_SERIES_ID)
        }
        return try {
            context.contentResolver.query(params.uri, projection, params.selection, params.selectionArgs, null)?.use { c ->
                val list = ArrayList<Program>()
                while (c.moveToNext()) {
                    kotlin.coroutines.coroutineContext.ensureActive()
                    if (params.filter.apply(c)) list.add(ProgramImpl.fromCursor(c))
                }
                list
            } ?: emptyList()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Error querying ${params.uri}", e)
            null
        }
    }

    /** Mit ALL_EPG per SQL filtern, sonst per Zeitraum-URI und Cursor-Filter. */
    private fun createSqlParams(): SqlParams {
        val now = System.currentTimeMillis()
        if (PermissionUtils.hasAccessAllEpg(context)) {
            val selection = StringBuilder(if (loadCurrentProgram) PROGRAM_PREDICATE_WITH_CURRENT_PROGRAM else PROGRAM_PREDICATE)
            val args = arrayListOf(now.toString())
            if (!queryAllChannels) {
                selection.append(" AND ").append(CHANNEL_ID_PREDICATE)
                args.add(seriesRecordings[0].channelId.toString())
            }
            if (seriesRecordings.size == 1) {
                selection.append(" AND ").append(PROGRAM_TITLE_PREDICATE)
                args.add(seriesRecordings[0].title.orEmpty())
            }
            return SqlParams(Programs.CONTENT_URI, selection.toString(), args.toTypedArray(), SeriesRecordingCursorFilter())
        }
        val uri = if (queryAllChannels) {
            Programs.CONTENT_URI.buildUpon()
                .appendQueryParameter(PARAM_START_TIME, now.toString())
                .appendQueryParameter(PARAM_END_TIME, Long.MAX_VALUE.toString()).build()
        } else {
            TvContract.buildProgramsUriForChannel(seriesRecordings[0].channelId, now, Long.MAX_VALUE)
        }
        return SqlParams(uri, null, null, SeriesRecordingCursorFilterForNonSystem())
    }

    private open inner class SeriesRecordingCursorFilter {
        private val disallowedProgramIds = HashSet<Long>()
        private val seasonEpisodeNumbers = HashSet<SeasonEpisodeNumber>()

        init {
            if (!loadDisallowedProgram) disallowedProgramIds.addAll(dataManager.getDisallowedProgramIds())
            if (!loadScheduledEpisode) {
                val ids = seriesRecordings.map { it.id }.toSet()
                for (r in dataManager.getAllScheduledRecordings()) {
                    if (r.seriesRecordingId in ids && r.state != ScheduledRecording.STATE_RECORDING_FAILED &&
                        r.state != ScheduledRecording.STATE_RECORDING_CLIPPED
                    ) seasonEpisodeNumbers.add(SeasonEpisodeNumber(r))
                }
            }
        }

        /** Passt zu einer Serie und ist (optional) noch nicht geplant bzw. abgelehnt. */
        @WorkerThread
        open fun apply(c: Cursor): Boolean {
            if (!loadDisallowedProgram && c.getLong(PROGRAM_ID_INDEX) in disallowedProgramIds) return false
            val program = ProgramImpl.fromCursor(c)
            for (series in seriesRecordings) {
                val matches = if (ignoreChannelOption) series.matchProgram(program, SeriesRecording.OPTION_CHANNEL_ALL)
                else series.matchProgram(program)
                if (matches) {
                    return loadScheduledEpisode ||
                        SeasonEpisodeNumber(series.id, program.seasonNumber, program.episodeNumber) !in seasonEpisodeNumbers
                }
            }
            return false
        }
    }

    private inner class SeriesRecordingCursorFilterForNonSystem : SeriesRecordingCursorFilter() {
        override fun apply(c: Cursor): Boolean =
            (loadCurrentProgram || c.getLong(START_TIME_INDEX) > System.currentTimeMillis()) &&
                c.getInt(RECORDING_PROHIBITED_INDEX) == 0 && super.apply(c)
    }

    private class SqlParams(val uri: Uri, val selection: String?, val selectionArgs: Array<String>?, val filter: SeriesRecordingCursorFilter)

    companion object {
        private const val TAG = "EpisodicProgramLoadTask"
        private val PROGRAM_ID_INDEX = ProgramImpl.PROJECTION.indexOf(Programs._ID)
        private val START_TIME_INDEX = ProgramImpl.PROJECTION.indexOf(Programs.COLUMN_START_TIME_UTC_MILLIS)
        private val RECORDING_PROHIBITED_INDEX = ProgramImpl.PROJECTION.indexOf(Programs.COLUMN_RECORDING_PROHIBITED)
        private const val PARAM_START_TIME = "start_time"
        private const val PARAM_END_TIME = "end_time"
        private const val PROGRAM_PREDICATE = Programs.COLUMN_START_TIME_UTC_MILLIS + ">? AND " + Programs.COLUMN_RECORDING_PROHIBITED + "=0"
        private const val PROGRAM_PREDICATE_WITH_CURRENT_PROGRAM =
            Programs.COLUMN_END_TIME_UTC_MILLIS + ">? AND " + Programs.COLUMN_RECORDING_PROHIBITED + "=0"
        private const val CHANNEL_ID_PREDICATE = Programs.COLUMN_CHANNEL_ID + "=?"
        private const val PROGRAM_TITLE_PREDICATE = Programs.COLUMN_TITLE + "=?"
    }
}
