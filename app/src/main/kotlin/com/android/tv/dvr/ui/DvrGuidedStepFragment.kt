package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.VerticalGridView
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dialog.HalfSizedDialogFragment.OnActionClickListener
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.RecordingStorageStatusManager

/**
 * Basis der DVR-GuidedSteps in halbhohen Dialogen.
 *
 * Abweichung: Tracker-Präfixe/-Labels der Unterklassen entfallen (Analytics entfernt).
 */
abstract class DvrGuidedStepFragment : TrackedGuidedStepFragment() {
    /** Null, wenn DVR nicht verfügbar ist (TvSingletons liefert DvrManager?). */
    protected var dvrManager: DvrManager? = null
        private set
    private var onActionClickListener: OnActionClickListener? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dvrManager = TvSingletons.getSingletons(context).getDvrManager()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        guidedActionsStylist.actionsGridView?.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE)
        guidedButtonActionsStylist.actionsGridView?.setWindowAlignment(VerticalGridView.WINDOW_ALIGN_BOTH_EDGE)
        return view
    }

    override fun onProvideTheme(): Int = R.style.Theme_TV_Dvr_GuidedStep

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        onActionClickListener?.onActionClick(action.id)
        dismissDialog()
    }

    /** Schließt den umgebenden halbhohen Dialog. */
    protected open fun dismissDialog() {
        val activity = activity
        if (activity is MainActivity) {
            val currentDialog = activity.overlayManager.currentDialog
            if (currentDialog is DvrHalfSizedDialogFragment) currentDialog.dismiss()
        } else {
            (parentFragment as? DialogFragment)?.dismiss()
        }
    }

    /** Abweichung: öffentlich statt protected (Java: Paketzugriff aus DvrHalfSizedDialogFragment). */
    fun setOnActionClickListener(listener: OnActionClickListener?) {
        onActionClickListener = listener
    }

    /** Innerer GuidedStep für [DvrHalfSizedDialogFragment.DvrNoFreeSpaceErrorDialogFragment]. */
    class DvrNoFreeSpaceErrorFragment : DvrGuidedStepFragment() {
        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance =
            GuidanceStylist.Guidance(getString(R.string.dvr_error_no_free_space_title),
                getString(R.string.dvr_error_no_free_space_description), null, null)

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            val activity = requireActivity()
            actions.add(GuidedAction.Builder(activity).id(ACTION_RECORD_ANYWAY.toLong())
                .title(R.string.dvr_action_record_anyway).build())
            actions.add(GuidedAction.Builder(activity).id(ACTION_DELETE_RECORDINGS.toLong())
                .title(R.string.dvr_action_delete_recordings).build())
            actions.add(GuidedAction.Builder(activity).id(ACTION_CANCEL_RECORDING.toLong())
                .title(R.string.dvr_action_record_cancel).build())
        }
    }

    /** Innerer GuidedStep für [DvrHalfSizedDialogFragment.DvrSmallSizedStorageErrorDialogFragment]. */
    class DvrSmallSizedStorageErrorFragment : DvrGuidedStepFragment() {
        override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
            val title = resources.getString(R.string.dvr_error_small_sized_storage_title)
            val description = resources.getString(R.string.dvr_error_small_sized_storage_description,
                RecordingStorageStatusManager.MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES / 1024 / 1024 / 1024)
            return GuidanceStylist.Guidance(title, description, null, null)
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.add(GuidedAction.Builder(requireActivity()).id(GuidedAction.ACTION_ID_OK)
                .title(android.R.string.ok).build())
        }

        override fun onTrackedGuidedActionClicked(action: GuidedAction) = dismissDialog()
    }

    companion object {
        /** Aktion „trotzdem aufnehmen/planen“. */
        const val ACTION_RECORD_ANYWAY = 1
        /** Aktion „vorhandene Aufnahmen löschen“. */
        const val ACTION_DELETE_RECORDINGS = 2
        /** Aktion „Aufnahmeanfrage abbrechen“. */
        const val ACTION_CANCEL_RECORDING = 3
        const val UNKNOWN_DVR_ACTION = "Unknown DVR Action"
    }
}
