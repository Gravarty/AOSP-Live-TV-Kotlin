package com.android.tv.dvr.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording

/** Fragt, ob die Serienaufnahme beendet werden soll. */
class DvrStopSeriesRecordingFragment : DvrGuidedStepFragment() {
    private var seriesRecording: SeriesRecording? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        seriesRecording = BundleCompat.getParcelable(requireArguments(), KEY_SERIES_RECORDING, SeriesRecording::class.java)
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val title = getString(R.string.dvr_series_schedules_stop_dialog_title)
        val description = getString(R.string.dvr_series_schedules_stop_dialog_description)
        val icon = requireContext().getDrawable(R.drawable.ic_dvr_delete)
        return GuidanceStylist.Guidance(title, description, null, icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        actions.add(GuidedAction.Builder(activity).id(ACTION_STOP_SERIES_RECORDING.toLong())
            .title(R.string.dvr_series_schedules_stop_dialog_action_stop).build())
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        val seriesRecording = seriesRecording
        val singletons = TvSingletons.getSingletons(requireContext())
        val dvrManager = singletons.getDvrManager()
        // Bugfix: ohne Serie/DvrManager nur schließen (Original: NPE)
        if (action.id == ACTION_STOP_SERIES_RECORDING.toLong() && seriesRecording != null && dvrManager != null) {
            val toDelete = ArrayList<ScheduledRecording>()
            for (r in singletons.getDvrDataManager().getAvailableScheduledRecordings()) {
                if (r.seriesRecordingId == seriesRecording.id) {
                    if (r.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED) toDelete.add(r) else dvrManager.stopRecording(r)
                }
            }
            if (toDelete.isNotEmpty()) dvrManager.forceRemoveScheduledRecording(*ScheduledRecording.toArray(toDelete))
            dvrManager.updateSeriesRecording(
                SeriesRecording.buildFrom(seriesRecording).setState(SeriesRecording.STATE_SERIES_STOPPED).build())
        }
        dismissDialog()
    }

    companion object {
        /** Zu beendende Serienaufnahme (SeriesRecording, Parcelable). */
        const val KEY_SERIES_RECORDING = "key_series_recoridng"
        private const val ACTION_STOP_SERIES_RECORDING = 1
    }
}
