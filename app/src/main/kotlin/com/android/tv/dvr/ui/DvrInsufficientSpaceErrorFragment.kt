package com.android.tv.dvr.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.dvr.ui.browse.DvrBrowseActivity

/** Fehlerdialog: Aufnahmen sind mangels Speicherplatz fehlgeschlagen. */
class DvrInsufficientSpaceErrorFragment : DvrGuidedStepFragment() {
    private var failedScheduledRecordingInfos: ArrayList<String>? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        failedScheduledRecordingInfos = arguments?.getStringArrayList(FAILED_SCHEDULED_RECORDING_INFOS)
        SoftPreconditions.checkState(!failedScheduledRecordingInfos.isNullOrEmpty(), TAG, "failed scheduled recording is null")
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        // Bugfix: leere/fehlende Liste führte im Original zu NPE/IndexOutOfBounds
        val infos = failedScheduledRecordingInfos.orEmpty()
        val title: String?
        val description: String?
        when (infos.size) {
            0 -> {
                title = null
                description = null
            }
            1 -> {
                title = getString(R.string.dvr_error_insufficient_space_title_one_recording, infos[0])
                description = getString(R.string.dvr_error_insufficient_space_description_one_recording, infos[0])
            }
            2 -> {
                title = getString(R.string.dvr_error_insufficient_space_title_two_recordings, infos[0], infos[1])
                description = getString(R.string.dvr_error_insufficient_space_description_two_recordings, infos[0], infos[1])
            }
            else -> {
                title = getString(R.string.dvr_error_insufficient_space_title_three_or_more_recordings, infos[0], infos[1], infos[2])
                description = getString(R.string.dvr_error_insufficient_space_description_three_or_more_recordings,
                    infos[0], infos[1], infos[2])
            }
        }
        return Guidance(title, description, null, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_OK).build())
        if (TvSingletons.getSingletons(activity).getDvrManager()?.hasValidItems() == true) {
            actions.add(GuidedAction.Builder(activity).id(ACTION_VIEW_RECENT_RECORDINGS.toLong())
                .title(resources.getString(R.string.dvr_error_insufficient_space_action_view_recent_recordings)).build())
        }
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        if (action.id == ACTION_VIEW_RECENT_RECORDINGS.toLong()) {
            val activity = requireActivity()
            activity.startActivity(Intent(activity, DvrBrowseActivity::class.java))
        }
        dismissDialog()
    }

    companion object {
        /** Infos zu den fehlgeschlagenen Aufnahmen (ArrayList<String>). */
        const val FAILED_SCHEDULED_RECORDING_INFOS = "failed_scheduled_recording_infos"
        private const val TAG = "DvrInsufficientSpaceErrorFragment"
        private const val ACTION_VIEW_RECENT_RECORDINGS = 1
    }
}
