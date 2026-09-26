package com.android.tv.dvr

import android.util.ArraySet
import androidx.annotation.MainThread
import com.android.tv.common.util.Clock
import com.android.tv.dvr.DvrDataManager.OnDvrScheduleLoadFinishedListener
import com.android.tv.dvr.DvrDataManager.OnRecordedProgramLoadFinishedListener
import com.android.tv.dvr.DvrDataManager.RecordedProgramListener
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener
import com.android.tv.dvr.DvrDataManager.SeriesRecordingListener
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Listener-Verwaltung und gemeinsame Abfragen der DVR-Daten.
 * Bugfix: Benachrichtigungen iterieren über eine Kopie (Listener, die sich im Callback abmelden,
 * lösten sonst eine ConcurrentModificationException aus).
 */
@MainThread
abstract class BaseDvrDataManager(protected val clock: Clock) : WritableDvrDataManager {

    private val onDvrScheduleLoadFinishedListeners = CopyOnWriteArraySet<OnDvrScheduleLoadFinishedListener>()
    private val onRecordedProgramLoadFinishedListeners = CopyOnWriteArraySet<OnRecordedProgramLoadFinishedListener>()
    private val scheduledRecordingListeners = ArraySet<ScheduledRecordingListener>()
    private val seriesRecordingListeners = ArraySet<SeriesRecordingListener>()
    private val recordedProgramListeners = ArraySet<RecordedProgramListener>()
    /** Gelöschte Aufnahmen je Sendungs-ID (nicht erneut planen). */
    protected val deletedScheduleMap = HashMap<Long, ScheduledRecording>()

    override fun addDvrScheduleLoadFinishedListener(listener: OnDvrScheduleLoadFinishedListener) { onDvrScheduleLoadFinishedListeners.add(listener) }
    override fun removeDvrScheduleLoadFinishedListener(listener: OnDvrScheduleLoadFinishedListener) { onDvrScheduleLoadFinishedListeners.remove(listener) }
    override fun addRecordedProgramLoadFinishedListener(listener: OnRecordedProgramLoadFinishedListener) { onRecordedProgramLoadFinishedListeners.add(listener) }
    override fun removeRecordedProgramLoadFinishedListener(listener: OnRecordedProgramLoadFinishedListener) { onRecordedProgramLoadFinishedListeners.remove(listener) }
    final override fun addScheduledRecordingListener(listener: ScheduledRecordingListener) { scheduledRecordingListeners.add(listener) }
    final override fun removeScheduledRecordingListener(listener: ScheduledRecordingListener) { scheduledRecordingListeners.remove(listener) }
    final override fun addSeriesRecordingListener(listener: SeriesRecordingListener) { seriesRecordingListeners.add(listener) }
    final override fun removeSeriesRecordingListener(listener: SeriesRecordingListener) { seriesRecordingListeners.remove(listener) }
    final override fun addRecordedProgramListener(listener: RecordedProgramListener) { recordedProgramListeners.add(listener) }
    final override fun removeRecordedProgramListener(listener: RecordedProgramListener) { recordedProgramListeners.remove(listener) }

