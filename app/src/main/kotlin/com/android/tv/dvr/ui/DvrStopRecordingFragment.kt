package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener
import com.android.tv.dvr.data.ScheduledRecording

/** Fragt, ob die laufende Aufnahme des Kanals beendet werden soll. */
class DvrStopRecordingFragment : DvrGuidedStepFragment() {

    private var schedule: ScheduledRecording? = null
    private var dvrDataManager: DvrDataManager? = null
    // Abweichung: @ReasonType (IntDef) entfällt; Werte siehe REASON_*
    private var stopReason = 0

    private val scheduledRecordingListener = object : ScheduledRecordingListener {
        override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {}

        override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
            if (scheduledRecordings.any { it.id == schedule?.id }) dismissDialog()
        }

        override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
            if (scheduledRecordings.any { it.id == schedule?.id && it.state != ScheduledRecording.STATE_RECORDING_IN_PROGRESS }) {
                dismissDialog()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val args = requireArguments()
        val channelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID)
        schedule = dvrManager?.getCurrentRecording(channelId)
        if (schedule == null) {
            dismissDialog()
            return
        }
        dvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager().also {
            it.addScheduledRecordingListener(scheduledRecordingListener)
        }
        stopReason = args.getInt(KEY_REASON)
    }

    override fun onDetach() {
        dvrDataManager?.removeScheduledRecordingListener(scheduledRecordingListener)
        super.onDetach()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = getString(R.string.dvr_stop_recording_dialog_title)
        val description = if (stopReason == REASON_ON_CONFLICT) {
            // Bugfix: schedule kann null sein, wenn der Dialog bereits geschlossen wird (Original: NPE)
            getString(R.string.dvr_stop_recording_dialog_description_on_conflict,
                schedule?.getProgramDisplayTitle(requireContext()))
        } else {
            getString(R.string.dvr_stop_recording_dialog_description)
        }
        val image = resources.getDrawable(R.drawable.quantum_ic_warning_white_96, null)
        return Guidance(title, description, null, image)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val context = requireContext()
        actions.add(GuidedAction.Builder(context).id(ACTION_STOP).title(R.string.dvr_action_stop).build())
        actions.add(GuidedAction.Builder(context).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    companion object {
        /**
         * Aktions-ID „Beenden“. Abweichung: Long statt int, damit Aufrufer direkt mit der
         * actionId (Long) aus OnActionClickListener vergleichen können.
         */
        const val ACTION_STOP = 1L
        /** Grund für den Dialog (Int, REASON_*). */
        const val KEY_REASON = "DvrStopRecordingFragment.type"
        /** Nutzer möchte eine laufende Aufnahme beenden. */
        const val REASON_USER_STOP = 1
        /** Nutzer möchte eine Sendung aufnehmen, die mit der laufenden Aufnahme kollidiert. */
        const val REASON_ON_CONFLICT = 2
    }
}
