package com.android.tv.dvr.ui.list

import android.content.Context
import android.util.ArrayMap
import android.util.Log
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.SeriesRecordingHeaderRow
import com.android.tv.util.Utils

/** Adapter für die Folgen einer Serienaufnahme (eine Kopfzeile + eine Zeile pro Sendung). */
class SeriesScheduleRowAdapter(
    context: Context,
    classPresenterSelector: ClassPresenterSelector,
    private val seriesRecording: SeriesRecording,
) : ScheduleRowAdapter(context, classPresenterSelector) {
    private val inputId: String?
    // Abweichung: TvSingletons liefert DvrManager nullable; die Serienliste gibt es nur mit DVR.
    private val dvrManager: DvrManager = checkNotNull(TvSingletons.getSingletons(context).getDvrManager())
    private val dataManager: DvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
    private val programs: MutableMap<Long, Program> = ArrayMap()
    private lateinit var headerRow: SeriesRecordingHeaderRow

    init {
        val input = seriesRecording.inputId?.let { Utils.getTvInputInfoForInputId(context, it) }
        inputId = if (SoftPreconditions.checkState(input != null, TAG, "Input not found: ${seriesRecording.inputId}")) input!!.id else null
        setHasStableIds(true)
    }

    override fun start() {
        setPrograms(emptyList())
    }

    /** Anzuzeigende Sendungen setzen. */
    fun setPrograms(programs: List<Program>?) {
        val programList = programs ?: emptyList()
        clear()
        this.programs.clear()
        val sortedPrograms = programList.sorted()
        val rows = ArrayList<EpisodicProgramRow>()
        headerRow = SeriesRecordingHeaderRow(seriesRecording.title, null, sortedPrograms.size, seriesRecording, programList)
        for (program in sortedPrograms) {
            var schedule = dataManager.getScheduledRecordingForProgramId(program.id)
            if (schedule != null && !willBeKept(schedule)) schedule = null
            rows.add(EpisodicProgramRow(inputId, program, schedule, headerRow))
            this.programs[program.id] = program
        }
        headerRow.description = getDescription()
        add(headerRow)
        for (row in rows) add(row)
        sendNextUpdateMessage(System.currentTimeMillis())
    }

    private fun getDescription(): String? {
        var conflicts = 0
        for (programId in programs.keys) {
            if (dvrManager.isConflicting(dataManager.getScheduledRecordingForProgramId(programId))) ++conflicts
        }
        return if (conflicts == 0) null
        else context.resources.getQuantityString(R.plurals.dvr_series_schedules_header_description, conflicts, conflicts)
    }

    override fun getId(position: Int): Long {
        val obj = get(position)
        if (obj is EpisodicProgramRow) return obj.program.id
        if (obj is SeriesRecordingHeaderRow) return 0
        return super.getId(position)
    }

    override fun onScheduledRecordingAdded(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: $schedule")
        val index = findRowIndexByProgramId(schedule.programId)
        if (index != -1) {
            val row = get(index) as EpisodicProgramRow
            if (!row.isStartRecordingRequested) {
                setScheduleToRow(row, schedule)
                notifyArrayItemRangeChanged(index, 1)
            }
        }
    }

    override fun onScheduledRecordingRemoved(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: $schedule")
        val index = findRowIndexByProgramId(schedule.programId)
        if (index != -1) {
            val row = get(index) as EpisodicProgramRow
            row.schedule = null
            notifyArrayItemRangeChanged(index, 1)
        }
    }

    override fun onScheduledRecordingUpdated(schedule: ScheduledRecording, conflictChange: Boolean) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: $schedule")
        val index = findRowIndexByProgramId(schedule.programId)
        if (index != -1) {
            val row = get(index) as EpisodicProgramRow
            if (conflictChange && isStartOrStopRequested()) {
                // Konflikt-Update bis zur Antwort auf Start/Stopp verzögern (kein Zwischenzustand).
                addPendingUpdate(row)
                return
            }
            if (row.isStopRecordingRequested) {
                // Warten, bis die Aufnahme beendet ist.
                if (schedule.state == ScheduledRecording.STATE_RECORDING_FINISHED ||
                    schedule.state == ScheduledRecording.STATE_RECORDING_CLIPPED ||
                    schedule.state == ScheduledRecording.STATE_RECORDING_FAILED
                ) {
                    row.isStopRecordingRequested = false
                    if (!isStartOrStopRequested()) executePendingUpdate()
                    row.schedule = null
                }
            } else if (row.isStartRecordingRequested) {
                // Angeforderter Start hat höchste Priorität, der Zustand wechselt also sicher von
                // NOT_STARTED. Direkt den Folgezustand zeigen (kein Flackern).
                if (schedule.state != ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                    row.isStartRecordingRequested = false
                    if (!isStartOrStopRequested()) executePendingUpdate()
                    setScheduleToRow(row, schedule)
                }
            } else {
                setScheduleToRow(row, schedule)
            }
            notifyArrayItemRangeChanged(index, 1)
        }
    }

    /** Serienaufnahme wurde geändert (z. B. gestoppt/fortgesetzt). */
    fun onSeriesRecordingUpdated(seriesRecording: SeriesRecording) {
        if (seriesRecording.id == this.seriesRecording.id) {
            headerRow.seriesRecording = seriesRecording
            notifyArrayItemRangeChanged(0, 1)
        }
    }

    private fun setScheduleToRow(row: ScheduleRow, schedule: ScheduledRecording?) {
        row.schedule = if (schedule != null && willBeKept(schedule)) schedule else null
    }

    private fun findRowIndexByProgramId(programId: Long): Int {
        for (i in 0 until size()) {
            val item = get(i)
            if (item is EpisodicProgramRow && item.program.id == programId) return i
        }
        return -1
    }

    override fun notifyArrayItemRangeChanged(positionStart: Int, itemCount: Int) {
        headerRow.description = getDescription()
        super.notifyArrayItemRangeChanged(0, 1)
        super.notifyArrayItemRangeChanged(positionStart, itemCount)
    }

    override fun handleUpdateRow(currentTimeMs: Long) {
        val iter = programs.values.iterator()
        while (iter.hasNext()) {
            val program = iter.next()
            if (program.endTimeUtcMillis <= currentTimeMs) {
                // Abgelaufene Sendung entfernen.
                removeItems(findRowIndexByProgramId(program.id), 1)
                iter.remove()
            } else if (program.startTimeUtcMillis < currentTimeMs) {
                // Button „AUFNAHME STARTEN“ anzeigen.
                notifyItemRangeChanged(findRowIndexByProgramId(program.id), 1)
            }
        }
    }

    /** Nimmt die Zeit, zu der die Sendungen im Handler geprüft werden. */
    override fun getNextTimerMs(currentTimeMs: Long): Long {
        var earliest = Long.MAX_VALUE
        for (program in programs.values) {
            if (earliest > program.startTimeUtcMillis && program.startTimeUtcMillis >= currentTimeMs) {
                // Wechsel „PLANEN“ → „AUFNAHME STARTEN“.
                earliest = program.startTimeUtcMillis
            } else if (earliest > program.endTimeUtcMillis) {
                // Zeile muss entfernt werden.
                earliest = program.endTimeUtcMillis
            }
        }
        return earliest
    }

    companion object {
        private const val TAG = "SeriesRowAdapter"
        private const val DEBUG = false
    }
}
