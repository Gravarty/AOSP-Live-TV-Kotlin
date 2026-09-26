package com.android.tv.dvr.ui.browse

import android.os.Bundle
import androidx.leanback.widget.Action
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.ui.DetailsActivity

/** Detailansicht einer geplanten Aufnahme. */
class ScheduledRecordingDetailsFragment : RecordingDetailsFragment() {
    // DvrManager ist ohne DVR null; die Detailansicht gibt es nur mit DVR.
    private var dvrManager: DvrManager? = null
    private var scheduleAction: Action? = null
    private var hideViewSchedule = false

    override fun onCreate(savedInstanceState: Bundle?) {
        dvrManager = TvSingletons.getSingletons(requireContext()).getDvrManager()
        hideViewSchedule = arguments?.getBoolean(DetailsActivity.HIDE_VIEW_SCHEDULE) ?: false
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        scheduleAction?.icon = resources.getDrawable(getScheduleIconId(), null)
    }

    override fun onCreateActionsAdapter(): SparseArrayObjectAdapter {
        val adapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        val res = resources
        if (!hideViewSchedule) {
            val action = Action(ACTION_VIEW_SCHEDULE.toLong(), res.getString(R.string.dvr_detail_view_schedule), null,
                res.getDrawable(getScheduleIconId(), null))
            scheduleAction = action
            adapter.set(ACTION_VIEW_SCHEDULE, action)
        }
        adapter.set(ACTION_CANCEL, Action(ACTION_CANCEL.toLong(), res.getString(R.string.dvr_detail_cancel_recording),
            null, res.getDrawable(R.drawable.ic_dvr_cancel_32dp, null)))
        return adapter
    }

    override fun onCreateOnActionClickedListener() = OnActionClickedListener { action ->
        when (action.id) {
            ACTION_VIEW_SCHEDULE.toLong() -> DvrUiHelper.startSchedulesActivity(requireContext(), getRecording())
            ACTION_CANCEL.toLong() -> {
                dvrManager?.removeScheduledRecording(getRecording())
                requireActivity().finish()
            }
        }
    }

    private fun getScheduleIconId(): Int =
        if (dvrManager?.isConflicting(getRecording()) == true) R.drawable.ic_warning_white_32dp
        else R.drawable.ic_schedule_32dp

    companion object {
        private const val ACTION_VIEW_SCHEDULE = 1
        private const val ACTION_CANCEL = 2
    }
}
