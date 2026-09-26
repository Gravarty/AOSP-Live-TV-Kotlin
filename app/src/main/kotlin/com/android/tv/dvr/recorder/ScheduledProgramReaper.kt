package com.android.tv.dvr.recorder

import androidx.annotation.MainThread
import com.android.tv.common.util.Clock
import com.android.tv.dvr.WritableDvrDataManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import java.util.concurrent.TimeUnit

/** Entfernt Pläne, die seit mehr als 7 Tagen vorbei sind (abgeschlossene Serienfolgen bleiben). */
class ScheduledProgramReaper internal constructor(
    private val dvrDataManager: WritableDvrDataManager,
    private val clock: Clock,
) : Runnable {
    @MainThread
    override fun run() {
        val cutoff = clock.currentTimeMillis() - TimeUnit.DAYS.toMillis(DAYS.toLong())
        val toRemove = dvrDataManager.getAllScheduledRecordings().filter {
            it.endTimeMs < cutoff && (it.seriesRecordingId == SeriesRecording.ID_NOT_SET || it.state != ScheduledRecording.STATE_RECORDING_FINISHED)
        } + dvrDataManager.getDeletedSchedules().filter { it.endTimeMs < cutoff }
        if (toRemove.isNotEmpty()) dvrDataManager.removeScheduledRecording(*toRemove.toTypedArray())
    }

    companion object {
        const val DAYS = 7
    }
}
