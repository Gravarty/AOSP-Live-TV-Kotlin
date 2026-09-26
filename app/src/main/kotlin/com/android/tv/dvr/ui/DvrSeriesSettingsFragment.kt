package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import android.util.LongSparseArray
import androidx.core.os.BundleCompat
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.ChannelImpl
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeasonEpisodeNumber
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.recorder.SeriesRecordingScheduler

/** Einstellungen einer Serienaufnahme (Priorität, Kanäle). GuidedStepFragment → SupportFragment. */
class DvrSeriesSettingsFragment : GuidedStepSupportFragment(), DvrDataManager.SeriesRecordingListener {
    private lateinit var dvrDataManager: DvrDataManager
    // Bugfix: nullable, da bei fehlender Serie/Sendungsliste nur finish() aufgerufen wird
    private var seriesRecording: SeriesRecording? = null
    private var seriesRecordingId = 0L
    /** SeriesRecording.OPTION_CHANNEL_ONE oder OPTION_CHANNEL_ALL (Abweichung: ohne @ChannelOption). */
    private var channelOption = SeriesRecording.OPTION_CHANNEL_ALL
    private var selectedChannelId = Channel.INVALID_ID
    private var backStackCount = 0
    private var showViewScheduleOptionInDialog = false
    private var currentProgram: Program? = null

    private var fragmentTitle: String? = null
    private var seriesRecordingTitle: String? = null
    private var priorityActionTitle: String? = null
    private var priorityActionHighestText: String? = null
    private var priorityActionLowestText: String? = null
    private var channelsActionTitle: String? = null
    private var channelsActionAllText: String? = null
    private val id2Channel = LongSparseArray<Channel>()
    private val channels = ArrayList<Channel>()
    private var programs: List<Program>? = null

