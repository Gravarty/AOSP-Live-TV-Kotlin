package com.android.tv.dvr.ui.list

import android.content.Context
import android.text.format.DateUtils
import android.util.Log
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.Clock
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.DateHeaderRow
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit

/**
 * Adapter für den Aufnahmeverlauf (fehlgeschlagene Aufnahmen und aufgenommene Sendungen), nach Tagen
 * gruppiert, neueste zuerst. Abweichung: UiFlags entfernt, maxHistoryDays fest 0 (= unbegrenzt, wie AOSP-Default).
 */
class DvrHistoryRowAdapter(
    /** Kontext. */
    val context: Context,
    classPresenterSelector: ClassPresenterSelector,
    private val clock: Clock,
    private val dvrDataManager: DvrDataManager,
) : ArrayObjectAdapter(classPresenterSelector) {
    private val maxHistoryDays = MAX_HISTORY_DAYS
    private val titles = arrayListOf(context.getString(R.string.dvr_date_today), context.getString(R.string.dvr_date_yesterday))
    private val recordedProgramScheduleMap = HashMap<Long, ScheduledRecording>()

    /** Startet den Adapter. */
    fun start() {
        clear()
        val recordingList = dvrDataManager.getFailedScheduledRecordings().toMutableList()
        val recordedProgramList = dvrDataManager.getRecordedPrograms()
        recordingList.addAll(recordedProgramsToScheduledRecordings(recordedProgramList, maxHistoryDays))
        recordingList.sortWith(ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.reversed())
        var deadLine = Utils.getFirstMillisecondOfDay(clock.currentTimeMillis())
        var i = 0
        while (i < recordingList.size) {
            val section = ArrayList<ScheduledRecording>()
            while (i < recordingList.size && recordingList[i].startTimeMs >= deadLine) {
                section.add(recordingList[i++])
            }
            if (section.isNotEmpty()) {
                val headerRow = DateHeaderRow(calculateHeaderDate(deadLine),
                    context.resources.getQuantityString(R.plurals.dvr_schedules_section_subtitle, section.size, section.size),
                    section.size, deadLine)
                add(headerRow)
                for (recording in section) add(ScheduleRow(recording, headerRow))
            }
            deadLine -= ONE_DAY_MS
        }
    }

    private fun calculateHeaderDate(timeMs: Long): String {
        val titleIndex = ((Utils.getFirstMillisecondOfDay(clock.currentTimeMillis()) - timeMs) / ONE_DAY_MS).toInt()
        return if (titleIndex < titles.size) {
            titles[titleIndex]
        } else {
            DateUtils.formatDateTime(context, timeMs,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        }
    }

    private fun recordedProgramsToScheduledRecordings(programs: List<RecordedProgram>, maxDays: Long): List<ScheduledRecording> =
        programs.mapNotNull { recordedProgramsToScheduledRecordings(it, maxDays) }

    private fun recordedProgramsToScheduledRecordings(program: RecordedProgram, maxDays: Long): ScheduledRecording? {
        val firstMillisecondToday = Utils.getFirstMillisecondOfDay(clock.currentTimeMillis())
        if (maxDays != 0L && maxDays < Utils.computeDateDifference(program.startTimeUtcMillis, firstMillisecondToday)) {
            return null
        }
        val scheduledRecording = ScheduledRecording.builder(program).build()
        recordedProgramScheduleMap[program.id] = scheduledRecording
        return scheduledRecording
    }

    fun onScheduledRecordingAdded(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: $schedule")
        if (findRowByScheduledRecording(schedule) == null &&
            (schedule.state == ScheduledRecording.STATE_RECORDING_FINISHED ||
                schedule.state == ScheduledRecording.STATE_RECORDING_CLIPPED ||
                schedule.state == ScheduledRecording.STATE_RECORDING_FAILED)
        ) {
            addScheduleRow(schedule)
        }
    }

    fun onScheduledRecordingAdded(program: RecordedProgram) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: $program")
        if (recordedProgramScheduleMap[program.id] != null) return
        val schedule = recordedProgramsToScheduledRecordings(program, maxHistoryDays) ?: return
        addScheduleRow(schedule)
    }

    fun onScheduledRecordingRemoved(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: $schedule")
        val row = findRowByScheduledRecording(schedule) ?: return
        removeScheduleRow(row)
        notifyArrayItemRangeChanged(indexOf(row), 1)
    }

    fun onScheduledRecordingRemoved(program: RecordedProgram) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: $program")
        if (recordedProgramScheduleMap[program.id] != null) {
            recordedProgramScheduleMap.remove(program.id)
            val row = findRowByRecordedProgram(program)
            if (row != null) {
                removeScheduleRow(row)
                notifyArrayItemRangeChanged(indexOf(row), 1)
            }
        }
    }

    fun onScheduledRecordingUpdated(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: $schedule")
        val row = findRowByScheduledRecording(schedule) ?: return
        row.schedule = schedule
        if (schedule.state != ScheduledRecording.STATE_RECORDING_FAILED) {
            // Nur fehlgeschlagene Aufnahmen; beendete kommen als RecordedProgram.
            removeScheduleRow(row)
        }
        notifyArrayItemRangeChanged(indexOf(row), 1)
    }

    fun onScheduledRecordingUpdated(program: RecordedProgram) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: $program")
        val row = findRowByRecordedProgram(program)
        if (row != null) {
            removeScheduleRow(row)
            notifyArrayItemRangeChanged(indexOf(row), 1)
            recordedProgramScheduleMap.remove(program.id)
        }
        onScheduledRecordingAdded(program)
    }

    private fun addScheduleRow(recording: ScheduledRecording?) {
        // Darf nicht aus abgeleiteten Klassen aufgerufen werden.
        SoftPreconditions.checkState(javaClass == DvrHistoryRowAdapter::class.java, TAG, null)
        recording ?: return
        val comparator = ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.reversed()
        var pre = -1
        var index = 0
        while (index < size()) {
            val scheduleRow = get(index)
            if (scheduleRow is ScheduleRow) {
                if (comparator.compare(scheduleRow.schedule!!, recording) > 0) break
                pre = index
            }
            index++
        }
        val deadLine = Utils.getFirstMillisecondOfDay(recording.startTimeMs)
        if (pre >= 0 && getHeaderRow(pre).deadLineMs == deadLine) {
            val headerRow = (get(pre) as ScheduleRow).headerRow!!
            headerRow.itemCount = headerRow.itemCount + 1
            add(++pre, ScheduleRow(recording, headerRow))
            updateHeaderDescription(headerRow)
        } else if (index < size() && getHeaderRow(index).deadLineMs == deadLine) {
            val headerRow = (get(index) as ScheduleRow).headerRow!!
            headerRow.itemCount = headerRow.itemCount + 1
            add(index, ScheduleRow(recording, headerRow))
            updateHeaderDescription(headerRow)
        } else {
            val headerRow = DateHeaderRow(calculateHeaderDate(deadLine),
                context.resources.getQuantityString(R.plurals.dvr_schedules_section_subtitle, 1, 1), 1, deadLine)
            add(++pre, headerRow)
            // Bugfix: Das Original fügte die Zeile an derselben Position ein und damit VOR ihrer Kopfzeile.
            add(pre + 1, ScheduleRow(recording, headerRow))
        }
    }

    private fun getHeaderRow(index: Int): DateHeaderRow = (get(index) as ScheduleRow).headerRow as DateHeaderRow

    /** Zeile zur [ScheduledRecording] (per ID). */
    private fun findRowByScheduledRecording(recording: ScheduledRecording?): ScheduleRow? {
        recording ?: return null
        for (i in 0 until size()) {
            val item = get(i)
            if (item is ScheduleRow && item.schedule != null && item.schedule!!.id == recording.id) return item
        }
        return null
    }

    private fun findRowByRecordedProgram(program: RecordedProgram?): ScheduleRow? {
        program ?: return null
        for (i in 0 until size()) {
            val row = get(i) as? ScheduleRow ?: continue
            if (row.hasRecordedProgram() && row.schedule!!.recordedProgramId == program.id) return row
        }
        return null
    }

    private fun removeScheduleRow(scheduleRow: ScheduleRow?) {
        // Darf nicht aus abgeleiteten Klassen aufgerufen werden.
        SoftPreconditions.checkState(javaClass == DvrHistoryRowAdapter::class.java, TAG, null)
        scheduleRow ?: return
        scheduleRow.schedule = null
        val headerRow = scheduleRow.headerRow
        remove(scheduleRow)
        // Anzahl in der zugehörigen Kopfzeile anpassen.
        if (headerRow != null) {
            headerRow.itemCount = headerRow.itemCount - 1
            if (headerRow.itemCount == 0) {
                remove(headerRow)
            } else {
                replace(indexOf(headerRow), headerRow)
                updateHeaderDescription(headerRow)
            }
        }
    }

    private fun updateHeaderDescription(headerRow: SchedulesHeaderRow) {
        headerRow.description = context.resources.getQuantityString(R.plurals.dvr_schedules_section_subtitle,
            headerRow.itemCount, headerRow.itemCount)
    }

    companion object {
        private const val TAG = "DvrHistoryRowAdapter"
        private const val DEBUG = false
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        /** Abweichung: statt UiFlags.maxHistoryDays() (AOSP-Default 0 = keine Begrenzung). */
        private const val MAX_HISTORY_DAYS = 0L
    }
}
