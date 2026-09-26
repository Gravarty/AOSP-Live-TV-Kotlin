package com.android.tv.dvr.ui

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.recorder.ConflictChecker
import com.android.tv.dvr.recorder.ConflictChecker.OnUpcomingConflictChangeListener
import com.android.tv.util.Utils

/** Basis der Konflikt-Dialoge (Sendung, Kanalaufnahme, Kanalwechsel). */
abstract class DvrConflictFragment : DvrGuidedStepFragment() {
    /** Kollidierende Aufnahmen. */
    protected var conflicts: List<ScheduledRecording> = emptyList()

    override fun onProvideTheme(): Int = R.style.Theme_TV_Dvr_Conflict_GuidedStep

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireContext()).clickAction(GuidedAction.ACTION_ID_OK).build())
        actions.add(GuidedAction.Builder(requireContext()).id(ACTION_VIEW_SCHEDULES.toLong())
            .title(R.string.dvr_action_view_schedules).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        if (action.id == ACTION_VIEW_SCHEDULES.toLong()) {
            DvrUiHelper.startSchedulesActivityForOneTimeRecordingConflict(requireContext(), conflicts)
        }
        dismissDialog()
        // Aufnahme-Einstellungen beim Schließen ebenfalls beenden
        val activity = activity
        if (activity is DvrRecordingSettingsActivity) activity.finish()
    }

    /** Beschreibung mit den Titeln der kollidierenden Aufnahmen; null, wenn keine mehr übrig sind. */
    internal fun getConflictDescription(): String? {
        val titles = conflicts.mapNotNull { getScheduleTitle(it) }.distinct()
        return when (titles.size) {
            0 -> {
                Log.i(TAG, "Conflict has been resolved by any reason. Maybe input might have been deleted.")
                null
            }
            1 -> resources.getString(R.string.dvr_program_conflict_dialog_description_1, titles[0])
            2 -> resources.getString(R.string.dvr_program_conflict_dialog_description_2, titles[0], titles[1])
            3 -> resources.getString(R.string.dvr_program_conflict_dialog_description_3, titles[0], titles[1])
            else -> resources.getQuantityString(R.plurals.dvr_program_conflict_dialog_description_many,
                titles.size - LISTED_PROGRAM_COUNT, titles[0], titles[1], titles.size - LISTED_PROGRAM_COUNT)
        }
    }

    private fun getScheduleTitle(schedule: ScheduledRecording): String? =
        if (schedule.type == ScheduledRecording.TYPE_TIMED) {
            TvSingletons.getSingletons(requireContext()).getChannelDataManager().getChannel(schedule.channelId)?.displayName
        } else {
            schedule.programTitle
        }

    /** Konflikt beim Planen einer Sendung. */
    class DvrProgramConflictFragment : DvrConflictFragment() {
        private var program: Program? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            arguments?.let { program = BundleCompat.getParcelable(it, DvrHalfSizedDialogFragment.KEY_PROGRAM, ProgramImpl::class.java) }
            val program = program
            SoftPreconditions.checkArgument(program != null, null, null)
            val input = Utils.getTvInputInfoForProgram(requireContext(), program)
            SoftPreconditions.checkState(input != null, null, null)
            var conflicts: List<ScheduledRecording>? = null
            if (input != null && program != null) {
                conflicts = TvSingletons.getSingletons(requireContext()).getDvrManager()?.getConflictingSchedules(program)
            }
            if (conflicts.isNullOrEmpty()) dismissDialog()
            this.conflicts = conflicts.orEmpty()
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
            val title = resources.getString(R.string.dvr_program_conflict_dialog_title)
            val descriptionPrefix = getString(R.string.dvr_program_conflict_dialog_description_prefix, program?.title)
            val description = getConflictDescription()
            if (description == null) dismissDialog()
            val icon = resources.getDrawable(R.drawable.quantum_ic_error_white_48, null)
            // Bugfix: kein „null“ im Text, wenn keine Konflikte mehr übrig sind
            return Guidance(title, "$descriptionPrefix ${description.orEmpty()}", null, icon)
        }
    }

    /** Konflikt beim Aufnehmen eines Kanals (feste Dauer). */
    class DvrChannelRecordConflictFragment : DvrConflictFragment() {
        private var channel: Channel? = null
        private var startTimeMs = 0L
        private var endTimeMs = 0L

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val args = requireArguments()
            val channelId = args.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID)
            val channel = TvSingletons.getSingletons(requireContext()).getChannelDataManager().getChannel(channelId)
            this.channel = channel
            SoftPreconditions.checkArgument(channel != null, null, null)
            // Bugfix: fehlender Kanal (Original: NPE)
            val input = channel?.let { Utils.getTvInputInfoForChannelId(requireContext(), it.id) }
            SoftPreconditions.checkState(input != null, null, null)
            var conflicts: List<ScheduledRecording>? = null
            if (input != null && channel != null) {
                startTimeMs = args.getLong(DvrHalfSizedDialogFragment.KEY_START_TIME_MS)
                endTimeMs = args.getLong(DvrHalfSizedDialogFragment.KEY_END_TIME_MS)
                conflicts = TvSingletons.getSingletons(requireContext()).getDvrManager()
                    ?.getConflictingSchedules(channel.id, startTimeMs, endTimeMs)
            }
            if (conflicts.isNullOrEmpty()) dismissDialog()
            this.conflicts = conflicts.orEmpty()
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
            val title = resources.getString(R.string.dvr_channel_conflict_dialog_title)
            val descriptionPrefix = getString(R.string.dvr_channel_conflict_dialog_description_prefix, channel?.displayName)
            val description = getConflictDescription()
            if (description == null) dismissDialog()
            val icon = resources.getDrawable(R.drawable.quantum_ic_error_white_48, null)
            // Bugfix: kein „null“ im Text, wenn keine Konflikte mehr übrig sind
            return Guidance(title, "$descriptionPrefix ${description.orEmpty()}", null, icon)
        }
    }

    /**
     * Konflikt beim Kanalwechsel; schließt sich, sobald keine anstehenden Konflikte mehr bestehen.
     */
    class DvrChannelWatchConflictFragment : DvrConflictFragment(), OnUpcomingConflictChangeListener {
        private var channelId = Channel.INVALID_ID

        /**
         * Abweichung: MainActivity hat keinen ConflictChecker (SHOW_UPCOMING_CONFLICT_DIALOG ist im
         * Original aus, getDvrConflictChecker() liefert dort dann null) → immer null.
         */
        private fun getConflictChecker(): ConflictChecker? = null

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            arguments?.let { channelId = it.getLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID) }
            SoftPreconditions.checkArgument(channelId != Channel.INVALID_ID, null, null)
            val checker = getConflictChecker()
            var conflicts: List<ScheduledRecording>? = null
            if (checker != null) {
                checker.addOnUpcomingConflictChangeListener(this)
                conflicts = checker.getUpcomingConflicts()
                if (DEBUG) Log.d(TAG, "onCreateView: upcoming conflicts: $conflicts")
                if (conflicts.isEmpty()) dismissDialog()
            }
            if (conflicts == null) {
                if (DEBUG) Log.d(TAG, "onCreateView: There's no conflict.")
                conflicts = emptyList()
            }
            if (conflicts.isEmpty()) dismissDialog()
            this.conflicts = conflicts
            return super.onCreateView(inflater, container, savedInstanceState)
        }

        override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
            val title = resources.getString(R.string.dvr_epg_channel_watch_conflict_dialog_title)
            val description = resources.getString(R.string.dvr_epg_channel_watch_conflict_dialog_description)
            return Guidance(title, description, null, null)
        }

        override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
            actions.add(GuidedAction.Builder(requireContext()).id(ACTION_DELETE_CONFLICT.toLong())
                .title(R.string.dvr_action_delete_schedule).build())
            actions.add(GuidedAction.Builder(requireContext()).id(ACTION_CANCEL.toLong())
                .title(R.string.dvr_action_record_program).build())
        }

        override fun onTrackedGuidedActionClicked(action: GuidedAction) {
            if (action.id == ACTION_CANCEL.toLong()) {
                getConflictChecker()?.setCheckedConflictsForChannel(channelId, conflicts)
            } else if (action.id == ACTION_DELETE_CONFLICT.toLong()) {
                for (schedule in conflicts) {
                    if (schedule.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                        dvrManager?.stopRecording(schedule)
                    } else {
                        dvrManager?.removeScheduledRecording(schedule)
                    }
                }
            }
            // Bugfix: Original ruft super.onGuidedActionClicked() → endlose Rekursion über
            // onTrackedGuidedActionClicked; gemeint ist die Basisbehandlung (Dialog schließen)
            super.onTrackedGuidedActionClicked(action)
        }

        override fun onDetach() {
            getConflictChecker()?.removeOnUpcomingConflictChangeListener(this)
            super.onDetach()
        }

        override fun onUpcomingConflictChange() {
            val checker = getConflictChecker()
            if (checker == null || checker.getUpcomingConflicts().isEmpty()) {
                if (DEBUG) Log.d(TAG, "onUpcomingConflictChange: There's no conflict.")
                dismissDialog()
            }
        }
    }

    private companion object {
        const val TAG = "DvrConflictFragment"
        const val DEBUG = false
        const val ACTION_DELETE_CONFLICT = 1
        const val ACTION_CANCEL = 2
        const val ACTION_VIEW_SCHEDULES = 3
        // Anzahl der namentlich genannten Sendungen in R.plurals.dvr_program_conflict_dialog_description_many
        const val LISTED_PROGRAM_COUNT = 2
    }
}
