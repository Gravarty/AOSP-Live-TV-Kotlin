package com.android.tv.dvr.ui

import android.content.Context
import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.ProgramImpl
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrManager
import java.util.concurrent.TimeUnit

/** Aufnahme-Einstellungen einer Sendung: früher starten / später beenden. GuidedStepFragment → SupportFragment. */
class DvrRecordingSettingsFragment : GuidedStepSupportFragment() {
    // Bugfix: nullable, da bei fehlender Sendung nur finish() aufgerufen wird und die Views trotzdem entstehen
    private var program: Program? = null
    private var fragmentTitle: String? = null
    private var startEarlyActionTitle: String? = null
    private var endLateActionTitle: String? = null
    private var timeActionOnTimeText: String? = null
    private var timeActionOneMinText: String? = null
    private var timeActionFiveMinText: String? = null
    private var timeActionFifteenMinText: String? = null
    private var timeActionHalfHourText: String? = null
    private var timeActionOneHourText: String? = null
    private var timeActionTwoHoursText: String? = null
    private var timeActionThreeHoursText: String? = null

    private var startEarlyGuidedAction: GuidedAction? = null
    private var endLateGuidedAction: GuidedAction? = null
    private var startEarlyTime = 0L
    private var endLateTime = 0L
    private var dvrManager: DvrManager? = null

