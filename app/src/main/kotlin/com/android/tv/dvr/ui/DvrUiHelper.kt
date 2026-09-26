package com.android.tv.dvr.ui

import android.app.Activity
import android.app.ProgressDialog
import android.content.Context
import android.content.Intent
import android.media.tv.TvInputManager
import android.os.Bundle
import android.text.Html
import android.text.Spannable
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.style.TextAppearanceSpan
import android.widget.ImageView
import android.widget.Toast
import androidx.annotation.MainThread
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.CommonUtils
import com.android.tv.data.api.BaseProgram
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.dialog.HalfSizedDialogFragment
import com.android.tv.dvr.RecordingStorageStatusManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.EpisodicProgramLoadTask
import com.android.tv.dvr.ui.browse.DvrBrowseActivity
import com.android.tv.dvr.ui.list.DvrHistoryActivity
import com.android.tv.dvr.ui.list.DvrSchedulesActivity
import com.android.tv.dvr.ui.list.DvrSchedulesFragment
import com.android.tv.dvr.ui.list.DvrSeriesSchedulesFragment
import com.android.tv.dvr.ui.playback.DvrPlaybackActivity
import com.android.tv.ui.DetailsActivity
import com.android.tv.util.ToastUtils
import com.android.tv.util.Utils

/** Hilfen für DVR-Dialoge, Aufnahme-Anfragen und das Öffnen der DVR-Activities. */
@MainThread
object DvrUiHelper {
    private const val TAG = "DvrUiHelper"
    @Suppress("DEPRECATION")
    private var progressDialog: ProgressDialog? = null

    /** Beim eingebauten Tuner zuerst den Speicher prüfen, dann aufnehmen. */
    @JvmStatic
    fun checkStorageStatusAndShowErrorMessage(activity: Activity, inputId: String, recordingRequestRunnable: Runnable) {
        if (CommonUtils.isBundledInput(inputId)) {
            when (TvSingletons.getSingletons(activity).getRecordingStorageStatusManager().getDvrStorageStatus()) {
                RecordingStorageStatusManager.STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL -> return showDvrSmallSizedStorageErrorDialog(activity)
                RecordingStorageStatusManager.STORAGE_STATUS_MISSING -> return showDvrMissingStorageErrorDialog(activity)
                RecordingStorageStatusManager.STORAGE_STATUS_FREE_SPACE_INSUFFICIENT ->
                    return showDvrNoFreeSpaceErrorDialog(activity, recordingRequestRunnable)
            }
        }
        recordingRequestRunnable.run()
    }

