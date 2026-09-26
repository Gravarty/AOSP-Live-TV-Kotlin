package com.android.tv.dvr.recorder

import android.annotation.SuppressLint
import android.content.Context
import android.util.ArraySet
import android.util.LongSparseArray
import androidx.annotation.MainThread
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.WritableDvrDataManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeasonEpisodeNumber
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.EpisodicProgramLoadTask

/**
 * Plant neue Folgen aktiver Serien aus dem EPG (eine Ausstrahlung je Folge) und plant bei
 * fehlgeschlagenen/abgeschnittenen Folgen nach.
 * Entfällt: Serien-Infos aus dem Cloud-EPG (EpgReader ist im AOSP-Build ein leerer Stub).
 * Bugfix: Nach resumeUpdate() wurde die Warteliste nie geleert.
 */
class SeriesRecordingScheduler private constructor(context: Context) {

    private val context = context.applicationContext
    private val dvrManager: DvrManager by lazy { TvSingletons.getSingletons(this.context).getDvrManager()!! }
    private val dataManager = TvSingletons.getSingletons(context).getDvrDataManager() as WritableDvrDataManager
    private val scheduleTasks = ArrayList<SeriesRecordingUpdateTask>()
    private var started = false
    private var paused = false
    private val pendingSeriesRecordings = ArraySet<Long>()

    private val seriesRecordingListener = object : DvrDataManager.SeriesRecordingListener {
        override fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording) {}

