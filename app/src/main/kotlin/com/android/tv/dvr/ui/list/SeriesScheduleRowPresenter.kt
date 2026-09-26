package com.android.tv.dvr.ui.list

import android.content.Context
import android.view.View
import androidx.leanback.widget.RowPresenter
import com.android.tv.R
import com.android.tv.common.SoftPreconditions
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.util.Utils

/** RowPresenter für Folgen einer Serienaufnahme. */
class SeriesScheduleRowPresenter(context: Context) : ScheduleRowPresenter(context) {

    /** ViewHolder mit breiterer Zeitspalte. */
    class SeriesScheduleRowViewHolder(view: View, presenter: ScheduleRowPresenter) : ScheduleRowPresenter.ScheduleRowViewHolder(view, presenter) {
        init {
            val lp = timeView.layoutParams
            lp.width = view.resources.getDimensionPixelSize(R.dimen.dvr_series_schedules_item_time_width)
            timeView.layoutParams = lp
        }
    }

    override fun onGetScheduleRowViewHolder(view: View): ScheduleRowPresenter.ScheduleRowViewHolder = SeriesScheduleRowViewHolder(view, this)

    override fun onGetRecordingTimeText(row: ScheduleRow): String? =
        Utils.getDurationString(context, row.startTimeMs, row.endTimeMs, false, true, true, 0)

    override fun onGetProgramInfoText(row: ScheduleRow): String? = row.getEpisodeDisplayTitle(context)

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        val viewHolder = vh as SeriesScheduleRowViewHolder
        val row = item as EpisodicProgramRow
        if (dvrManager.isConflicting(row.schedule)) {
            viewHolder.programTitleView.compoundDrawablePadding =
                context.resources.getDimensionPixelOffset(R.dimen.dvr_schedules_warning_icon_padding)
            viewHolder.programTitleView.setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_warning_gray600_36dp, 0, 0, 0)
        } else {
            viewHolder.programTitleView.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0)
        }
    }

    override fun onInfoClicked(row: ScheduleRow) {
        DvrUiHelper.startSchedulesActivity(context, row.schedule)
    }

    override fun onStartRecording(row: ScheduleRow) {
        SoftPreconditions.checkState(row.schedule == null, TAG, "Start request with the existing schedule: $row")
        row.isStartRecordingRequested = true
        dvrManager.addScheduleWithHighestPriority((row as EpisodicProgramRow).program)
    }

    override fun onStopRecording(row: ScheduleRow) {
        SoftPreconditions.checkState(row.schedule != null, TAG, "Stop request with the null schedule: $row")
        row.isStopRecordingRequested = true
        // Abweichung: bei fehlender Aufnahme nicht stoppen (Original: NPE).
        row.schedule?.let { dvrManager.stopRecording(it) }
    }

    override fun onCreateSchedule(row: ScheduleRow) {
        if (row.schedule == null) {
            dvrManager.addScheduleWithHighestPriority((row as EpisodicProgramRow).program)
        } else {
            super.onCreateSchedule(row)
        }
    }

    override fun getAvailableActions(row: ScheduleRow): IntArray? {
        if (row.schedule == null) {
            return if (row.isOnAir) intArrayOf(ACTION_START_RECORDING) else intArrayOf(ACTION_CREATE_SCHEDULE)
        }
        return super.getAvailableActions(row)
    }

    override fun canResolveConflict(): Boolean = false

    override fun shouldKeepScheduleAfterRemoving(): Boolean = true

    companion object {
        private const val TAG = "SeriesRowPresenter"
    }
}