    @JvmStatic
    fun showScheduleDialog(activity: Activity, program: Program?, addCurrentProgramToSeries: Boolean) {
        if (SoftPreconditions.checkNotNull(program) == null) return
        val args = Bundle().apply {
            putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program!!.toParcelable())
            putBoolean(DvrScheduleFragment.KEY_ADD_CURRENT_PROGRAM_TO_SERIES, addCurrentProgramToSeries)
        }
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrScheduleDialogFragment(), args, true, true)
    }

    @JvmStatic
    fun showChannelRecordDurationOptions(activity: Activity, channel: Channel?) {
        if (SoftPreconditions.checkNotNull(channel) == null) return
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrChannelRecordDurationOptionDialogFragment(),
            Bundle().apply { putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel!!.id) })
    }

    @JvmStatic
    fun showScheduleConflictDialog(activity: Activity, program: Program?) {
        program ?: return
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrProgramConflictDialogFragment(),
            Bundle().apply { putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program.toParcelable()) }, false, true)
    }

    @JvmStatic
    fun showChannelWatchConflictDialog(activity: MainActivity, channel: Channel?) {
        channel ?: return
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrChannelWatchConflictDialogFragment(),
            Bundle().apply { putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channel.id) })
    }

    @JvmStatic
    fun showDvrInsufficientSpaceErrorDialog(activity: MainActivity, failedScheduledRecordingInfoSet: Set<String>) {
        val args = Bundle().apply {
            putStringArrayList(DvrInsufficientSpaceErrorFragment.FAILED_SCHEDULED_RECORDING_INFOS, ArrayList(failedScheduledRecordingInfoSet))
        }
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrInsufficientSpaceErrorDialogFragment(), args)
        Utils.clearRecordingFailedReason(activity, TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE)
        Utils.clearFailedScheduledRecordingInfoSet(activity)
    }

    @JvmStatic
    fun showDvrNoFreeSpaceErrorDialog(activity: Activity, recordingRequestRunnable: Runnable) {
        val fragment = DvrHalfSizedDialogFragment.DvrNoFreeSpaceErrorDialogFragment()
        fragment.setOnActionClickListener(HalfSizedDialogFragment.OnActionClickListener { actionId ->
            when (actionId) {
                DvrGuidedStepFragment.ACTION_RECORD_ANYWAY.toLong() -> recordingRequestRunnable.run()
                DvrGuidedStepFragment.ACTION_DELETE_RECORDINGS.toLong() -> activity.startActivity(Intent(activity, DvrBrowseActivity::class.java))
            }
        })
        showDialogFragment(activity, fragment, null)
    }

    private fun showDvrMissingStorageErrorDialog(activity: Activity) =
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrMissingStorageErrorDialogFragment(), null)

    @JvmStatic
    fun showDvrSmallSizedStorageErrorDialog(activity: Activity) =
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrSmallSizedStorageErrorDialogFragment(), null)

    @JvmStatic
    fun showStopRecordingDialog(activity: Activity, channelId: Long, reason: Int, listener: HalfSizedDialogFragment.OnActionClickListener?) {
        val fragment = DvrHalfSizedDialogFragment.DvrStopRecordingDialogFragment()
        fragment.setOnActionClickListener(listener)
        showDialogFragment(activity, fragment, Bundle().apply {
            putLong(DvrHalfSizedDialogFragment.KEY_CHANNEL_ID, channelId)
            putInt(DvrStopRecordingFragment.KEY_REASON, reason)
        })
    }

    @JvmStatic
    fun showAlreadyScheduleDialog(activity: Activity, program: Program?) {
        program ?: return
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrAlreadyScheduledDialogFragment(),
            Bundle().apply { putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program.toParcelable()) }, false, true)
    }

    @JvmStatic
    fun showAlreadyRecordedDialog(activity: Activity, program: Program?) {
        program ?: return
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrAlreadyRecordedDialogFragment(),
            Bundle().apply { putParcelable(DvrHalfSizedDialogFragment.KEY_PROGRAM, program.toParcelable()) }, false, true)
    }

    @JvmStatic
    fun showWriteStoragePermissionRationaleDialog(activity: Activity) =
        showDialogFragment(activity, DvrHalfSizedDialogFragment.DvrWriteStoragePermissionRationaleDialogFragment(), Bundle(), false, false)

    /** Laufende Sendung aufnehmen (ohne Sendung: Dauer auswählen). */
    @JvmStatic
    fun requestRecordingCurrentProgram(activity: Activity, channel: Channel?, program: Program?, addProgramToSeries: Boolean) {
        if (program == null) {
            showChannelRecordDurationOptions(activity, channel)
        } else if (handleCreateSchedule(activity, program, addProgramToSeries)) {
            Toast.makeText(activity, activity.getString(R.string.dvr_msg_current_program_scheduled, program.title,
                Utils.toTimeString(program.endTimeUtcMillis, false)), Toast.LENGTH_SHORT).show()
        }
    }

    @JvmStatic
    fun requestRecordingFutureProgram(activity: Activity, program: Program, addProgramToSeries: Boolean) {
        if (handleCreateSchedule(activity, program, addProgramToSeries)) {
            ToastUtils.show(activity, activity.getString(R.string.dvr_msg_program_scheduled, program.title), Toast.LENGTH_SHORT)
        }
    }

    /**
     * Einzelsendung: planen (bei Konflikt Dialog). Folge: schon aufgenommen/geplant → Hinweis;
     * ohne aktive Serie → Auswahl Folge/Serie; sonst Folge planen.
     */
    private fun handleCreateSchedule(activity: Activity, program: Program?, addProgramToSeries: Boolean): Boolean {
        program ?: return false
        val dvrManager = TvSingletons.getSingletons(activity).getDvrManager() ?: return false
        if (!program.isEpisodic) {
            dvrManager.addSchedule(program)
            if (dvrManager.getConflictingSchedules(program).isNotEmpty()) {
                showScheduleConflictDialog(activity, program)
                return false
            }
        } else {
            if (dvrManager.getRecordedProgram(program.title, program.seasonNumber, program.episodeNumber) != null) {
                showAlreadyRecordedDialog(activity, program)
                return false
            }
            val duplicate = dvrManager.getScheduledRecording(program.title, program.seasonNumber, program.episodeNumber)
            if (duplicate != null && (duplicate.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED ||
                    duplicate.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS)
            ) {
                showAlreadyScheduleDialog(activity, program)
                return false
            }
            val series = dvrManager.getSeriesRecording(program)
            if (series == null || series.isStopped) {
                showScheduleDialog(activity, program, addProgramToSeries)
                return false
            }
            dvrManager.addSchedule(program)
        }
        return true
    }

    private fun showDialogFragment(activity: Activity, dialogFragment: DvrHalfSizedDialogFragment, args: Bundle?,
        keepSidePanelHistory: Boolean = false, keepProgramGuide: Boolean = false) {
        dialogFragment.arguments = args
        if (activity is MainActivity) {
            activity.overlayManager.showDialogFragment(DvrHalfSizedDialogFragment.DIALOG_TAG, dialogFragment, keepSidePanelHistory, keepProgramGuide)
        } else {
            dialogFragment.show((activity as FragmentActivity).supportFragmentManager, DvrHalfSizedDialogFragment.DIALOG_TAG)
        }
    }

    @JvmStatic
    fun isChannelWatchConflictDialogShown(activity: MainActivity): Boolean =
        activity.overlayManager.currentDialog is DvrHalfSizedDialogFragment.DvrChannelWatchConflictDialogFragment

    private fun getEarliestScheduledRecording(recordings: List<ScheduledRecording>): ScheduledRecording? =
        recordings.sortedWith(ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR).firstOrNull()

    @JvmStatic
    fun startPlaybackActivity(context: Context, programId: Long, seekTimeMs: Long, pinChecked: Boolean) {
        val intent = Intent(context, DvrPlaybackActivity::class.java).putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, programId)
        if (seekTimeMs != TvInputManager.TIME_SHIFT_INVALID_TIME) intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_SEEK_TIME, seekTimeMs)
        intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_PIN_CHECKED, pinChecked)
        context.startActivity(intent)
    }

    @JvmStatic
    fun startSchedulesActivityForTuneConflict(context: Context, channel: Channel?) {
        channel ?: return
        val conflicts = TvSingletons.getSingletons(context).getDvrManager()?.getConflictingSchedulesForTune(channel.id).orEmpty()
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts))
    }

    @JvmStatic
    fun startSchedulesActivityForOneTimeRecordingConflict(context: Context, conflicts: List<ScheduledRecording>) =
        startSchedulesActivity(context, getEarliestScheduledRecording(conflicts))

    @JvmStatic
    fun startDvrHistoryActivity(context: Context) = context.startActivity(Intent(context, DvrHistoryActivity::class.java))

    @JvmStatic
    fun startSchedulesActivity(context: Context, focusedScheduledRecording: ScheduledRecording?) {
        val intent = Intent(context, DvrSchedulesActivity::class.java)
            .putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE, DvrSchedulesActivity.TYPE_FULL_SCHEDULE)
        focusedScheduledRecording?.let { intent.putExtra(DvrSchedulesFragment.SCHEDULES_KEY_SCHEDULED_RECORDING, it) }
        context.startActivity(intent)
    }

    @JvmStatic
    fun startSchedulesActivityForSeries(context: Context, seriesRecording: SeriesRecording) {
        context.startActivity(Intent(context, DvrSchedulesActivity::class.java)
            .putExtra(DvrSchedulesActivity.KEY_SCHEDULES_TYPE, DvrSchedulesActivity.TYPE_SERIES_SCHEDULE)
            .putExtra(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING, seriesRecording))
    }

    /** Serien-Einstellungen öffnen; ohne Sendungsliste diese erst laden (mit Fortschrittsdialog). */
    @JvmStatic
    @Suppress("DEPRECATION")
    fun startSeriesSettingsActivity(context: Context, seriesRecordingId: Long, programs: List<Program>?,
        removeEmptySeriesSchedule: Boolean, isWindowTranslucent: Boolean, showViewScheduleOptionInDialog: Boolean, currentProgram: Program?) {
        val series = TvSingletons.getSingletons(context).getDvrDataManager().getSeriesRecording(seriesRecordingId) ?: return
        if (programs != null) {
            startSeriesSettingsActivityInternal(context, seriesRecordingId, programs, removeEmptySeriesSchedule, isWindowTranslucent,
                showViewScheduleOptionInDialog, currentProgram)
            return
        }
        val task = object : EpisodicProgramLoadTask(context, series) {
            override fun onPostExecute(programs: List<Program>) {
                progressDialog?.dismiss()
                progressDialog = null
                startSeriesSettingsActivityInternal(context, seriesRecordingId, programs, removeEmptySeriesSchedule,
                    isWindowTranslucent, showViewScheduleOptionInDialog, currentProgram)
            }
        }.setLoadCurrentProgram(true).setLoadDisallowedProgram(true).setLoadScheduledEpisode(true).setIgnoreChannelOption(true)
        progressDialog = ProgressDialog.show(context, null, context.getString(R.string.dvr_series_progress_message_reading_programs), true, true) {
            task.cancel(true)
            progressDialog = null
        }
        task.execute()
    }

    @JvmStatic
    fun startRecordingSettingsActivity(context: Context, program: Program?) {
        program ?: return
        context.startActivity(Intent(context, DvrRecordingSettingsActivity::class.java)
            .putExtra(DvrRecordingSettingsActivity.IS_WINDOW_TRANSLUCENT, true)
            .putExtra(DvrRecordingSettingsActivity.PROGRAM, program.toParcelable()))
    }

    private fun startSeriesSettingsActivityInternal(context: Context, seriesRecordingId: Long, programs: List<Program>,
        removeEmptySeriesSchedule: Boolean, isWindowTranslucent: Boolean, showViewScheduleOptionInDialog: Boolean, currentProgram: Program?) {
        val intent = Intent(context, DvrSeriesSettingsActivity::class.java).putExtra(DvrSeriesSettingsActivity.SERIES_RECORDING_ID, seriesRecordingId)
        BigArguments.reset()
        BigArguments.setArgument(DvrSeriesSettingsActivity.PROGRAM_LIST, programs)
        intent.putExtra(DvrSeriesSettingsActivity.REMOVE_EMPTY_SERIES_RECORDING, removeEmptySeriesSchedule)
            .putExtra(DvrSeriesSettingsActivity.IS_WINDOW_TRANSLUCENT, isWindowTranslucent)
            .putExtra(DvrSeriesSettingsActivity.SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG, showViewScheduleOptionInDialog)
        currentProgram?.let { intent.putExtra(DvrSeriesSettingsActivity.CURRENT_PROGRAM, it.toParcelable()) }
        context.startActivity(intent)
    }

    @JvmStatic
    fun startSeriesScheduledDialogActivity(context: Context, seriesRecording: SeriesRecording?, showViewScheduleOptionInDialog: Boolean,
        programs: List<Program>) {
        seriesRecording ?: return
        val intent = Intent(context, DvrSeriesScheduledDialogActivity::class.java)
            .putExtra(DvrSeriesScheduledDialogActivity.SERIES_RECORDING_ID, seriesRecording.id)
            .putExtra(DvrSeriesScheduledDialogActivity.SHOW_VIEW_SCHEDULE_OPTION, showViewScheduleOptionInDialog)
        BigArguments.reset()
        BigArguments.setArgument(DvrSeriesScheduledFragment.SERIES_SCHEDULED_KEY_PROGRAMS, programs)
        context.startActivity(intent)
    }

    /** Detailansicht passend zum Objekt (geplant, laufend, aufgenommen, fehlgeschlagen, Serie). */
    @JvmStatic
    fun startDetailsActivity(activity: Activity, dvrItem: Any?, imageView: ImageView?, hideViewScheduleIn: Boolean) {
        dvrItem ?: return
        var hideViewSchedule = hideViewScheduleIn
        var recordingId: Long
        val viewType: Int
        when (dvrItem) {
            is ScheduledRecording -> {
                recordingId = dvrItem.id
                viewType = when {
                    dvrItem.state == ScheduledRecording.STATE_RECORDING_NOT_STARTED -> DetailsActivity.SCHEDULED_RECORDING_VIEW
                    dvrItem.state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS -> DetailsActivity.CURRENT_RECORDING_VIEW
                    dvrItem.state == ScheduledRecording.STATE_RECORDING_FINISHED && dvrItem.recordedProgramId != null -> {
                        recordingId = dvrItem.recordedProgramId
                        DetailsActivity.RECORDED_PROGRAM_VIEW
                    }
                    dvrItem.state == ScheduledRecording.STATE_RECORDING_FAILED -> {
                        hideViewSchedule = true
                        DetailsActivity.SCHEDULED_RECORDING_VIEW
                    }
                    else -> return
                }
            }
            is RecordedProgram -> {
                recordingId = dvrItem.id
                viewType = DetailsActivity.RECORDED_PROGRAM_VIEW
            }
            is SeriesRecording -> {
                recordingId = dvrItem.id
                viewType = DetailsActivity.SERIES_RECORDING_VIEW
            }
            else -> return
        }
        val intent = Intent(activity, DetailsActivity::class.java)
            .putExtra(DetailsActivity.RECORDING_ID, recordingId)
            .putExtra(DetailsActivity.DETAILS_VIEW_TYPE, viewType)
            .putExtra(DetailsActivity.HIDE_VIEW_SCHEDULE, hideViewSchedule)
        val bundle = imageView?.let { ActivityOptionsCompat.makeSceneTransitionAnimation(activity, it, DetailsActivity.SHARED_ELEMENT_NAME).toBundle() }
        activity.startActivity(intent, bundle)
    }

    @JvmStatic
    fun showCancelAllSeriesRecordingDialog(activity: DvrSchedulesActivity, seriesRecording: SeriesRecording) {
        val fragment = DvrHalfSizedDialogFragment.DvrStopSeriesRecordingDialogFragment()
        fragment.arguments = Bundle().apply { putParcelable(DvrStopSeriesRecordingFragment.KEY_SERIES_RECORDING, seriesRecording) }
        fragment.show(activity.supportFragmentManager, DvrHalfSizedDialogFragment.DvrStopSeriesRecordingDialogFragment.DIALOG_TAG)
    }

    @JvmStatic
    fun startSeriesDeletionActivity(context: Context, seriesRecordingId: Long) =
        context.startActivity(Intent(context, DvrSeriesDeletionActivity::class.java).putExtra(DvrSeriesDeletionActivity.SERIES_RECORDING_ID, seriesRecordingId))

    @JvmStatic
    fun showAddScheduleToast(context: Context, title: String?, startTimeMs: Long, endTimeMs: Long) {
        val msg = if (startTimeMs > System.currentTimeMillis()) context.getString(R.string.dvr_msg_program_scheduled, title)
        else context.getString(R.string.dvr_msg_current_program_scheduled, title, Utils.toTimeString(endTimeMs, false))
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun getStyledTitleWithEpisodeNumber(context: Context, schedule: ScheduledRecording, styleResId: Int): CharSequence =
        getStyledTitleWithEpisodeNumber(context, schedule.programTitle, schedule.seasonNumber, schedule.episodeNumber, styleResId)

    @JvmStatic
    fun getStyledTitleWithEpisodeNumber(context: Context, program: BaseProgram, styleResId: Int): CharSequence =
        getStyledTitleWithEpisodeNumber(context, program.title, program.seasonNumber, program.episodeNumber, styleResId)

    /** "Titel · S1 F2" mit eigenem Stil für die Folgennummer (Span aus dem HTML-String). */
    @JvmStatic
    fun getStyledTitleWithEpisodeNumber(context: Context, title: String?, seasonNumber: String?, episodeNumber: String?, styleResId: Int): CharSequence {
        if (title.isNullOrEmpty()) return ""
        val builder = if (seasonNumber.isNullOrEmpty() || seasonNumber == "0") {
            SpannableStringBuilder.valueOf(
                if (episodeNumber.isNullOrEmpty()) SpannableStringBuilder.valueOf(title)
                else Html.fromHtml(context.getString(R.string.program_title_with_episode_number_no_season, title, episodeNumber), Html.FROM_HTML_MODE_LEGACY))
        } else {
            SpannableStringBuilder.valueOf(Html.fromHtml(context.getString(R.string.program_title_with_episode_number, title, seasonNumber, episodeNumber),
                Html.FROM_HTML_MODE_LEGACY))
        }
        val spans = builder.getSpans(0, builder.length, Any::class.java)
        if (spans.isNotEmpty()) {
            if (styleResId != 0) {
                builder.setSpan(TextAppearanceSpan(context, styleResId), builder.getSpanStart(spans[0]), builder.getSpanEnd(spans[0]),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            builder.removeSpan(spans[0])
        }
        return SpannableString(builder)
    }
}