        override fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording) {
            val removedIds = seriesRecordings.map { it.id }.toSet()
            val iter = scheduleTasks.iterator()
            while (iter.hasNext()) {
                val task = iter.next()
                // Aufgabe nur für entfernte Serien: abbrechen
                if (task.seriesRecordings.all { it.id in removedIds }) {
                    task.cancel(true)
                    iter.remove()
                }
            }
        }

        override fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording) {
            val (stopped, normal) = seriesRecordings.partition { it.isStopped }
            if (stopped.isNotEmpty()) onSeriesRecordingRemoved(*stopped.toTypedArray())
            if (normal.isNotEmpty()) updateSchedules(normal)
        }
    }

    private val scheduledRecordingListener = object : DvrDataManager.ScheduledRecordingListener {
        override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {}

        override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) =
            handleScheduledRecordingChange(scheduledRecordings.toList())

        /** Fehlgeschlagene/abgeschnittene Folgen mit Staffel+Folge: andere Ausstrahlung suchen. */
        override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
            val forUpdate = scheduledRecordings.filter {
                (it.state == ScheduledRecording.STATE_RECORDING_FAILED || it.state == ScheduledRecording.STATE_RECORDING_CLIPPED) &&
                    it.seriesRecordingId != SeriesRecording.ID_NOT_SET && !it.seasonNumber.isNullOrEmpty() && !it.episodeNumber.isNullOrEmpty()
            }
            if (forUpdate.isNotEmpty()) handleScheduledRecordingChange(forUpdate)
        }

        private fun handleScheduledRecordingChange(schedules: List<ScheduledRecording>) {
            val series = schedules.map { it.seriesRecordingId }.filter { it != SeriesRecording.ID_NOT_SET }.toSet()
                .mapNotNull { dataManager.getSeriesRecording(it) }
            if (series.isNotEmpty()) updateSchedules(series)
        }
    }

    @MainThread
    fun start() {
        SoftPreconditions.checkState(dataManager.isInitialized)
        if (started) return
        started = true
        dataManager.addSeriesRecordingListener(seriesRecordingListener)
        dataManager.addScheduledRecordingListener(scheduledRecordingListener)
        updateSchedules(dataManager.getSeriesRecordings())
    }

    @MainThread
    fun stop() {
        if (!started) return
        started = false
        scheduleTasks.forEach { it.cancel(true) }
        scheduleTasks.clear()
        dataManager.removeScheduledRecordingListener(scheduledRecordingListener)
        dataManager.removeSeriesRecordingListener(seriesRecordingListener)
    }

    /** Pausieren, solange DvrDbSync Sendungen prüft. */
    fun pauseUpdate() {
        if (paused) return
        paused = true
        if (!started) return
        for (task in scheduleTasks) {
            task.seriesRecordings.forEach { pendingSeriesRecordings.add(it.id) }
            task.cancel(true)
        }
    }

    fun resumeUpdate() {
        if (!paused) return
        paused = false
        if (!started) return
        if (pendingSeriesRecordings.isNotEmpty()) {
            val series = pendingSeriesRecordings.mapNotNull { dataManager.getSeriesRecording(it) }
            pendingSeriesRecordings.clear()
            if (series.isNotEmpty()) updateSchedules(series)
        }
    }

    /** Laufende Aufgaben dieser Serien ersetzen und neu laden. */
    fun updateSchedules(seriesRecordings: Collection<SeriesRecording>) {
        if (!started) return
        if (paused) {
            seriesRecordings.forEach { pendingSeriesRecordings.add(it.id) }
            return
        }
        val ids = seriesRecordings.map { it.id }.toSet()
        val previous = ArrayList<SeriesRecording>()
        val iter = scheduleTasks.iterator()
        while (iter.hasNext()) {
            val task = iter.next()
            if (task.seriesRecordings.any { it.id in ids }) {
                task.cancel(true)
                previous.addAll(task.seriesRecordings)
                iter.remove()
            }
        }
        val toUpdate = (seriesRecordings + previous).distinctBy { it.id }
            .filter { dataManager.getSeriesRecording(it.id)?.isStopped == false }
        if (toUpdate.isEmpty()) return
        if (toUpdate.any { it.channelOption == SeriesRecording.OPTION_CHANNEL_ALL }) {
            SeriesRecordingUpdateTask(toUpdate).also { scheduleTasks.add(it) }.execute()
        } else {
            toUpdate.forEach { SeriesRecordingUpdateTask(listOf(it)).also { t -> scheduleTasks.add(t) }.execute() }
        }
    }

    private inner class SeriesRecordingUpdateTask(seriesRecordings: List<SeriesRecording>) :
        EpisodicProgramLoadTask(context, seriesRecordings) {

        override fun onPostExecute(programs: List<Program>) {
            scheduleTasks.remove(this)
            val map = pickOneProgramPerEpisode(dataManager, seriesRecordings, programs)
            for (series in seriesRecordings) {
                val actual = dataManager.getSeriesRecording(series.id)
                if (actual == null || actual.isStopped) continue
                val toSchedule = map.get(series.id)
                if (!toSchedule.isNullOrEmpty()) dvrManager.addScheduleToSeriesRecording(series, toSchedule)
            }
        }

        override fun onCancelled(programs: List<Program>?) {
            scheduleTasks.remove(this)
        }

        override fun toString() = "SeriesRecordingUpdateTask:{series_recordings=$seriesRecordings}"
    }

    companion object {
        private const val TAG = "SeriesRecordingSchd"
        @SuppressLint("StaticFieldLeak")
        private var instance: SeriesRecordingScheduler? = null

        @JvmStatic
        @Synchronized
        fun getInstance(context: Context): SeriesRecordingScheduler =
            instance ?: SeriesRecordingScheduler(context).also { instance = it }

        /**
         * Je Folge eine Ausstrahlung: bereits geplante bevorzugen, sonst die früheste.
         * Sendungen ohne Staffel/Folge werden alle übernommen.
         */
        @JvmStatic
        fun pickOneProgramPerEpisode(dataManager: DvrDataManager, seriesRecordings: List<SeriesRecording>, programs: List<Program>): LongSparseArray<MutableList<Program>> {
            val result = LongSparseArray<MutableList<Program>>()
            val seriesIds = HashMap<String?, Long>()
            for (s in seriesRecordings) {
                result.put(s.id, ArrayList())
                seriesIds[s.seriesId] = s.id
            }
            val perEpisode = HashMap<SeasonEpisodeNumber, MutableList<Program>>()
            for (program in programs) {
                // Bugfix-Absicherung: Sendung ohne passende Serie überspringen (Original: NPE)
                val seriesRecordingId = seriesIds[program.seriesId] ?: continue
                if (program.seasonNumber.isNullOrEmpty() || program.episodeNumber.isNullOrEmpty()) {
                    result.get(seriesRecordingId).add(program)
                    continue
                }
                perEpisode.getOrPut(SeasonEpisodeNumber(seriesRecordingId, program.seasonNumber, program.episodeNumber)) { ArrayList() }.add(program)
            }
            for ((key, list) in perEpisode) {
                list.sortWith { lhs, rhs ->
                    val l = isProgramScheduled(dataManager, lhs)
                    val r = isProgramScheduled(dataManager, rhs)
                    when {
                        l && !r -> -1
                        !l && r -> 1
                        else -> lhs.compareTo(rhs)
                    }
                }
                var added = false
                val forSeries = result.get(key.seriesRecordingId)
                for (program in list) {
                    if (isProgramScheduled(dataManager, program)) {
                        forSeries.add(program)
                        added = true
                    } else if (!added) {
                        forSeries.add(program)
                        break
                    }
                }
            }
            return result
        }

        private fun isProgramScheduled(dataManager: DvrDataManager, program: Program): Boolean =
            dataManager.getScheduledRecordingForProgramId(program.id)?.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED
    }
}
