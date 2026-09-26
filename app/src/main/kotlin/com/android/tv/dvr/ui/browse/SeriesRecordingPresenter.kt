package com.android.tv.dvr.ui.browse

import android.content.Context
import android.media.tv.TvInputManager
import android.text.TextUtils
import androidx.leanback.widget.Presenter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording

/** Zeigt eine [SeriesRecording] in [DvrBrowseFragment]. */
internal class SeriesRecordingPresenter(context: Context) : DvrItemPresenter<SeriesRecording>(context) {
    private val dvrDataManager: DvrDataManager
    // DvrManager ist ohne DVR null; die Browse-Ansicht gibt es nur mit DVR.
    private val dvrManager: DvrManager?
    private val watchedPositionManager: DvrWatchedPositionManager

    init {
        val singletons = TvSingletons.getSingletons(context)
        dvrDataManager = singletons.getDvrDataManager()
        dvrManager = singletons.getDvrManager()
        watchedPositionManager = singletons.getDvrWatchedPositionManager()
    }

    private inner class SeriesRecordingViewHolder(
        private val cardView: RecordingCardView,
        private val dvrDataManager: DvrDataManager,
        private val dvrManager: DvrManager?,
        private val watchedPositionManager: DvrWatchedPositionManager,
    ) : DvrItemViewHolder(cardView),
        DvrWatchedPositionManager.WatchedPositionChangedListener,
        DvrDataManager.ScheduledRecordingListener,
        DvrDataManager.RecordedProgramListener {
        private lateinit var seriesRecording: SeriesRecording

        override fun onWatchedPositionChanged(recordedProgramId: Long, positionMs: Long) {
            if (positionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                watchedPositionManager.removeListener(this, recordedProgramId)
                updateCardViewContent()
            }
        }

        override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
            if (scheduledRecordings.any { it.seriesRecordingId == seriesRecording.id }) updateCardViewContent()
        }

        override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
            if (scheduledRecordings.any { it.seriesRecordingId == seriesRecording.id }) updateCardViewContent()
        }

        override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) {
            var needToUpdateCardView = false
            for (recordedProgram in recordedPrograms) {
                if (TextUtils.equals(recordedProgram.seriesId, seriesRecording.seriesId)) {
                    dvrDataManager.removeScheduledRecordingListener(this)
                    watchedPositionManager.addListener(this, recordedProgram.id)
                    needToUpdateCardView = true
                }
            }
            if (needToUpdateCardView) updateCardViewContent()
        }

        override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) {
            var needToUpdateCardView = false
            for (recordedProgram in recordedPrograms) {
                if (TextUtils.equals(recordedProgram.seriesId, seriesRecording.seriesId)) {
                    if (watchedPositionManager.getWatchedPosition(recordedProgram.id) ==
                        TvInputManager.TIME_SHIFT_INVALID_TIME
                    ) {
                        watchedPositionManager.removeListener(this, recordedProgram.id)
                    }
                    needToUpdateCardView = true
                }
            }
            if (needToUpdateCardView) updateCardViewContent()
        }

        override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) {
            // Nichts zu tun
        }

        override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
            // Nichts zu tun
        }

        override fun onBound(item: SeriesRecording) {
            seriesRecording = item
            dvrDataManager.addScheduledRecordingListener(this)
            dvrDataManager.addRecordedProgramListener(this)
            for (recordedProgram in dvrDataManager.getRecordedPrograms(item.id)) {
                if (watchedPositionManager.getWatchedPosition(recordedProgram.id) ==
                    TvInputManager.TIME_SHIFT_INVALID_TIME
                ) {
                    watchedPositionManager.addListener(this, recordedProgram.id)
                }
            }
            updateCardViewContent()
        }

        override fun onUnbound() {
            dvrDataManager.removeScheduledRecordingListener(this)
            dvrDataManager.removeRecordedProgramListener(this)
            watchedPositionManager.removeListener(this)
            getView().reset()
        }

        private fun updateCardViewContent() {
            var count = 0
            val quantityStringId: Int
            val recordedPrograms = dvrDataManager.getRecordedPrograms(seriesRecording.id)
            if (recordedPrograms.isEmpty()) {
                count = dvrManager?.getAvailableScheduledRecording(seriesRecording.id)?.size ?: 0
                quantityStringId = R.plurals.dvr_count_scheduled_recordings
            } else {
                for (recordedProgram in recordedPrograms) {
                    if (watchedPositionManager.getWatchedPosition(recordedProgram.id) ==
                        TvInputManager.TIME_SHIFT_INVALID_TIME
                    ) {
                        count++
                    }
                }
                if (count == 0) {
                    count = recordedPrograms.size
                    quantityStringId = R.plurals.dvr_count_recordings
                } else {
                    quantityStringId = R.plurals.dvr_count_new_recordings
                }
            }
            cardView.setContent(cardView.resources.getQuantityString(quantityStringId, count, count), null)
        }
    }

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder =
        SeriesRecordingViewHolder(RecordingCardView(context), dvrDataManager, dvrManager, watchedPositionManager)

    override fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: SeriesRecording) {
        val cardView = viewHolder.getView()
        // Bugfix: Das Original rief hier zusätzlich onBound() auf, obwohl DvrItemPresenter das nach
        // diesem Aufruf ohnehin tut (doppelte Listener-Registrierung). Der zusätzliche Aufruf entfällt.
        val details = DetailsContent.createFromSeriesRecording(context, item)
        cardView.setTitle(details.title)
        cardView.setImageUri(details.logoImageUri, details.isUsingChannelLogo)
        cardView.setDetailBackgroundImageUri(details.backgroundImageUri)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        // Bugfix: Das Original rief hier zusätzlich onUnbound() auf, obwohl super das ebenfalls tut.
        (viewHolder.view as RecordingCardView).reset()
        super.onUnbindViewHolder(viewHolder)
    }
}
