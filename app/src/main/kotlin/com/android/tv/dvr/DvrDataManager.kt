package com.android.tv.dvr

import android.util.Range
import androidx.annotation.MainThread
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording

/** Lesezugriff auf Aufnahmen, geplante Aufnahmen und Serien. */
@MainThread
interface DvrDataManager {
    val isInitialized: Boolean
    val isDvrScheduleLoadFinished: Boolean
    val isRecordedProgramLoadFinished: Boolean

    fun getRecordedPrograms(): List<RecordedProgram>
    fun getRecordedPrograms(seriesRecordingId: Long): List<RecordedProgram>
    fun getAllScheduledRecordings(): List<ScheduledRecording>
    fun getAvailableScheduledRecordings(): List<ScheduledRecording>
    fun getStartedRecordings(): List<ScheduledRecording>
    fun getNonStartedScheduledRecordings(): List<ScheduledRecording>
    fun getFailedScheduledRecordings(): List<ScheduledRecording>
    fun getSeriesRecordings(): List<SeriesRecording>
    fun getSeriesRecordings(inputId: String): List<SeriesRecording>
    fun getNextScheduledStartTimeAfter(time: Long): Long
    fun getScheduledRecordings(period: Range<Long>, state: Int): List<ScheduledRecording>
    fun getScheduledRecordings(seriesRecordingId: Long): List<ScheduledRecording>
    fun getScheduledRecordings(inputId: String): List<ScheduledRecording>

    fun addDvrScheduleLoadFinishedListener(listener: OnDvrScheduleLoadFinishedListener)
    fun removeDvrScheduleLoadFinishedListener(listener: OnDvrScheduleLoadFinishedListener)
    fun addRecordedProgramLoadFinishedListener(listener: OnRecordedProgramLoadFinishedListener)
    fun removeRecordedProgramLoadFinishedListener(listener: OnRecordedProgramLoadFinishedListener)
    fun addScheduledRecordingListener(listener: ScheduledRecordingListener)
    fun removeScheduledRecordingListener(listener: ScheduledRecordingListener)
    fun addRecordedProgramListener(listener: RecordedProgramListener)
    fun removeRecordedProgramListener(listener: RecordedProgramListener)
    fun addSeriesRecordingListener(listener: SeriesRecordingListener)
    fun removeSeriesRecordingListener(listener: SeriesRecordingListener)

    fun getScheduledRecording(recordingId: Long): ScheduledRecording?
    fun getScheduledRecordingForProgramId(programId: Long): ScheduledRecording?
    fun getRecordedProgram(recordingId: Long): RecordedProgram?
    fun getSeriesRecording(seriesRecordingId: Long): SeriesRecording?
    fun getSeriesRecording(seriesId: String): SeriesRecording?
    fun getDeletedSchedules(): Collection<ScheduledRecording>
    fun getDisallowedProgramIds(): Collection<Long>
    fun checkAndRemoveEmptySeriesRecording(vararg seriesRecordingIds: Long)

    fun interface OnDvrScheduleLoadFinishedListener {
        fun onDvrScheduleLoadFinished()
    }

    fun interface OnRecordedProgramLoadFinishedListener {
        fun onRecordedProgramLoadFinished()
    }

    interface ScheduledRecordingListener {
        fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording)
        fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording)
        fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording)
    }

    interface SeriesRecordingListener {
        fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording)
        fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording)
        fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording)
    }

    interface RecordedProgramListener {
        fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram)
        fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram)
        fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram)
    }

    companion object {
        const val NEXT_START_TIME_NOT_FOUND = -1L
    }
}

/** Schreibzugriff (nur DVR-intern). */
@MainThread
interface WritableDvrDataManager : DvrDataManager {
    fun addScheduledRecording(vararg scheduledRecordings: ScheduledRecording)
    fun addSeriesRecording(vararg seriesRecordings: SeriesRecording)
    fun removeScheduledRecording(vararg scheduledRecordings: ScheduledRecording)
    fun removeScheduledRecording(forceRemove: Boolean, vararg scheduledRecordings: ScheduledRecording)
    fun removeSeriesRecording(vararg seasonSchedules: SeriesRecording)
    fun updateScheduledRecording(vararg scheduledRecordings: ScheduledRecording)
    fun updateSeriesRecording(vararg seriesRecordings: SeriesRecording)
    fun changeState(scheduledRecording: ScheduledRecording, newState: Int)
    fun changeState(scheduledRecording: ScheduledRecording, newState: Int, reason: Int)
    fun forgetStorage(inputId: String)
}