    protected fun notifyDvrScheduleLoadFinished() = onDvrScheduleLoadFinishedListeners.forEach { it.onDvrScheduleLoadFinished() }
    protected fun notifyRecordedProgramLoadFinished() = onRecordedProgramLoadFinishedListeners.forEach { it.onRecordedProgramLoadFinished() }
    protected fun notifyRecordedProgramsAdded(vararg p: RecordedProgram) = recordedProgramListeners.toList().forEach { it.onRecordedProgramsAdded(*p) }
    protected fun notifyRecordedProgramsChanged(vararg p: RecordedProgram) = recordedProgramListeners.toList().forEach { it.onRecordedProgramsChanged(*p) }
    protected fun notifyRecordedProgramsRemoved(vararg p: RecordedProgram) = recordedProgramListeners.toList().forEach { it.onRecordedProgramsRemoved(*p) }
    protected fun notifySeriesRecordingAdded(vararg s: SeriesRecording) = seriesRecordingListeners.toList().forEach { it.onSeriesRecordingAdded(*s) }
    protected fun notifySeriesRecordingRemoved(vararg s: SeriesRecording) = seriesRecordingListeners.toList().forEach { it.onSeriesRecordingRemoved(*s) }
    protected fun notifySeriesRecordingChanged(vararg s: SeriesRecording) = seriesRecordingListeners.toList().forEach { it.onSeriesRecordingChanged(*s) }
    protected fun notifyScheduledRecordingAdded(vararg r: ScheduledRecording) = scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingAdded(*r) }
    protected fun notifyScheduledRecordingRemoved(vararg r: ScheduledRecording) = scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingRemoved(*r) }
    protected fun notifyScheduledRecordingStatusChanged(vararg r: ScheduledRecording) =
        scheduledRecordingListeners.toList().forEach { it.onScheduledRecordingStatusChanged(*r) }

    private fun filterEndTimeIsPast(originals: List<ScheduledRecording>) = originals.filter { it.endTimeMs > clock.currentTimeMillis() }

    override fun getAvailableScheduledRecordings(): List<ScheduledRecording> = filterEndTimeIsPast(
        getRecordingsWithState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS, ScheduledRecording.STATE_RECORDING_NOT_STARTED))

    override fun getStartedRecordings(): List<ScheduledRecording> =
        filterEndTimeIsPast(getRecordingsWithState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS))

    override fun getNonStartedScheduledRecordings(): List<ScheduledRecording> =
        filterEndTimeIsPast(getRecordingsWithState(ScheduledRecording.STATE_RECORDING_NOT_STARTED))

    override fun getFailedScheduledRecordings(): List<ScheduledRecording> = getRecordingsWithState(ScheduledRecording.STATE_RECORDING_FAILED)

    override fun changeState(scheduledRecording: ScheduledRecording, newState: Int) {
        if (scheduledRecording.state != newState) {
            updateScheduledRecording(ScheduledRecording.buildFrom(scheduledRecording).setState(newState).build())
        }
    }

    override fun changeState(scheduledRecording: ScheduledRecording, newState: Int, reason: Int) {
        if (scheduledRecording.state != newState) {
            val builder = ScheduledRecording.buildFrom(scheduledRecording).setState(newState)
            if (newState == ScheduledRecording.STATE_RECORDING_FAILED) builder.setFailedReason(reason)
            updateScheduledRecording(builder.build())
        }
    }

    override fun getDeletedSchedules(): Collection<ScheduledRecording> = deletedScheduleMap.values
    override fun getDisallowedProgramIds(): Collection<Long> = deletedScheduleMap.keys

    protected abstract fun getRecordingsWithState(vararg states: Int): List<ScheduledRecording>

    override fun getRecordedPrograms(seriesRecordingId: Long): List<RecordedProgram> {
        val series = getSeriesRecording(seriesRecordingId) ?: return emptyList()
        return getRecordedPrograms().filter { series.seriesId == it.seriesId }
    }

    override fun checkAndRemoveEmptySeriesRecording(vararg seriesRecordingIds: Long) {
        val toRemove = seriesRecordingIds.asList().mapNotNull { id: Long -> getSeriesRecording(id) }.filter { isEmptySeriesRecording(it) }
        removeSeriesRecording(*toRemove.toTypedArray())
    }

    /** Leer = gestoppt, ohne ausstehende Aufnahmen und ohne Aufnahmen dieser Serie. */
    protected fun isEmptySeriesRecording(seriesRecording: SeriesRecording): Boolean {
        if (!seriesRecording.isStopped) return false
        if (getAvailableScheduledRecordings().any { it.seriesRecordingId == seriesRecording.id }) return false
        return getRecordedPrograms().none { seriesRecording.seriesId == it.seriesId }
    }

    override fun forgetStorage(inputId: String) {}
}