    private var priorityGuidedAction: GuidedAction? = null
    private var channelsGuidedAction: GuidedAction? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        backStackCount = parentFragmentManager.backStackEntryCount
        dvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
        val args = requireArguments()
        seriesRecordingId = args.getLong(DvrSeriesSettingsActivity.SERIES_RECORDING_ID)
        val series = dvrDataManager.getSeriesRecording(seriesRecordingId)
        seriesRecording = series
        if (series == null) {
            requireActivity().finish()
            return
        }
        seriesRecordingTitle = series.title
        showViewScheduleOptionInDialog = args.getBoolean(DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG)
        currentProgram = BundleCompat.getParcelable(args, DvrSeriesSettingsActivity.CURRENT_PROGRAM, ProgramImpl::class.java)
        dvrDataManager.addSeriesRecordingListener(this)
        @Suppress("UNCHECKED_CAST")
        val programs = BigArguments.getArgument(DvrSeriesSettingsActivity.PROGRAM_LIST) as List<Program>?
        this.programs = programs
        BigArguments.reset()
        if (programs == null) {
            requireActivity().finish()
            return
        }
        val channelIds = HashSet<Long>()
        val channelDataManager = TvSingletons.getSingletons(context).getChannelDataManager()
        for (program in programs) {
            val channelId = program.channelId
            if (channelIds.add(channelId)) {
                val channel = channelDataManager.getChannel(channelId)
                if (channel != null) {
                    id2Channel.put(channel.id, channel)
                    channels.add(channel)
                }
            }
        }
        channelOption = series.channelOption
        selectedChannelId = Channel.INVALID_ID
        if (channelOption == SeriesRecording.OPTION_CHANNEL_ONE) {
            val channel = channelDataManager.getChannel(series.channelId)
            if (channel != null) {
                selectedChannelId = channel.id
            } else {
                channelOption = SeriesRecording.OPTION_CHANNEL_ALL
            }
        }
        channels.sortWith(ChannelImpl.CHANNEL_NUMBER_COMPARATOR)
        fragmentTitle = getString(R.string.dvr_series_settings_title)
        priorityActionTitle = getString(R.string.dvr_series_settings_priority)
        priorityActionHighestText = getString(R.string.dvr_series_settings_priority_highest)
        priorityActionLowestText = getString(R.string.dvr_series_settings_priority_lowest)
        channelsActionTitle = getString(R.string.dvr_series_settings_channels)
        channelsActionAllText = getString(R.string.dvr_series_settings_channels_all)
    }

    override fun onResume() {
        super.onResume()
        // Falls sich die Reihenfolge der Serien-Prioritäten geändert hat, ohne dass die Serie aktualisiert wurde
        updatePriorityGuidedAction()
    }

    override fun onDetach() {
        super.onDetach()
        dvrDataManager.removeSeriesRecordingListener(this)
    }

    override fun onDestroy() {
        if (parentFragmentManager.backStackEntryCount == backStackCount &&
            arguments?.getBoolean(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING) == true
        ) {
            dvrDataManager.checkAndRemoveEmptySeriesRecording(seriesRecordingId)
        }
        super.onDestroy()
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance =
        Guidance(fragmentTitle, null, seriesRecordingTitle, null)

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val priority = GuidedAction.Builder(activity)
            .id(ACTION_ID_PRIORITY)
            .title(priorityActionTitle)
            .build()
        priorityGuidedAction = priority
        actions.add(priority)

        val channelsAction = GuidedAction.Builder(activity)
            .id(ACTION_ID_CHANNEL)
            .title(channelsActionTitle)
            .subActions(buildChannelSubAction())
            .build()
        channelsGuidedAction = channelsAction
        actions.add(channelsAction)
        updateChannelsGuidedAction(false)
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_OK).build())
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val actionId = action.id
        if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepSupportFragments()
            return
        }
        // Abweichung: ohne Serie nichts weiter tun (Fragment wird bereits beendet); Abbrechen oben vorgezogen
        val series = seriesRecording ?: return
        if (actionId == GuidedAction.ACTION_ID_OK) {
            if (channelOption != series.channelOption || series.isStopped ||
                (channelOption == SeriesRecording.OPTION_CHANNEL_ONE && series.channelId != selectedChannelId)
            ) {
                val builder = SeriesRecording.buildFrom(series)
                    .setChannelOption(channelOption)
                    .setState(SeriesRecording.STATE_SERIES_NORMAL)
                if (selectedChannelId != Channel.INVALID_ID) {
                    builder.setChannelId(selectedChannelId)
                }
                val dvrManager = TvSingletons.getSingletons(requireContext()).getDvrManager()
                dvrManager?.updateSeriesRecording(builder.build())
                val currentProgram = currentProgram
                if (currentProgram != null &&
                    (channelOption == SeriesRecording.OPTION_CHANNEL_ALL || selectedChannelId == currentProgram.channelId)
                ) {
                    dvrManager?.addSchedule(currentProgram)
                }
                updateSchedulesToSeries()
                showConfirmDialog()
            } else {
                showConfirmDialog()
            }
        } else if (actionId == ACTION_ID_PRIORITY) {
            val fragment = DvrPrioritySettingsFragment()
            val args = Bundle()
            args.putLong(DvrPrioritySettingsFragment.COME_FROM_SERIES_RECORDING_ID, series.id)
            fragment.arguments = args
            GuidedStepSupportFragment.add(parentFragmentManager, fragment, R.id.dvr_settings_view_frame)
        }
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        val actionId = action.id
        if (actionId == SUB_ACTION_ID_CHANNEL_ALL) {
            channelOption = SeriesRecording.OPTION_CHANNEL_ALL
            selectedChannelId = Channel.INVALID_ID
            updateChannelsGuidedAction(true)
            return true
        } else if (actionId > SUB_ACTION_ID_CHANNEL_ONE_BASE) {
            channelOption = SeriesRecording.OPTION_CHANNEL_ONE
            selectedChannelId = actionId - SUB_ACTION_ID_CHANNEL_ONE_BASE
            updateChannelsGuidedAction(true)
            return true
        }
        return false
    }

    override fun onCreateButtonActionsStylist(): GuidedActionsStylist = DvrGuidedActionsStylist(true)

    private fun updateChannelsGuidedAction(notifyActionChanged: Boolean) {
        val channelsAction = channelsGuidedAction ?: return
        if (channelOption == SeriesRecording.OPTION_CHANNEL_ALL) {
            channelsAction.description = channelsActionAllText
        } else {
            id2Channel.get(selectedChannelId)?.let { channelsAction.description = it.displayText }
        }
        if (notifyActionChanged) {
            notifyActionChanged(findActionPositionById(ACTION_ID_CHANNEL))
        }
    }

    private fun updatePriorityGuidedAction() {
        // Bugfix: ohne Serie bzw. vor dem Anlegen der Aktionen nichts tun (Original: NPE)
        val series = seriesRecording ?: return
        val priorityAction = priorityGuidedAction ?: return
        var totalSeriesCount = 0
        var priorityOrder = 0
        for (other in dvrDataManager.getSeriesRecordings()) {
            if (other.state == SeriesRecording.STATE_SERIES_NORMAL || other.id == series.id) {
                ++totalSeriesCount
            }
            if (other.state == SeriesRecording.STATE_SERIES_NORMAL &&
                other.id != series.id &&
                other.priority > series.priority
            ) {
                ++priorityOrder
            }
        }
        priorityAction.description = when {
            priorityOrder == 0 -> priorityActionHighestText
            priorityOrder >= totalSeriesCount - 1 -> priorityActionLowestText
            else -> getString(R.string.dvr_series_settings_priority_rank, priorityOrder + 1)
        }
        notifyActionChanged(findActionPositionById(ACTION_ID_PRIORITY))
    }

    private fun updateSchedulesToSeries() {
        val series = seriesRecording ?: return
        val programs = programs ?: return
        val recordingCandidates = ArrayList<Program>()
        val scheduledEpisodes = HashSet<SeasonEpisodeNumber>()
        for (r in dvrDataManager.getScheduledRecordings(seriesRecordingId)) {
            if (r.state != ScheduledRecording.STATE_RECORDING_FAILED && r.state != ScheduledRecording.STATE_RECORDING_CLIPPED) {
                scheduledEpisodes.add(SeasonEpisodeNumber(r.seriesRecordingId, r.seasonNumber, r.episodeNumber))
            }
        }
        for (program in programs) {
            // Laufende Sendungen und bereits geplante Folgen ausschließen, Kanaloption beachten
            if (program.startTimeUtcMillis >= System.currentTimeMillis() &&
                series.matchProgram(program) &&
                !scheduledEpisodes.contains(SeasonEpisodeNumber(seriesRecordingId, program.seasonNumber, program.episodeNumber))
            ) {
                recordingCandidates.add(program)
            }
        }
        if (recordingCandidates.isEmpty()) {
            return
        }
        // Bugfix: fehlender Eintrag führte im Original zu einer NPE
        val programsToSchedule = SeriesRecordingScheduler.pickOneProgramPerEpisode(
            dvrDataManager,
            listOf(series),
            recordingCandidates,
        ).get(seriesRecordingId).orEmpty()
        if (programsToSchedule.isNotEmpty()) {
            TvSingletons.getSingletons(requireContext()).getDvrManager()?.addScheduleToSeriesRecording(series, programsToSchedule)
        }
    }

    private fun buildChannelSubAction(): List<GuidedAction> {
        val channelSubActions = ArrayList<GuidedAction>()
        channelSubActions.add(
            GuidedAction.Builder(activity)
                .id(SUB_ACTION_ID_CHANNEL_ALL)
                .title(channelsActionAllText)
                .build(),
        )
        for (channel in channels) {
            channelSubActions.add(
                GuidedAction.Builder(activity)
                    .id(SUB_ACTION_ID_CHANNEL_ONE_BASE + channel.id)
                    .title(channel.displayText)
                    .build(),
            )
        }
        return channelSubActions
    }

    private fun showConfirmDialog() {
        DvrUiHelper.startSeriesScheduledDialogActivity(
            requireContext(),
            seriesRecording,
            showViewScheduleOptionInDialog,
            programs.orEmpty(),
        )
        finishGuidedStepSupportFragments()
    }

    override fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording) {}

    override fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording) {
        for (series in seriesRecordings) {
            if (series.id == seriesRecording?.id) {
                finishGuidedStepSupportFragments()
                return
            }
        }
    }

    override fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording) {
        for (series in seriesRecordings) {
            if (series.id == seriesRecordingId) {
                seriesRecording = series
                updatePriorityGuidedAction()
                return
            }
        }
    }

    companion object {
        private const val TAG = "SeriesSettingsFragment"

        private const val ACTION_ID_PRIORITY = 10L
        private const val ACTION_ID_CHANNEL = 11L

        private const val SUB_ACTION_ID_CHANNEL_ALL = 102L
        // Aktions-ID je Kanal = SUB_ACTION_ID_CHANNEL_ONE_BASE + Kanal-ID
        private const val SUB_ACTION_ID_CHANNEL_ONE_BASE = 500L
    }
}
