package com.android.tv.dvr.ui

import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.list.DvrSchedulesActivity
import com.android.tv.dvr.ui.list.DvrSeriesSchedulesFragment

/** Bestätigung nach dem Planen einer Serie inkl. Konflikthinweisen. */
class DvrSeriesScheduledFragment : DvrGuidedStepFragment() {
    private var seriesRecording: SeriesRecording? = null
    private var showViewScheduleOption = false
    private var programs: List<Program>? = null
    private var seriesRecordingTitle: String? = null

    private var schedulesAddedCount = 0
    private var hasConflict = false
    private var inThisSeriesConflictCount = 0
    private var outThisSeriesConflictCount = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val args = arguments
        val seriesRecordingId =
            args?.getLong(DvrSeriesScheduledDialogActivity.SERIES_RECORDING_ID, SERIES_RECORDING_ID_NOT_SET)
                ?: SERIES_RECORDING_ID_NOT_SET
        if (seriesRecordingId == SERIES_RECORDING_ID_NOT_SET) {
            requireActivity().finish()
            return
        }
        showViewScheduleOption = args?.getBoolean(DvrSeriesScheduledDialogActivity.SHOW_VIEW_SCHEDULE_OPTION) ?: false
        val series = TvSingletons.getSingletons(context).getDvrDataManager().getSeriesRecording(seriesRecordingId)
        seriesRecording = series
        if (series == null) {
            requireActivity().finish()
            return
        }
        seriesRecordingTitle = series.title
        @Suppress("UNCHECKED_CAST")
        programs = BigArguments.getArgument(SERIES_SCHEDULED_KEY_PROGRAMS) as List<Program>?
        BigArguments.reset()
        schedulesAddedCount =
            TvSingletons.getSingletons(context).getDvrManager()?.getAvailableScheduledRecording(series.id)?.size ?: 0
        val conflictingRecordings =
            TvSingletons.getSingletons(context).getDvrScheduleManager()?.getConflictingSchedules(series).orEmpty()
        hasConflict = conflictingRecordings.isNotEmpty()
        for (recording in conflictingRecordings) {
            if (recording.seriesRecordingId == series.id) {
                ++inThisSeriesConflictCount
            } else if (recording.priority < series.priority) {
                ++outThisSeriesConflictCount
            }
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = getString(R.string.dvr_series_recording_dialog_title)
        val icon: Drawable? = if (!hasConflict) {
            resources.getDrawable(R.drawable.quantum_ic_check_circle_white_48, null)
        } else {
            resources.getDrawable(R.drawable.quantum_ic_error_white_48, null)
        }
        return GuidanceStylist.Guidance(title, getDescription(), null, icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(context).clickAction(GuidedAction.ACTION_ID_OK).build())
        if (showViewScheduleOption) {
            actions.add(
                GuidedAction.Builder(context)
                    .id(ACTION_VIEW_SCHEDULES.toLong())
                    .title(R.string.dvr_action_view_schedules)
                    .build(),
            )
        }
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        if (action.id == ACTION_VIEW_SCHEDULES.toLong()) {
            val intent = Intent(activity, DvrSchedulesActivity::class.java)
            intent.putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE, DvrSchedulesActivity.TYPE_SERIES_SCHEDULE)
            intent.putExtra(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING, seriesRecording)
            BigArguments.reset()
            BigArguments.setArgument(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_PROGRAMS, programs)
            startActivity(intent)
        }
        requireActivity().finish()
    }

    // Bugfix: Präfix war fälschlich "DvrMissingStorageErrorFragment"
    override fun getTrackerPrefix(): String = "DvrSeriesScheduledFragment"

    override fun getTrackerLabelForGuidedAction(action: GuidedAction): String =
        if (action.id == ACTION_VIEW_SCHEDULES.toLong()) "view-schedules" else super.getTrackerLabelForGuidedAction(action)

    private fun getDescription(): String {
        val res = resources
        if (!hasConflict) {
            return res.getQuantityString(
                R.plurals.dvr_series_scheduled_no_conflict,
                schedulesAddedCount,
                schedulesAddedCount,
                seriesRecordingTitle,
            )
        }
        // Beide Zähler 0 hieße hasConflict == false, dieser Fall ist oben erledigt
        return if (inThisSeriesConflictCount != 0 && outThisSeriesConflictCount != 0) {
            res.getQuantityString(
                R.plurals.dvr_series_scheduled_this_and_other_series_conflict,
                schedulesAddedCount,
                schedulesAddedCount,
                seriesRecordingTitle,
                inThisSeriesConflictCount + outThisSeriesConflictCount,
            )
        } else if (inThisSeriesConflictCount != 0) {
            res.getQuantityString(
                R.plurals.dvr_series_recording_scheduled_only_this_series_conflict,
                schedulesAddedCount,
                schedulesAddedCount,
                seriesRecordingTitle,
                inThisSeriesConflictCount,
            )
        } else if (outThisSeriesConflictCount == 1) {
            res.getQuantityString(
                R.plurals.dvr_series_scheduled_only_other_series_one_conflict,
                schedulesAddedCount,
                schedulesAddedCount,
                seriesRecordingTitle,
            )
        } else {
            res.getQuantityString(
                R.plurals.dvr_series_scheduled_only_other_series_many_conflicts,
                schedulesAddedCount,
                schedulesAddedCount,
                seriesRecordingTitle,
                outThisSeriesConflictCount,
            )
        }
    }

    companion object {
        /** Schlüssel der Sendungsliste (über [BigArguments]) für [DvrSeriesSchedulesFragment]. Typ: List<Program> */
        const val SERIES_SCHEDULED_KEY_PROGRAMS = "series_scheduled_key_programs"

        private const val SERIES_RECORDING_ID_NOT_SET = -1L

        private const val ACTION_VIEW_SCHEDULES = 1
    }
}
