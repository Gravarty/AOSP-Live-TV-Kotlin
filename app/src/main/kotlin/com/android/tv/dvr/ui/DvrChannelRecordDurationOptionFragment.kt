package com.android.tv.dvr.ui

import android.os.Bundle
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.api.Channel
import com.android.tv.dvr.ui.DvrConflictFragment.DvrChannelRecordConflictFragment
import java.util.concurrent.TimeUnit

/** Auswahl der Aufnahmedauer für einen Kanal ohne Sendungsinformation. */
class DvrChannelRecordDurationOptionFragment : DvrGuidedStepFragment() {
    private val durations = ArrayList<Long>()
    private var channel: Channel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        arguments?.let { args ->
            val channelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID)
            channel = TvSingletons.getSingletons(requireContext()).getChannelDataManager().getChannel(channelId)
        }
        SoftPreconditions.checkArgument(channel != null, null, null)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = resources.getString(R.string.dvr_channel_record_duration_dialog_title)
        val icon = resources.getDrawable(R.drawable.ic_dvr, null)
        return Guidance(title, null, null, icon)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        durations.clear()
        durations.add(TimeUnit.MINUTES.toMillis(10))
        durations.add(TimeUnit.MINUTES.toMillis(30))
        durations.add(TimeUnit.HOURS.toMillis(1))
        durations.add(TimeUnit.HOURS.toMillis(3))
        val titles = intArrayOf(
            R.string.recording_start_dialog_10_min_duration,
            R.string.recording_start_dialog_30_min_duration,
            R.string.recording_start_dialog_1_hour_duration,
            R.string.recording_start_dialog_3_hours_duration,
        )
        // Aktions-ID = Index in durations (0..3)
        titles.forEachIndexed { actionId, title ->
            actions.add(GuidedAction.Builder(requireContext()).id(actionId.toLong()).title(title).build())
        }
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        // Bugfix: ohne Kanal/DvrManager nur schließen (Original: NPE)
        val channel = channel
        val dvrManager = TvSingletons.getSingletons(requireContext()).getDvrManager()
        if (channel == null || dvrManager == null) {
            dismissDialog()
            return
        }
        val duration = durations[action.id.toInt()]
        val startTimeMs = System.currentTimeMillis()
        val endTimeMs = System.currentTimeMillis() + duration
        val conflicts = dvrManager.getConflictingSchedules(channel.id, startTimeMs, endTimeMs)
        dvrManager.addSchedule(channel, startTimeMs, endTimeMs)
        if (conflicts.isEmpty()) {
            dismissDialog()
        } else {
            val fragment: GuidedStepSupportFragment = DvrChannelRecordConflictFragment()
            fragment.arguments = Bundle().apply {
                putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.id)
                putLong(DvrHalfSizedDialogFragment.KEY_START_TIME_MS, startTimeMs)
                putLong(DvrHalfSizedDialogFragment.KEY_END_TIME_MS, endTimeMs)
            }
            GuidedStepSupportFragment.add(parentFragmentManager, fragment, R.id.halfsized_dialog_host)
        }
    }
}
