package com.android.tv.dvr.ui.playback

import android.content.Context
import com.android.tv.R
import com.android.tv.dvr.ui.browse.RecordedProgramPresenter
import com.android.tv.dvr.ui.browse.RecordingCardView

/** Erzeugt Karten für ähnliche Aufnahmen in der DVR-Wiedergabe. */
internal class DvrPlaybackCardPresenter(context: Context) : RecordedProgramPresenter(context) {
    // Eigene Referenz statt getContext() des Basis-Presenters (gleicher Kontext)
    private val cardContext = context
    private val relatedRecordingCardWidth =
        context.resources.getDimensionPixelSize(R.dimen.dvr_related_recordings_width)
    private val relatedRecordingCardHeight =
        context.resources.getDimensionPixelSize(R.dimen.dvr_related_recordings_height)

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder =
        RecordedProgramViewHolder(
            RecordingCardView(cardContext, relatedRecordingCardWidth, relatedRecordingCardHeight, true),
            null,
        )
}
