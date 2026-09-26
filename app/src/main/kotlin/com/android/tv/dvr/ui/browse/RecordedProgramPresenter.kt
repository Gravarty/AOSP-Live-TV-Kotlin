package com.android.tv.dvr.ui.browse

import android.content.Context
import android.media.tv.TvInputManager
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.util.Utils
import kotlin.math.max
import kotlin.math.min

/** Zeigt ein [RecordedProgram] in [DvrBrowseFragment]. */
open class RecordedProgramPresenter internal constructor(
    context: Context,
    private val showEpisodeTitle: Boolean,
    private val expandTitleWhenFocused: Boolean,
) : DvrItemPresenter<RecordedProgram>(context) {
    private val dvrWatchedPositionManager: DvrWatchedPositionManager =
        TvSingletons.getSingletons(context).getDvrWatchedPositionManager()
    private val todayString: String = context.getString(R.string.dvr_date_today)
    private val yesterdayString: String = context.getString(R.string.dvr_date_yesterday)
    private val progressBarColor: Int = context.resources.getColor(R.color.play_controls_progress_bar_watched, null)

    constructor(context: Context) : this(context, false, false)

    // Im Original protected; öffentlich, da DvrPlaybackCardPresenter ihn erzeugt.
    inner class RecordedProgramViewHolder(view: RecordingCardView, progressColor: Int?) :
        DvrItemViewHolder(view), DvrWatchedPositionManager.WatchedPositionChangedListener {
        private var program: RecordedProgram? = null
        private val showProgress: Boolean = progressColor != null

        init {
            if (progressColor != null) view.setProgressBarColor(progressColor)
        }

        private fun setProgressBar(watchedPositionMs: Long) {
            val p = program ?: return
            getView().setProgressBar(
                if (watchedPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) null
                else min(100, (100.0f * watchedPositionMs / p.durationMillis).toInt()))
        }

        override fun onWatchedPositionChanged(recordedProgramId: Long, positionMs: Long) {
            if (recordedProgramId == program?.id) setProgressBar(positionMs)
        }

        override fun onBound(item: RecordedProgram) {
            program = item
            if (showProgress) {
                dvrWatchedPositionManager.addListener(this, item.id)
                setProgressBar(dvrWatchedPositionManager.getWatchedPosition(item.id))
            } else {
                getView().setProgressBar(null)
            }
        }

        override fun onUnbound() {
            val p = program
            if (showProgress && p != null) dvrWatchedPositionManager.removeListener(this, p.id)
            getView().reset()
        }
    }

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder =
        RecordedProgramViewHolder(RecordingCardView(context, expandTitleWhenFocused), progressBarColor)

    override fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: RecordedProgram) {
        val cardView = (viewHolder as RecordedProgramViewHolder).getView()
        val details = DetailsContent.createFromRecordedProgram(context, item)
        cardView.setTitle(if (showEpisodeTitle) item.getEpisodeDisplayTitle(context) else details.title)
        cardView.setImageUri(details.logoImageUri, details.isUsingChannelLogo)
        cardView.setContent(generateMajorContent(item), generateMinorContent(item))
        cardView.setDetailBackgroundImageUri(details.backgroundImageUri)
    }

    private fun generateMajorContent(program: RecordedProgram): String {
        val dateDifference = Utils.computeDateDifference(program.startTimeUtcMillis, System.currentTimeMillis())
        return when (dateDifference) {
            0 -> todayString
            1 -> yesterdayString
            else -> Utils.getDurationString(
                context, program.startTimeUtcMillis, program.startTimeUtcMillis, false, true, false, 0)
        }
    }

    private fun generateMinorContent(program: RecordedProgram): String {
        val durationMinutes = max(1, Utils.getRoundOffMinsFromMs(program.durationMillis))
        return context.resources.getQuantityString(R.plurals.dvr_program_duration, durationMinutes, durationMinutes)
    }
}
