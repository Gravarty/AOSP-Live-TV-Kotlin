package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import android.text.format.DateUtils
import androidx.core.os.BundleCompat
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.ScheduledRecording

/**
 * Weist darauf hin, dass dieselbe Folge bereits geplant ist.
 *
 * Der Aufnahmeplan ist zu diesem Zeitpunkt noch nicht angelegt.
 */
class DvrAlreadyScheduledFragment : DvrGuidedStepFragment() {
    private var program: Program? = null
    private var duplicate: ScheduledRecording? = null

    // Abweichung: keine Dagger-Injektion von DvrFlags; startEarlyEndLateEnabled() ist im AOSP-Build false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val program = BundleCompat.getParcelable(requireArguments(), DvrHalfSizedDialogFragment.KEY_PROGRAM, ProgramImpl::class.java)
        this.program = program
        // Bugfix: ohne Sendung/DvrManager nichts tun (Original: NPE)
        val dvrManager = dvrManager ?: return
        if (program == null) return
        duplicate = dvrManager.getScheduledRecording(program.title, program.seasonNumber, program.episodeNumber)
        if (duplicate == null) {
            dvrManager.addSchedule(program)
            DvrUiHelper.showAddScheduleToast(context, program.title, program.startTimeUtcMillis, program.endTimeUtcMillis)
            dismissDialog()
        }
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = getString(R.string.dvr_already_scheduled_dialog_title)
        // Bugfix: duplicate kann null sein, wenn der Dialog bereits geschlossen wird (Original: NPE)
        val startTimeMs = duplicate?.startTimeMs ?: 0L
        val description = getString(R.string.dvr_already_scheduled_dialog_description,
            DateUtils.formatDateTime(requireContext(), startTimeMs, DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_DATE))
        val image = resources.getDrawable(R.drawable.quantum_ic_warning_white_96, null)
        return Guidance(title, description, null, image)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val context = requireContext()
        actions.add(GuidedAction.Builder(context).id(ACTION_RECORD_ANYWAY.toLong()).title(R.string.dvr_action_record_anyway).build())
        actions.add(GuidedAction.Builder(context).id(ACTION_RECORD_INSTEAD.toLong()).title(R.string.dvr_action_record_instead).build())
        actions.add(GuidedAction.Builder(context).id(ACTION_CANCEL.toLong()).title(R.string.dvr_action_record_cancel).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        val program = program
        val dvrManager = dvrManager
        if (program != null && dvrManager != null) {
            when (action.id) {
                ACTION_RECORD_ANYWAY.toLong() -> dvrManager.addSchedule(program)
                ACTION_RECORD_INSTEAD.toLong() -> {
                    duplicate?.let { dvrManager.removeScheduledRecording(it) }
                    dvrManager.addSchedule(program)
                }
            }
        }
        dismissDialog()
    }

    private companion object {
        const val ACTION_RECORD_ANYWAY = 1
        const val ACTION_RECORD_INSTEAD = 2
        const val ACTION_CANCEL = 3
    }
}
