package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.RecordedProgram

/**
 * Weist darauf hin, dass dieselbe Folge bereits aufgenommen wurde.
 *
 * Der Aufnahmeplan ist zu diesem Zeitpunkt noch nicht angelegt.
 */
class DvrAlreadyRecordedFragment : DvrGuidedStepFragment() {
    private var program: Program? = null
    private var duplicate: RecordedProgram? = null

    // Abweichung: keine Dagger-Injektion von DvrFlags; startEarlyEndLateEnabled() ist im AOSP-Build false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val program = BundleCompat.getParcelable(requireArguments(), DvrHalfSizedDialogFragment.KEY_PROGRAM, ProgramImpl::class.java)
        this.program = program
        // Bugfix: ohne Sendung/DvrManager nichts tun (Original: NPE)
        val dvrManager = dvrManager ?: return
        if (program == null) return
        duplicate = dvrManager.getRecordedProgram(program.title, program.seasonNumber, program.episodeNumber)
        if (duplicate == null) {
            dvrManager.addSchedule(program)
            DvrUiHelper.showAddScheduleToast(context, program.title, program.startTimeUtcMillis, program.endTimeUtcMillis)
            dismissDialog()
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = getString(R.string.dvr_already_recorded_dialog_title)
        val description = getString(R.string.dvr_already_recorded_dialog_description)
        val image = resources.getDrawable(R.drawable.quantum_ic_warning_white_96, null)
        return Guidance(title, description, null, image)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val context = requireContext()
        actions.add(GuidedAction.Builder(context).id(ACTION_RECORD_ANYWAY.toLong()).title(R.string.dvr_action_record_anyway).build())
        actions.add(GuidedAction.Builder(context).id(ACTION_WATCH.toLong()).title(R.string.dvr_action_watch_now).build())
        actions.add(GuidedAction.Builder(context).id(ACTION_CANCEL.toLong()).title(R.string.dvr_action_record_cancel).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        when (action.id) {
            ACTION_RECORD_ANYWAY.toLong() -> program?.let { dvrManager?.addSchedule(it) }
            ACTION_WATCH.toLong() -> DvrUiHelper.startDetailsActivity(requireActivity(), duplicate, null, false)
        }
        dismissDialog()
    }

    private companion object {
        const val ACTION_RECORD_ANYWAY = 1
        const val ACTION_WATCH = 2
        const val ACTION_CANCEL = 3
    }
}
