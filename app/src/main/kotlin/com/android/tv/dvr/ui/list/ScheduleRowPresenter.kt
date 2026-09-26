package com.android.tv.dvr.ui.list

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.Activity
import android.content.Context
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.IntDef
import androidx.leanback.widget.RowPresenter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.dialog.HalfSizedDialogFragment
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrStopRecordingFragment
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.util.ToastUtils
import com.android.tv.util.Utils
import kotlin.math.max
import kotlin.math.roundToInt

/** RowPresenter für [ScheduleRow]: Zeit, Titel, Kanal, Zusatzinfo und bis zu zwei Aktions-Buttons. */
open class ScheduleRowPresenter(
    /** Kontext. */
    protected val context: Context,
) : RowPresenter() {

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(ACTION_START_RECORDING, ACTION_STOP_RECORDING, ACTION_CREATE_SCHEDULE, ACTION_REMOVE_SCHEDULE)
    annotation class ScheduleRowAction

    /** DVR-Manager. Abweichung: TvSingletons liefert ihn nullable; die DVR-Listen gibt es nur mit DVR. */
    protected val dvrManager: DvrManager = checkNotNull(TvSingletons.getSingletons(context).getDvrManager())
    private val dvrScheduleManager: DvrScheduleManager = checkNotNull(TvSingletons.getSingletons(context).getDvrScheduleManager())
    private val tunerConflictWillNotBeRecordedInfo =
        context.getString(R.string.dvr_schedules_tuner_conflict_will_not_be_recorded_info)
    private val tunerConflictWillBePartiallyRecordedInfo =
        context.getString(R.string.dvr_schedules_tuner_conflict_will_be_partially_recorded)
    private val animationDuration = context.resources.getInteger(android.R.integer.config_shortAnimTime)

    private var lastFocusedViewId = 0

    init {
        setHeaderPresenter(null)
        setSelectEffectEnabled(false)
    }

    /** ViewHolder für [ScheduleRow]. */
    open class ScheduleRowViewHolder(view: View, private val presenter: ScheduleRowPresenter) : RowPresenter.ViewHolder(view) {
        internal var actions: IntArray? = null
        private val ltr = view.context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
        internal val infoContainer: LinearLayout = view.findViewById(R.id.info_container)
        // Die erste Aktion liegt rechts von der zweiten.
        internal val secondActionContainer: RelativeLayout = view.findViewById(R.id.action_second_container)
        internal val firstActionContainer: RelativeLayout = view.findViewById(R.id.action_first_container)
        private val selectorView: View = view.findViewById(R.id.selector)
        /** Zeit-View. */
        val timeView: TextView = view.findViewById(R.id.time)
        /** Titel-View. */
        val programTitleView: TextView = view.findViewById(R.id.program_title)
        internal val infoSeparatorView: TextView = view.findViewById(R.id.info_separator)
        internal val channelNameView: TextView = view.findViewById(R.id.channel_name)
        internal val extraInfoIcon: ImageView = view.findViewById(R.id.extra_info_icon)
        internal val extraInfoView: TextView = view.findViewById(R.id.extra_info)
        internal val secondActionView: ImageView = view.findViewById(R.id.action_second)
        internal val firstActionView: ImageView = view.findViewById(R.id.action_first)

        internal var pendingAnimationRunnable: Runnable? = null

        private val selectorTranslationDelta: Int
        private val selectorWidthDelta: Int
        private val infoContainerTargetWidthWithNoAction: Int
        private val infoContainerTargetWidthWithOneAction: Int
        private val infoContainerTargetWidthWithTwoAction: Int
        private val roundRectRadius: Int

        private val onFocusChangeListener = View.OnFocusChangeListener { v, _ ->
            v.post {
                if (v.isFocused) presenter.lastFocusedViewId = v.id
                updateSelector()
            }
        }

        init {
            val res = view.resources
            selectorTranslationDelta = res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin) -
                res.getDimensionPixelSize(R.dimen.dvr_schedules_item_focus_translation_delta)
            selectorWidthDelta = res.getDimensionPixelSize(R.dimen.dvr_schedules_item_focus_width_delta)
            roundRectRadius = res.getDimensionPixelSize(R.dimen.dvr_schedules_selector_radius)
            val fullWidth = res.getDimensionPixelSize(R.dimen.dvr_schedules_item_width) -
                2 * res.getDimensionPixelSize(R.dimen.dvr_schedules_layout_padding)
            infoContainerTargetWidthWithNoAction = fullWidth + 2 * roundRectRadius
            infoContainerTargetWidthWithOneAction = fullWidth -
                res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin) -
                res.getDimensionPixelSize(R.dimen.dvr_schedules_item_delete_width) +
                roundRectRadius + selectorWidthDelta
            infoContainerTargetWidthWithTwoAction = infoContainerTargetWidthWithOneAction -
                res.getDimensionPixelSize(R.dimen.dvr_schedules_item_section_margin) -
                res.getDimensionPixelSize(R.dimen.dvr_schedules_item_icon_size)

            infoContainer.onFocusChangeListener = onFocusChangeListener
            firstActionContainer.onFocusChangeListener = onFocusChangeListener
            secondActionContainer.onFocusChangeListener = onFocusChangeListener
        }

        private fun updateSelector() {
            val animationDuration = selectorView.resources.getInteger(android.R.integer.config_shortAnimTime)
            val interpolator = DecelerateInterpolator()
            if (infoContainer.isFocused || secondActionContainer.isFocused || firstActionContainer.isFocused) {
                val lp = selectorView.layoutParams
                val targetWidth = if (infoContainer.isFocused) {
                    // Sichtbarkeit über actions prüfen statt getVisibility(), da evtl. gerade ausgeblendet wird.
                    val a = actions
                    when {
                        a == null || a.isEmpty() -> infoContainerTargetWidthWithNoAction
                        a.size == 1 -> infoContainerTargetWidthWithOneAction
                        else -> infoContainerTargetWidthWithTwoAction
                    }
                } else if (secondActionContainer.isFocused) {
                    max(secondActionContainer.width, 2 * roundRectRadius)
                } else {
                    firstActionContainer.width + roundRectRadius + selectorTranslationDelta
                }

                val targetTranslationX: Float = if (infoContainer.isFocused) {
                    if (ltr) (infoContainer.left - roundRectRadius - selectorView.left).toFloat()
                    else (infoContainer.right + roundRectRadius - selectorView.right).toFloat()
                } else if (secondActionContainer.isFocused) {
                    if (secondActionContainer.width > 2 * roundRectRadius) {
                        if (ltr) (secondActionContainer.left - selectorView.left).toFloat()
                        else (secondActionContainer.right - selectorView.right).toFloat()
                    } else {
                        if (ltr) {
                            (secondActionContainer.left - (roundRectRadius - secondActionContainer.width / 2) - selectorView.left).toFloat()
                        } else {
                            (secondActionContainer.right + (roundRectRadius - secondActionContainer.width / 2) - selectorView.right).toFloat()
                        }
                    }
                } else {
                    if (ltr) (firstActionContainer.left - selectorTranslationDelta - selectorView.left).toFloat()
                    else (firstActionContainer.right + selectorTranslationDelta - selectorView.right).toFloat()
                }

                if (selectorView.alpha == 0f) {
                    selectorView.translationX = targetTranslationX
                    lp.width = targetWidth
                    selectorView.requestLayout()
                }

                // Selektor einblenden und auf Zielbreite/-position animieren.
                val deltaWidth = (lp.width - targetWidth).toFloat()
                selectorView.animate().cancel()
                selectorView.animate()
                    .translationX(targetTranslationX)
                    .alpha(1f)
                    .setUpdateListener { animation ->
                        // Breite für diesen Animationsschritt.
                        val fraction = 1f - animation.animatedFraction
                        lp.width = targetWidth + (deltaWidth * fraction).roundToInt()
                        selectorView.requestLayout()
                    }
                    .setDuration(animationDuration.toLong())
                    .setInterpolator(interpolator)
                    .start()
                pendingAnimationRunnable?.let {
                    it.run()
                    pendingAnimationRunnable = null
                }
            } else {
                selectorView.animate().cancel()
                selectorView.animate()
                    .alpha(0f)
                    .setDuration(animationDuration.toLong())
                    .setInterpolator(interpolator)
                    .setUpdateListener(null)
                    .start()
            }
        }

        /** Infobereich ausgrauen. */
        fun greyOutInfo() {
            val grey = infoContainer.resources.getColor(R.color.dvr_schedules_item_info_grey, null)
            timeView.setTextColor(grey)
            programTitleView.setTextColor(grey)
            infoSeparatorView.setTextColor(grey)
            channelNameView.setTextColor(grey)
            extraInfoView.setTextColor(grey)
        }

        /** Ausgrauen rückgängig machen. */
        fun whiteBackInfo() {
            val res = infoContainer.resources
            timeView.setTextColor(res.getColor(R.color.dvr_schedules_item_info, null))
            programTitleView.setTextColor(res.getColor(R.color.dvr_schedules_item_main, null))
            infoSeparatorView.setTextColor(res.getColor(R.color.dvr_schedules_item_info, null))
            channelNameView.setTextColor(res.getColor(R.color.dvr_schedules_item_info, null))
            extraInfoView.setTextColor(res.getColor(R.color.dvr_schedules_item_info, null))
        }
    }

    public override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder =
        onGetScheduleRowViewHolder(LayoutInflater.from(context).inflate(R.layout.dvr_schedules_item, parent, false))

    override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(vh, item)
        val viewHolder = vh as ScheduleRowViewHolder
        val row = item as ScheduleRow
        val actions = getAvailableActions(row)
        viewHolder.actions = actions
        viewHolder.infoContainer.setOnClickListener {
            if (isInfoClickable(row)) onInfoClicked(row)
        }
        // Abweichung: null-sicherer Zugriff auf actions (ohne Aktionen sind die Buttons ausgeblendet).
        viewHolder.firstActionContainer.setOnClickListener {
            actions?.getOrNull(0)?.let { onActionClicked(it, row) }
        }
        viewHolder.secondActionContainer.setOnClickListener {
            actions?.getOrNull(1)?.let { onActionClicked(it, row) }
        }

        viewHolder.timeView.text = onGetRecordingTimeText(row)
        var programInfoText = onGetProgramInfoText(row)
        if (programInfoText.isNullOrEmpty()) {
            val durationMins = max(1, Utils.getRoundOffMinsFromMs(row.duration))
            programInfoText = context.resources.getQuantityString(R.plurals.dvr_schedules_recording_duration,
                durationMins, durationMins)
        }
        val channelName = getChannelNameText(row)
        viewHolder.programTitleView.text = programInfoText
        viewHolder.infoSeparatorView.visibility =
            if (!programInfoText.isNullOrEmpty() && !channelName.isNullOrEmpty()) View.VISIBLE else View.GONE
        viewHolder.channelNameView.text = channelName
        if (actions != null) {
            if (actions.size == 2) viewHolder.secondActionView.setImageResource(getImageForAction(actions[1]))
            if (actions.size == 2 || actions.size == 1) viewHolder.firstActionView.setImageResource(getImageForAction(actions[0]))
        }
        val schedule = row.schedule
        viewHolder.extraInfoIcon.visibility = View.GONE
        if (dvrManager.isConflicting(schedule) || isFailedRecording(schedule)) {
            val extraInfo = if (isFailedRecording(schedule)) {
                viewHolder.extraInfoIcon.visibility = View.VISIBLE
                context.getString(R.string.dvr_recording_failed_short) + " " + getErrorMessage(schedule!!)
            } else if (dvrScheduleManager.isPartiallyConflicting(schedule!!)) {
                tunerConflictWillBePartiallyRecordedInfo
            } else {
                tunerConflictWillNotBeRecordedInfo
            }
            viewHolder.extraInfoView.text = extraInfo
            viewHolder.extraInfoView.visibility = View.VISIBLE
        } else {
            viewHolder.extraInfoView.visibility = View.GONE
        }
        if (shouldBeGrayedOut(row)) viewHolder.greyOutInfo() else viewHolder.whiteBackInfo()
        if (isFailedRecording(schedule)) {
            viewHolder.extraInfoView.setTextColor(
                viewHolder.infoContainer.resources.getColor(R.color.dvr_recording_failed_text_color, null))
        }
        viewHolder.infoContainer.isFocusable = isInfoClickable(row)
        updateActionContainer(viewHolder, viewHolder.isSelected)
    }

    private fun isFailedRecording(scheduledRecording: ScheduledRecording?): Boolean =
        scheduledRecording != null && scheduledRecording.state == ScheduledRecording.STATE_RECORDING_FAILED

    private fun getErrorMessage(recording: ScheduledRecording): String {
        val reason = recording.failedReason ?: ScheduledRecording.FAILED_REASON_OTHER
        return when (reason) {
            ScheduledRecording.FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED ->
                context.getString(R.string.dvr_recording_failed_not_started_short)
            ScheduledRecording.FAILED_REASON_RESOURCE_BUSY -> context.getString(R.string.dvr_recording_failed_resource_busy_short)
            ScheduledRecording.FAILED_REASON_INPUT_UNAVAILABLE ->
                context.getString(R.string.dvr_recording_failed_input_unavailable_short, recording.inputId)
            ScheduledRecording.FAILED_REASON_INPUT_DVR_UNSUPPORTED ->
                context.getString(R.string.dvr_recording_failed_input_dvr_unsupported_short)
            ScheduledRecording.FAILED_REASON_INSUFFICIENT_SPACE ->
                context.getString(R.string.dvr_recording_failed_insufficient_space_short)
            // FAILED_REASON_OTHER, NOT_FINISHED, SCHEDULER_STOPPED, INVALID_CHANNEL, MESSAGE_NOT_SENT,
            // CONNECTION_FAILED und alles andere:
            else -> context.getString(R.string.dvr_recording_failed_system_failure, reason)
        }
    }

    private fun getImageForAction(@ScheduleRowAction action: Int): Int = when (action) {
        ACTION_START_RECORDING -> R.drawable.ic_record_start
        ACTION_STOP_RECORDING -> R.drawable.ic_record_stop
        ACTION_CREATE_SCHEDULE -> R.drawable.ic_scheduled_recording
        ACTION_REMOVE_SCHEDULE -> R.drawable.ic_dvr_cancel
        else -> 0
    }

    /** ViewHolder für die Zeile erzeugen. */
    protected open fun onGetScheduleRowViewHolder(view: View): ScheduleRowViewHolder = ScheduleRowViewHolder(view, this)

    /** Zeittext für die Zeit-View. */
    protected open fun onGetRecordingTimeText(row: ScheduleRow): String? =
        Utils.getDurationString(context, row.startTimeMs, row.endTimeMs, true, false, true, 0)

    /** Sendungsinfo für die Titel-View. */
    protected open fun onGetProgramInfoText(row: ScheduleRow): String? = row.getProgramTitleWithEpisodeNumber(context)

    private fun getChannelNameText(row: ScheduleRow): String? {
        val channel = TvSingletons.getSingletons(context).getChannelDataManager().getChannel(row.channelId) ?: return null
        return if (channel.displayName.isNullOrEmpty()) channel.displayNumber
        else channel.displayName!!.trim() + " " + channel.displayNumber
    }

    /** Klick auf den Infobereich. */
    protected open fun onInfoClicked(row: ScheduleRow) {
        DvrUiHelper.startDetailsActivity(context as Activity, row.schedule, null, true)
    }

    private fun isInfoClickable(row: ScheduleRow): Boolean {
        val schedule = row.schedule ?: return false
        return schedule.isNotStarted || schedule.isInProgress || schedule.isFinished || schedule.isFailed
    }

    /** Klick auf einen Aktions-Button. */
    protected open fun onActionClicked(@ScheduleRowAction action: Int, row: ScheduleRow) {
        when (action) {
            ACTION_START_RECORDING -> onStartRecording(row)
            ACTION_STOP_RECORDING -> onStopRecording(row)
            ACTION_CREATE_SCHEDULE -> onCreateSchedule(row)
            ACTION_REMOVE_SCHEDULE -> onRemoveSchedule(row)
        }
    }

    /** Aktion [ACTION_START_RECORDING]. */
    protected open fun onStartRecording(row: ScheduleRow) {
        // Zeile wurde gelöscht.
        val schedule = row.schedule ?: return
        // Würden laufende Aufnahmen dadurch gestoppt, erst nachfragen.
        val conflictSchedules = dvrScheduleManager.getConflictingSchedules(
            schedule.channelId, System.currentTimeMillis(), schedule.endTimeMs)
        for (i in conflictSchedules.indices.reversed()) {
            val conflictSchedule = conflictSchedules[i]
            if (conflictSchedule.isInProgress) {
                DvrUiHelper.showStopRecordingDialog(context as Activity, conflictSchedule.channelId,
                    DvrStopRecordingFragment.REASON_ON_CONFLICT,
                    HalfSizedDialogFragment.OnActionClickListener { actionId ->
                        if (actionId == DvrStopRecordingFragment.ACTION_STOP) onStartRecordingInternal(row)
                    })
                return
            }
        }
        onStartRecordingInternal(row)
    }

    private fun onStartRecordingInternal(row: ScheduleRow) {
        if (row.isOnAir && !row.isRecordingInProgress && !row.isStartRecordingRequested) {
            // Bugfix: Zeile kann inzwischen gelöscht sein (Dialog-Callback) – das Original hätte eine NPE geworfen.
            val schedule = row.schedule ?: return
            row.isStartRecordingRequested = true
            if (row.isRecordingNotStarted) {
                dvrManager.setHighestPriority(schedule)
            } else if (row.isRecordingFinished) {
                dvrManager.addSchedule(ScheduledRecording.buildFrom(schedule)
                    .setId(ScheduledRecording.ID_NOT_SET)
                    .setState(ScheduledRecording.STATE_RECORDING_NOT_STARTED)
                    .setPriority(dvrManager.suggestHighestPriority(schedule))
                    .build())
            } else {
                SoftPreconditions.checkState(false, TAG, "Invalid row state to start recording: $row")
                return
            }
            val msg = context.getString(R.string.dvr_msg_current_program_scheduled, schedule.programTitle,
                Utils.toTimeString(row.endTimeMs, false))
            ToastUtils.show(context, msg, Toast.LENGTH_SHORT)
        }
    }

    /** Aktion [ACTION_STOP_RECORDING]. */
    protected open fun onStopRecording(row: ScheduleRow) {
        // Zeile wurde gelöscht.
        val schedule = row.schedule ?: return
        if (row.isRecordingInProgress && !row.isStopRecordingRequested) {
            row.isStopRecordingRequested = true
            dvrManager.stopRecording(schedule)
            var deletedInfo: CharSequence? = onGetProgramInfoText(row)
            if (deletedInfo.isNullOrEmpty()) deletedInfo = getChannelNameText(row)
            ToastUtils.show(context, context.resources.getString(R.string.dvr_schedules_deletion_info, deletedInfo),
                Toast.LENGTH_SHORT)
        }
    }

    /** Aktion [ACTION_CREATE_SCHEDULE]. */
    protected open fun onCreateSchedule(row: ScheduleRow) {
        // Zeile wurde gelöscht.
        val schedule = row.schedule ?: return
        if (!row.isOnAir) {
            if (row.isScheduleCanceled) {
                dvrManager.updateScheduledRecording(ScheduledRecording.buildFrom(schedule)
                    .setState(ScheduledRecording.STATE_RECORDING_NOT_STARTED)
                    .setPriority(dvrManager.suggestHighestPriority(schedule))
                    .build())
                val msg = context.getString(R.string.dvr_msg_program_scheduled, schedule.programTitle)
                ToastUtils.show(context, msg, Toast.LENGTH_SHORT)
            } else if (dvrManager.isConflicting(schedule)) {
                dvrManager.setHighestPriority(schedule)
            }
        }
    }

    /** Aktion [ACTION_REMOVE_SCHEDULE]. */
    protected open fun onRemoveSchedule(row: ScheduleRow) {
        // Zeile wurde gelöscht.
        val schedule = row.schedule ?: return
        var deletedInfo: CharSequence? = null
        if (row.isOnAir) {
            if (row.isRecordingNotStarted) {
                deletedInfo = getDeletedInfo(row)
                dvrManager.removeScheduledRecording(schedule)
            }
        } else {
            if (dvrManager.isConflicting(schedule) && !shouldKeepScheduleAfterRemoving()) {
                deletedInfo = getDeletedInfo(row)
                dvrManager.removeScheduledRecording(schedule)
            } else if (row.isRecordingNotStarted) {
                deletedInfo = getDeletedInfo(row)
                dvrManager.updateScheduledRecording(ScheduledRecording.buildFrom(schedule)
                    .setState(ScheduledRecording.STATE_RECORDING_CANCELED)
                    .build())
            } else if (row.isRecordingFailed) {
                deletedInfo = getDeletedInfo(row)
                dvrManager.removeScheduledRecording(schedule)
            }
        }
        if (deletedInfo != null) {
            ToastUtils.show(context, context.resources.getString(R.string.dvr_schedules_deletion_info, deletedInfo),
                Toast.LENGTH_SHORT)
        }
    }

    private fun getDeletedInfo(row: ScheduleRow): CharSequence? {
        val deletedInfo = onGetProgramInfoText(row)
        return if (deletedInfo.isNullOrEmpty()) getChannelNameText(row) else deletedInfo
    }

    override fun onRowViewSelected(vh: RowPresenter.ViewHolder, selected: Boolean) {
        super.onRowViewSelected(vh, selected)
        updateActionContainer(vh, selected)
    }

    /** Aktions-Buttons je nach Auswahl ein-/ausblenden und Fokus wie in der vorherigen Zeile setzen. */
    private fun updateActionContainer(vh: RowPresenter.ViewHolder, selected: Boolean) {
        val viewHolder = vh as ScheduleRowViewHolder
        viewHolder.secondActionContainer.animate().setListener(null).cancel()
        viewHolder.firstActionContainer.animate().setListener(null).cancel()
        val actions = viewHolder.actions
        if (selected && actions != null) {
            when (actions.size) {
                2 -> {
                    prepareShowActionView(viewHolder.secondActionContainer)
                    prepareShowActionView(viewHolder.firstActionContainer)
                    viewHolder.pendingAnimationRunnable = Runnable {
                        showActionView(viewHolder.secondActionContainer)
                        showActionView(viewHolder.firstActionContainer)
                    }
                }
                1 -> {
                    prepareShowActionView(viewHolder.firstActionContainer)
                    viewHolder.pendingAnimationRunnable = Runnable {
                        hideActionView(viewHolder.secondActionContainer, View.GONE)
                        showActionView(viewHolder.firstActionContainer)
                    }
                    if (lastFocusedViewId == R.id.action_second_container) lastFocusedViewId = R.id.info_container
                }
                else -> {
                    viewHolder.pendingAnimationRunnable = Runnable {
                        hideActionView(viewHolder.secondActionContainer, View.GONE)
                        hideActionView(viewHolder.firstActionContainer, View.GONE)
                    }
                    lastFocusedViewId = R.id.info_container
                    SoftPreconditions.checkState(viewHolder.infoContainer.isFocusable, TAG,
                        "No focusable view in this row: $viewHolder")
                }
            }
            val view = viewHolder.view.findViewById<View>(lastFocusedViewId)
            if (view != null && view.visibility == View.VISIBLE) {
                // Beim Auswählen bekommt zunächst der Infobereich den Fokus. Damit dasselbe Element wie
                // in der vorherigen Zeile fokussiert wird, explizit requestFocus() aufrufen.
                if (view.hasFocus()) {
                    viewHolder.pendingAnimationRunnable?.run()
                } else if (view.isFocusable) {
                    view.requestFocus()
                } else {
                    viewHolder.view.requestFocus()
                }
            }
        } else {
            viewHolder.pendingAnimationRunnable = null
            hideActionView(viewHolder.firstActionContainer, View.GONE)
            hideActionView(viewHolder.secondActionContainer, View.GONE)
        }
    }

    private fun prepareShowActionView(view: View) {
        if (view.visibility != View.VISIBLE) view.alpha = 0.0f
        view.visibility = View.VISIBLE
    }

    /** Einblend-Animation. */
    private fun showActionView(view: View) {
        view.animate()
            .alpha(1.0f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(animationDuration.toLong())
            .start()
    }

    /** Ausblend-Animation, danach [visibility] setzen. */
    private fun hideActionView(view: View, visibility: Int) {
        if (view.visibility != View.VISIBLE) {
            if (view.visibility != visibility) view.visibility = visibility
            return
        }
        view.animate()
            .alpha(0.0f)
            .setInterpolator(DecelerateInterpolator())
            .setDuration(animationDuration.toLong())
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.visibility = visibility
                    view.animate().setListener(null)
                }
            })
            .start()
    }

    /** Verfügbare Aktionen je nach Zustand der Zeile (umgekehrte Reihenfolge wie auf dem Bildschirm). */
    protected open fun getAvailableActions(row: ScheduleRow): IntArray? {
        if (row.schedule != null) {
            if (row.isRecordingInProgress) {
                return intArrayOf(ACTION_STOP_RECORDING)
            } else if (row.isOnAir && !row.hasRecordedProgram()) {
                if (row.isRecordingNotStarted) {
                    return if (canResolveConflict()) {
                        // „START“ kann den Konfliktzustand ändern.
                        intArrayOf(ACTION_REMOVE_SCHEDULE, ACTION_START_RECORDING)
                    } else {
                        intArrayOf(ACTION_REMOVE_SCHEDULE)
                    }
                } else if (row.isRecordingFinished) {
                    return intArrayOf(ACTION_START_RECORDING)
                } else {
                    SoftPreconditions.checkState(false, TAG, "Invalid row state in checking the available actions(on air): $row")
                }
            } else {
                if (row.isScheduleCanceled) {
                    return intArrayOf(ACTION_CREATE_SCHEDULE)
                } else if (dvrManager.isConflicting(row.schedule) && canResolveConflict()) {
                    return intArrayOf(ACTION_REMOVE_SCHEDULE, ACTION_CREATE_SCHEDULE)
                } else if (row.isRecordingNotStarted) {
                    return intArrayOf(ACTION_REMOVE_SCHEDULE)
                } else if (row.isRecordingFailed) {
                    return intArrayOf(ACTION_REMOVE_SCHEDULE)
                } else if (row.isRecordingFinished) {
                    return intArrayOf()
                } else {
                    SoftPreconditions.checkState(false, TAG,
                        "Invalid row state in checking the available actions(future schedule): $row")
                }
            }
        }
        return null
    }

    /** Können Konflikte in dieser Ansicht aufgelöst werden? */
    protected open fun canResolveConflict(): Boolean = true

    /** Soll die Aufnahme nach dem Entfernen erhalten bleiben? */
    protected open fun shouldKeepScheduleAfterRemoving(): Boolean = false

    /** Soll die Zeile ausgegraut werden? */
    protected open fun shouldBeGrayedOut(row: ScheduleRow): Boolean =
        row.schedule == null ||
            (row.isOnAir && !row.isRecordingInProgress && !row.hasRecordedProgram()) ||
            dvrManager.isConflicting(row.schedule) ||
            row.isScheduleCanceled ||
            row.isRecordingFailed

    companion object {
        private const val TAG = "ScheduleRowPresenter"

        /** Aufnahme starten. */
        const val ACTION_START_RECORDING = 1
        /** Aufnahme stoppen. */
        const val ACTION_STOP_RECORDING = 2
        /** Aufnahme für die Zeile planen. */
        const val ACTION_CREATE_SCHEDULE = 3
        /** Geplante Aufnahme entfernen. */
        const val ACTION_REMOVE_SCHEDULE = 4
    }
}
