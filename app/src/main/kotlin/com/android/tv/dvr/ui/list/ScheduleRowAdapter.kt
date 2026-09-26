package com.android.tv.dvr.ui.list

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.text.format.DateUtils
import android.util.ArraySet
import android.util.Log
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.DateHeaderRow
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit

/** Adapter für [ScheduleRow]s, nach Tagen gruppiert; entfernt abgelaufene Zeilen per Timer. */
open class ScheduleRowAdapter(
    /** Kontext. */
    protected val context: Context,
    classPresenterSelector: ClassPresenterSelector,
) : ArrayObjectAdapter(classPresenterSelector) {
    private val titles = arrayListOf(context.getString(R.string.dvr_date_today), context.getString(R.string.dvr_date_tomorrow))
    private val pendingUpdate: MutableSet<ScheduleRow> = ArraySet()

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_UPDATE_ROW) {
            val currentTimeMs = System.currentTimeMillis()
            handleUpdateRow(currentTimeMs)
            sendNextUpdateMessage(currentTimeMs)
        }
        true
    }

    /** Startet den Adapter: lädt geplante und laufende Aufnahmen. */
    open fun start() {
        clear()
        val dataManager = TvSingletons.getSingletons(context).getDvrDataManager()
        val recordingList = dataManager.getNonStartedScheduledRecordings().toMutableList()
        recordingList.addAll(dataManager.getStartedRecordings())
        recordingList.sortWith(ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR)
        var deadLine = Utils.getLastMillisecondOfDay(System.currentTimeMillis())
        var i = 0
        while (i < recordingList.size) {
            val section = ArrayList<ScheduledRecording>()
            while (i < recordingList.size && recordingList[i].startTimeMs < deadLine) {
                section.add(recordingList[i++])
            }
            if (section.isNotEmpty()) {
                val headerRow = DateHeaderRow(calculateHeaderDate(deadLine),
                    context.resources.getQuantityString(R.plurals.dvr_schedules_section_subtitle, section.size, section.size),
                    section.size, deadLine)
                add(headerRow)
                for (recording in section) add(ScheduleRow(recording, headerRow))
            }
            deadLine += ONE_DAY_MS
        }
        sendNextUpdateMessage(System.currentTimeMillis())
    }

    private fun calculateHeaderDate(deadLine: Long): String {
        val titleIndex = ((deadLine - Utils.getLastMillisecondOfDay(System.currentTimeMillis())) / ONE_DAY_MS).toInt()
        return if (titleIndex < titles.size) {
            titles[titleIndex]
        } else {
            DateUtils.formatDateTime(context, deadLine,
                DateUtils.FORMAT_SHOW_WEEKDAY or DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_ABBREV_MONTH)
        }
    }

    /** Stoppt den Adapter; abgebrochene Aufnahmen werden jetzt endgültig gelöscht. */
    open fun stop() {
        handler.removeCallbacksAndMessages(null)
        val dvrManager = TvSingletons.getSingletons(context).getDvrManager()
        for (i in 0 until size()) {
            val row = get(i) as? ScheduleRow ?: continue
            if (row.isScheduleCanceled) {
                // Abweichung: DvrManager ist nullable (ohne DVR null).
                row.schedule?.let { dvrManager?.removeScheduledRecording(it) }
            }
        }
    }

    /** Zeile zur [ScheduledRecording] (per ID). */
    fun findRowByScheduledRecording(recording: ScheduledRecording?): ScheduleRow? {
        recording ?: return null
        for (i in 0 until size()) {
            val item = get(i)
            if (item is ScheduleRow && item.schedule != null && item.schedule!!.id == recording.id) return item
        }
        return null
    }

    private fun findRowWithStartRequest(schedule: ScheduledRecording): ScheduleRow? {
        for (i in 0 until size()) {
            val row = get(i) as? ScheduleRow ?: continue
            if (row.schedule != null && row.isStartRecordingRequested && row.matchSchedule(schedule)) return row
        }
        return null
    }

    private fun addScheduleRow(recording: ScheduledRecording?) {
        // Darf nicht aus abgeleiteten Klassen aufgerufen werden.
        SoftPreconditions.checkState(javaClass == ScheduleRowAdapter::class.java, TAG, null)
        recording ?: return
        var pre = -1
        var index = 0
        while (index < size()) {
            val scheduleRow = get(index)
            if (scheduleRow is ScheduleRow) {
                if (ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.compare(scheduleRow.schedule!!, recording) > 0) break
                pre = index
            }
            index++
        }
        val deadLine = Utils.getLastMillisecondOfDay(recording.startTimeMs)
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

    private fun removeScheduleRow(scheduleRow: ScheduleRow?) {
        // Darf nicht aus abgeleiteten Klassen aufgerufen werden.
        SoftPreconditions.checkState(javaClass == ScheduleRowAdapter::class.java, TAG, null)
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

    /** Aufnahme wurde im DvrDataManager hinzugefügt. */
    open fun onScheduledRecordingAdded(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingAdded: $schedule")
        // Bei angefordertem Start kommt erst NOT_STARTED, kurz danach IN_PROGRESS. Um Flackern zu
        // vermeiden, wird die Zeile erst in onScheduledRecordingUpdated aktualisiert.
        if (findRowWithStartRequest(schedule) == null) {
            addScheduleRow(schedule)
            sendNextUpdateMessage(System.currentTimeMillis())
        }
    }

    /** Aufnahme wurde im DvrDataManager entfernt. */
    open fun onScheduledRecordingRemoved(schedule: ScheduledRecording) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingRemoved: $schedule")
        val row = findRowByScheduledRecording(schedule) ?: return
        removeScheduleRow(row)
        notifyArrayItemRangeChanged(indexOf(row), 1)
        sendNextUpdateMessage(System.currentTimeMillis())
    }

    /** Aufnahme wurde im DvrDataManager geändert. */
    open fun onScheduledRecordingUpdated(schedule: ScheduledRecording, conflictChange: Boolean) {
        if (DEBUG) Log.d(TAG, "onScheduledRecordingUpdated: $schedule")
        var row = findRowByScheduledRecording(schedule)
        if (row != null) {
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
                    row.schedule = schedule
                }
            } else {
                row.schedule = schedule
                if (!willBeKept(schedule)) removeScheduleRow(row)
            }
            notifyArrayItemRangeChanged(indexOf(row), 1)
            sendNextUpdateMessage(System.currentTimeMillis())
        } else {
            row = findRowWithStartRequest(schedule)
            // Angeforderter Start hat höchste Priorität, der Zustand wechselt also sicher von
            // NOT_STARTED. Direkt den Folgezustand zeigen (kein Flackern).
            if (row != null && schedule.state != ScheduledRecording.STATE_RECORDING_NOT_STARTED) {
                row.isStartRecordingRequested = false
                if (!isStartOrStopRequested()) executePendingUpdate()
                row.schedule = schedule
                notifyArrayItemRangeChanged(indexOf(row), 1)
                sendNextUpdateMessage(System.currentTimeMillis())
            }
        }
    }

    /** Gibt es eine Zeile mit angefordertem Start/Stopp? */
    protected fun isStartOrStopRequested(): Boolean {
        for (i in 0 until size()) {
            val row = get(i) as? ScheduleRow ?: continue
            if (row.isStartRecordingRequested || row.isStopRecordingRequested) return true
        }
        return false
    }

    /** Aktualisierung der Zeile verzögern. */
    protected fun addPendingUpdate(row: ScheduleRow) {
        pendingUpdate.add(row)
    }

    /** Verzögerte Aktualisierungen ausführen. */
    protected fun executePendingUpdate() {
        for (row in pendingUpdate) {
            val index = indexOf(row)
            if (index != -1) notifyArrayItemRangeChanged(index, 1)
        }
        pendingUpdate.clear()
    }

    /** Soll die Aufnahme in der Liste bleiben? CANCELED bleibt sichtbar, damit sie neu geplant werden kann. */
    protected open fun willBeKept(schedule: ScheduledRecording): Boolean =
        schedule.endTimeMs > System.currentTimeMillis() &&
            (schedule.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS ||
                schedule.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED ||
                schedule.state == ScheduledRecording.STATE_RECORDING_CANCELED)

    /** Timer-Nachricht: abgelaufene Zeilen entfernen. */
    protected open fun handleUpdateRow(currentTimeMs: Long) {
        // Bugfix: Das Original entfernte beim Vorwärtslaufen und übersprang so die Folgezeile.
        // Deshalb erst sammeln, dann entfernen.
        val expired = (0 until size()).mapNotNull { get(it) as? ScheduleRow }.filter { it.endTimeMs <= currentTimeMs }
        expired.forEach { removeScheduleRow(it) }
    }

    /** Nächster Update-Zeitpunkt; [Long.MAX_VALUE], wenn kein Timer nötig ist. */
    protected open fun getNextTimerMs(currentTimeMs: Long): Long {
        var earliest = Long.MAX_VALUE
        for (i in 0 until size()) {
            // Früher beendete Aufnahmen werden erst zum Endzeitpunkt entfernt.
            val row = get(i) as? ScheduleRow ?: continue
            if (earliest > row.endTimeMs) earliest = row.endTimeMs
        }
        return earliest
    }

    /** Update-Nachricht zum Zeitpunkt aus [getNextTimerMs] senden. */
    protected fun sendNextUpdateMessage(currentTimeMs: Long) {
        handler.removeMessages(MSG_UPDATE_ROW)
        val nextTime = getNextTimerMs(currentTimeMs)
        if (nextTime != Long.MAX_VALUE) {
            handler.sendEmptyMessageDelayed(MSG_UPDATE_ROW, nextTime - System.currentTimeMillis())
        }
    }

    companion object {
        private const val TAG = "ScheduleRowAdapter"
        private const val DEBUG = false
        private val ONE_DAY_MS = TimeUnit.DAYS.toMillis(1)
        private const val MSG_UPDATE_ROW = 1
    }
}
