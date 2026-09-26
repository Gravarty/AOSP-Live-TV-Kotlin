package com.android.tv.dvr.ui.list

import android.content.Context
import com.android.tv.common.SoftPreconditions
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper

/** Zeile für eine geplante Aufnahme. */
open class ScheduleRow(
    /** Aufnahme der Zeile (null, wenn gelöscht bzw. keine geplant). */
    var schedule: ScheduledRecording?,
    /** Kopfzeile, zu der diese Zeile gehört. */
    val headerRow: SchedulesHeaderRow?,
) {
    /** Stopp der Aufnahme wurde angefordert. */
    var isStopRecordingRequested = false
        set(value) {
            SoftPreconditions.checkState(!isStartRecordingRequested, null, null)
            field = value
        }

    /** Start der Aufnahme wurde angefordert. */
    var isStartRecordingRequested = false
        set(value) {
            SoftPreconditions.checkState(!isStopRecordingRequested, null, null)
            field = value
        }

    open val channelId: Long get() = schedule?.channelId ?: -1
    open val startTimeMs: Long get() = schedule?.startTimeMs ?: -1
    open val endTimeMs: Long get() = schedule?.endTimeMs ?: -1

    val duration: Long get() = endTimeMs - startTimeMs

    /** Läuft die Sendung gerade? */
    val isOnAir: Boolean
        get() {
            val currentTimeMs = System.currentTimeMillis()
            return startTimeMs <= currentTimeMs && endTimeMs > currentTimeMs
        }

    val isRecordingNotStarted: Boolean get() = schedule?.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED
    val isRecordingInProgress: Boolean get() = schedule?.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS
    val isRecordingFailed: Boolean get() = schedule?.state == ScheduledRecording.STATE_RECORDING_FAILED
    val isScheduleCanceled: Boolean get() = schedule?.state == ScheduledRecording.STATE_RECORDING_CANCELED

    val isRecordingFinished: Boolean
        get() = schedule?.state.let {
            it == ScheduledRecording.STATE_RECORDING_FAILED || it == ScheduledRecording.STATE_RECORDING_CLIPPED ||
                it == ScheduledRecording.STATE_RECORDING_FINISHED
        }

    fun hasRecordedProgram(): Boolean = schedule.let {
        it != null && it.recordedProgramId != null && it.state == ScheduledRecording.STATE_RECORDING_FINISHED
    }

    /** Neuer Builder aus der bestehenden Aufnahme. */
    open fun createNewScheduleBuilder(): ScheduledRecording.Builder? = schedule?.let { ScheduledRecording.buildFrom(it) }

    /** Titel mit Folgennummer. */
    open fun getProgramTitleWithEpisodeNumber(context: Context): String? =
        schedule?.let { DvrUiHelper.getStyledTitleWithEpisodeNumber(context, it, 0).toString() }

    /** Titel inkl. Staffel-/Folgennummer. */
    open fun getEpisodeDisplayTitle(context: Context): String? = schedule?.getEpisodeDisplayTitle(context)

    override fun toString() = "${javaClass.simpleName}(schedule=$schedule,stopRecordingRequested=$isStopRecordingRequested," +
        "startRecordingRequested=$isStartRecordingRequested)"

    /** Gehört [schedule] zur selben Sendung bzw. zum selben Kanal-Zeitraum? */
    open fun matchSchedule(schedule: ScheduledRecording): Boolean {
        val own = this.schedule ?: return false
        return if (own.type == ScheduledRecording.TYPE_TIMED) {
            own.channelId == schedule.channelId && own.startTimeMs == schedule.startTimeMs && own.endTimeMs == schedule.endTimeMs
        } else {
            own.programId == schedule.programId
        }
    }
}
