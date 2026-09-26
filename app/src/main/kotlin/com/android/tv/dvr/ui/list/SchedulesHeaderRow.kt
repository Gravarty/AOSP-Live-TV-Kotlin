package com.android.tv.dvr.ui.list

import com.android.tv.data.api.Program
import com.android.tv.dvr.data.SeriesRecording

/** Basisklasse für die Kopfzeilen der Aufnahmeliste. */
abstract class SchedulesHeaderRow(var title: String?, var description: String?, var itemCount: Int) {

    /** Kopfzeile für ein Datum. */
    class DateHeaderRow(title: String?, description: String?, itemCount: Int,
        /** Spätester Zeitpunkt der Einträge unter dieser Kopfzeile. */
        val deadLineMs: Long) : SchedulesHeaderRow(title, description, itemCount)

    /** Kopfzeile für eine Serienaufnahme. */
    class SeriesRecordingHeaderRow(title: String?, description: String?, itemCount: Int,
        /** Serienaufnahme der Liste. */
        var seriesRecording: SeriesRecording,
        /** Sendungen der Serie. */
        val programs: List<Program>) : SchedulesHeaderRow(title, description, itemCount)
}
