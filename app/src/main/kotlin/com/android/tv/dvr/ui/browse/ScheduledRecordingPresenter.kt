package com.android.tv.dvr.ui.browse

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Zeigt eine [ScheduledRecording] in [DvrBrowseFragment]. */
internal class ScheduledRecordingPresenter(context: Context) : DvrItemPresenter<ScheduledRecording>(context) {
    // DvrManager ist ohne DVR null; die Browse-Ansicht gibt es nur mit DVR.
    private val dvrManager: DvrManager? = TvSingletons.getSingletons(context).getDvrManager()
    private val progressBarColor: Int =
        context.resources.getColor(R.color.play_controls_recording_icon_color_on_focus, null)

    private inner class ScheduledRecordingViewHolder(view: RecordingCardView, progressBarColor: Int) :
        DvrItemViewHolder(view) {
        // Handler() ist veraltet → expliziter Main-Looper.
        private val handler = Handler(Looper.getMainLooper())
        private var scheduledRecording: ScheduledRecording? = null
        private val progressBarUpdater = object : Runnable {
            override fun run() {
                updateProgressBar()
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL_MS)
            }
        }

        init {
            view.setProgressBarColor(progressBarColor)
        }

        override fun onBound(item: ScheduledRecording) {
            scheduledRecording = item
            updateProgressBar()
            startUpdateProgressBar()
        }

        override fun onUnbound() {
            stopUpdateProgressBar()
            scheduledRecording = null
            getView().reset()
        }

        private fun updateProgressBar() {
            val recording = scheduledRecording ?: return
            val cardView = getView()
            when (recording.state) {
                ScheduledRecording.STATE_RECORDING_IN_PROGRESS -> cardView.setProgressBar(
                    max(0, min((100 * (System.currentTimeMillis() - recording.startTimeMs) / recording.duration).toInt(), 100)))
                ScheduledRecording.STATE_RECORDING_FINISHED -> cardView.setProgressBar(100)
                // Fortschrittsbalken ausblenden.
                else -> cardView.setProgressBar(null)
            }
        }

        private fun startUpdateProgressBar() {
            handler.post(progressBarUpdater)
        }

        private fun stopUpdateProgressBar() {
            handler.removeCallbacks(progressBarUpdater)
        }
    }

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder =
        ScheduledRecordingViewHolder(RecordingCardView(context), progressBarColor)

    override fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: ScheduledRecording) {
        val cardView = viewHolder.getView()
        val details = DetailsContent.createFromScheduledRecording(context, item)
        cardView.setTitle(details.title)
        cardView.setImageUri(details.logoImageUri, details.isUsingChannelLogo)
        if (item.state == ScheduledRecording.STATE_RECORDING_FAILED) {
            cardView.setRecordingFailedContent(context)
        } else if (dvrManager?.isConflicting(item) == true) {
            cardView.setRecordingConflictContent(context)
        } else {
            cardView.setContent(generateMajorContent(item), null)
        }
        cardView.setDetailBackgroundImageUri(details.backgroundImageUri)
    }

    private fun generateMajorContent(recording: ScheduledRecording): String {
        val dateDifference = Utils.computeDateDifference(System.currentTimeMillis(), recording.startTimeMs)
        return when {
            dateDifference <= 0 -> context.getString(
                R.string.dvr_date_today_time,
                Utils.getDurationString(context, recording.startTimeMs, recording.endTimeMs, false, false, true, 0))
            dateDifference == 1 -> context.getString(
                R.string.dvr_date_tomorrow_time,
                Utils.getDurationString(context, recording.startTimeMs, recording.endTimeMs, false, false, true, 0))
            else -> Utils.getDurationString(
                context, recording.startTimeMs, recording.startTimeMs, false, true, false, 0)
        }
    }

    companion object {
        private val PROGRESS_UPDATE_INTERVAL_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
