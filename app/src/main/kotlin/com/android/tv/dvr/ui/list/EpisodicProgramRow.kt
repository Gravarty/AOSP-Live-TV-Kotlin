package com.android.tv.dvr.ui.list

import android.content.Context
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper

/** Zeile für eine Folge einer Serie. */
class EpisodicProgramRow(
    private val inputId: String?,
    val program: Program,
    recording: ScheduledRecording?,
    headerRow: SchedulesHeaderRow?,
) : ScheduleRow(recording, headerRow) {

    override val channelId: Long get() = program.channelId
    override val startTimeMs: Long get() = program.startTimeUtcMillis
    override val endTimeMs: Long get() = program.endTimeUtcMillis

    override fun createNewScheduleBuilder(): ScheduledRecording.Builder = ScheduledRecording.builder(inputId, program)

    override fun getProgramTitleWithEpisodeNumber(context: Context): String =
        DvrUiHelper.getStyledTitleWithEpisodeNumber(context, program, 0).toString()

    override fun getEpisodeDisplayTitle(context: Context): String? = program.getEpisodeDisplayTitle(context)

    override fun matchSchedule(schedule: ScheduledRecording): Boolean =
        schedule.type == ScheduledRecording.TYPE_PROGRAM && program.id == schedule.programId

    override fun toString() = "${super.toString()}(inputId=$inputId,program=$program)"
}