    override fun onAttach(context: Context) {
        super.onAttach(context)
        dvrManager = TvSingletons.getSingletons(context).getDvrManager()
        program = arguments?.let { BundleCompat.getParcelable(it, DvrRecordingSettingsActivity.PROGRAM, ProgramImpl::class.java) }
        if (program == null) {
            requireActivity().finish()
            return
        }
        fragmentTitle = getString(R.string.dvr_recording_settings_title)
        startEarlyActionTitle = getString(R.string.dvr_start_early_title)
        endLateActionTitle = getString(R.string.dvr_end_late_title)
        timeActionOnTimeText = getString(R.string.dvr_recording_settings_time_none)
        timeActionOneMinText = getString(R.string.dvr_recording_settings_time_one_min)
        timeActionFiveMinText = getString(R.string.dvr_recording_settings_time_five_mins)
        timeActionFifteenMinText = getString(R.string.dvr_recording_settings_time_fifteen_mins)
        timeActionHalfHourText = getString(R.string.dvr_recording_settings_time_half_hour)
        timeActionOneHourText = getString(R.string.dvr_recording_settings_time_one_hour)
        timeActionTwoHoursText = getString(R.string.dvr_recording_settings_time_two_hours)
        timeActionThreeHoursText = getString(R.string.dvr_recording_settings_time_three_hours)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val breadcrumb = program?.title
        val title = fragmentTitle
        // Bugfix: fehlende Werte nicht als "null" anzeigen
        val description = "${program?.episodeTitle.orEmpty()}\n${program?.description.orEmpty()}"
        return Guidance(title, description, breadcrumb, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val startEarly = GuidedAction.Builder(activity)
            .id(ACTION_ID_START_EARLY)
            .title(startEarlyActionTitle)
            .description(timeActionOnTimeText)
            .subActions(buildChannelSubActionStart())
            .build()
        startEarlyGuidedAction = startEarly
        actions.add(startEarly)

        val endLate = GuidedAction.Builder(activity)
            .id(ACTION_ID_END_LATE)
            .title(endLateActionTitle)
            .description(timeActionOnTimeText)
            .subActions(buildChannelSubActionEnd())
            .build()
        endLateGuidedAction = endLate
        actions.add(endLate)
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_OK).build())
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val actionId = action.id
        if (actionId == GuidedAction.ACTION_ID_OK) {
            // Abweichung: ohne Sendung oder DVR nichts tun
            val program = program ?: return
            val dvrManager = dvrManager ?: return
            var startEarlyTimeMs = TimeUnit.MINUTES.toMillis(startEarlyTime)
            val endLateTimeMs = TimeUnit.MINUTES.toMillis(endLateTime)
            var startTimeMs = program.startTimeUtcMillis - startEarlyTimeMs
            if (startTimeMs < System.currentTimeMillis()) {
                startTimeMs = System.currentTimeMillis()
                startEarlyTimeMs = program.startTimeUtcMillis - startTimeMs
            }
            val endTimeMs = program.endTimeUtcMillis + endLateTimeMs
            val customizedProgram: Program = ProgramImpl.Builder(program)
                .setStartTimeUtcMillis(startTimeMs)
                .setEndTimeUtcMillis(endTimeMs)
                .build()
            dvrManager.addSchedule(customizedProgram, startEarlyTimeMs, endLateTimeMs)
            val conflicts = dvrManager.getConflictingSchedules(customizedProgram)
            if (conflicts.isEmpty()) {
                DvrUiHelper.showAddScheduleToast(
                    requireContext(),
                    customizedProgram.title,
                    customizedProgram.startTimeUtcMillis,
                    customizedProgram.endTimeUtcMillis,
                )
                dismissDialog()
                finishGuidedStepSupportFragments()
            } else {
                DvrUiHelper.showScheduleConflictDialog(requireActivity(), customizedProgram)
            }
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepSupportFragments()
        }
    }

    override fun onSubGuidedActionClicked(action: GuidedAction): Boolean {
        when (action.id.toInt()) {
            SUB_ACTION_ID_START_ON_TIME -> {
                startEarlyTime = 0
                updateGuidedActions(true, timeActionOnTimeText)
            }
            SUB_ACTION_ID_START_ONE_MIN -> {
                startEarlyTime = 1
                updateGuidedActions(true, timeActionOneMinText)
            }
            SUB_ACTION_ID_START_FIVE_MIN -> {
                startEarlyTime = 5
                updateGuidedActions(true, timeActionFiveMinText)
            }
            SUB_ACTION_ID_START_FIFTEEN_MIN -> {
                startEarlyTime = 15
                updateGuidedActions(true, timeActionFifteenMinText)
            }
            SUB_ACTION_ID_START_HALF_HOUR -> {
                startEarlyTime = 30
                updateGuidedActions(true, timeActionHalfHourText)
            }
            SUB_ACTION_ID_END_ON_TIME -> {
                endLateTime = 0
                updateGuidedActions(false, timeActionOnTimeText)
            }
            SUB_ACTION_ID_END_ONE_MIN -> {
                endLateTime = 1
                updateGuidedActions(false, timeActionOneMinText)
            }
            SUB_ACTION_ID_END_FIFTEEN_MIN -> {
                endLateTime = 15
                updateGuidedActions(false, timeActionFifteenMinText)
            }
            SUB_ACTION_ID_END_HALF_HOUR -> {
                endLateTime = 30
                updateGuidedActions(false, timeActionHalfHourText)
            }
            SUB_ACTION_ID_END_ONE_HOUR -> {
                endLateTime = 60
                updateGuidedActions(false, timeActionOneHourText)
            }
            SUB_ACTION_ID_END_TWO_HOURS -> {
                endLateTime = 120
                updateGuidedActions(false, timeActionTwoHoursText)
            }
            SUB_ACTION_ID_END_THREE_HOURS -> {
                endLateTime = 180
                updateGuidedActions(false, timeActionThreeHoursText)
            }
            else -> {
                startEarlyTime = 0
                endLateTime = 0
                updateGuidedActions(true, timeActionOnTimeText)
                updateGuidedActions(false, timeActionOnTimeText)
            }
        }
        return true
    }

    private fun updateGuidedActions(start: Boolean, description: CharSequence?) {
        if (start) {
            startEarlyGuidedAction?.description = description
            notifyActionChanged(findActionPositionById(ACTION_ID_START_EARLY))
        } else {
            endLateGuidedAction?.description = description
            notifyActionChanged(findActionPositionById(ACTION_ID_END_LATE))
        }
    }

    override fun onCreateButtonActionsStylist(): GuidedActionsStylist = DvrGuidedActionsStylist(true)

    private fun buildTimeSubAction(id: Int, title: String?): GuidedAction =
        GuidedAction.Builder(activity).id(id.toLong()).title(title).build()

    private fun buildChannelSubActionStart(): List<GuidedAction> = listOf(
        buildTimeSubAction(SUB_ACTION_ID_START_ON_TIME, timeActionOnTimeText),
        buildTimeSubAction(SUB_ACTION_ID_START_ONE_MIN, timeActionOneMinText),
        buildTimeSubAction(SUB_ACTION_ID_START_FIVE_MIN, timeActionFiveMinText),
        buildTimeSubAction(SUB_ACTION_ID_START_FIFTEEN_MIN, timeActionFifteenMinText),
        buildTimeSubAction(SUB_ACTION_ID_START_HALF_HOUR, timeActionHalfHourText),
    )

    private fun buildChannelSubActionEnd(): List<GuidedAction> = listOf(
        buildTimeSubAction(SUB_ACTION_ID_END_ON_TIME, timeActionOnTimeText),
        buildTimeSubAction(SUB_ACTION_ID_END_ONE_MIN, timeActionOneMinText),
        buildTimeSubAction(SUB_ACTION_ID_END_FIFTEEN_MIN, timeActionFifteenMinText),
        buildTimeSubAction(SUB_ACTION_ID_END_HALF_HOUR, timeActionHalfHourText),
        buildTimeSubAction(SUB_ACTION_ID_END_ONE_HOUR, timeActionOneHourText),
        buildTimeSubAction(SUB_ACTION_ID_END_TWO_HOURS, timeActionTwoHoursText),
        buildTimeSubAction(SUB_ACTION_ID_END_THREE_HOURS, timeActionThreeHoursText),
    )

    private fun dismissDialog() {
        val activity = activity
        if (activity is MainActivity) {
            val currentDialog = activity.overlayManager.currentDialog
            if (currentDialog is DvrHalfSizedDialogFragment) {
                currentDialog.dismiss()
            }
        } else if (parentFragment is DialogFragment) {
            (parentFragment as DialogFragment).dismiss()
        }
    }

    companion object {
        private const val TAG = "RecordingSettingsFragment"

        private const val ACTION_ID_START_EARLY = 100L
        private const val ACTION_ID_END_LATE = 101L

        private const val SUB_ACTION_ID_START_ON_TIME = 1
        private const val SUB_ACTION_ID_START_ONE_MIN = 2
        private const val SUB_ACTION_ID_START_FIVE_MIN = 3
        private const val SUB_ACTION_ID_START_FIFTEEN_MIN = 4
        private const val SUB_ACTION_ID_START_HALF_HOUR = 5

        private const val SUB_ACTION_ID_END_ON_TIME = 6
        private const val SUB_ACTION_ID_END_ONE_MIN = 7
        private const val SUB_ACTION_ID_END_FIFTEEN_MIN = 8
        private const val SUB_ACTION_ID_END_HALF_HOUR = 9
        private const val SUB_ACTION_ID_END_ONE_HOUR = 10
        private const val SUB_ACTION_ID_END_TWO_HOURS = 11
        private const val SUB_ACTION_ID_END_THREE_HOURS = 12
    }
}
