package com.android.tv.dvr.ui.browse

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.leanback.widget.Presenter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.util.Utils

/** Zeigt die Karte „Alle Planungen“ in [DvrBrowseFragment]. */
internal class FullSchedulesCardPresenter(context: Context) : DvrItemPresenter<Any>(context) {
    private val iconDrawable: Drawable? = context.getDrawable(R.drawable.dvr_full_schedule)
    private val cardTitleText: String = context.getString(R.string.dvr_full_schedule_card_view_title)

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder = DvrItemViewHolder(RecordingCardView(context))

    override fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: Any) {
        val cardView = viewHolder.view as RecordingCardView
        cardView.setTitle(cardTitleText)
        cardView.setImage(iconDrawable)
        val scheduledRecordings =
            TvSingletons.getSingletons(context).getDvrDataManager().getAvailableScheduledRecordings()
        var fullDays = 0
        if (scheduledRecordings.isNotEmpty()) {
            fullDays = Utils.computeDateDifference(
                System.currentTimeMillis(),
                scheduledRecordings.maxWith(ScheduledRecording.START_TIME_COMPARATOR).startTimeMs) + 1
        }
        cardView.setContent(
            context.resources.getQuantityString(R.plurals.dvr_full_schedule_card_view_content, fullDays, fullDays),
            null)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        (viewHolder.view as RecordingCardView).reset()
        super.onUnbindViewHolder(viewHolder)
    }

    override fun onCreateOnClickListener(): View.OnClickListener =
        View.OnClickListener { DvrUiHelper.startSchedulesActivity(context, null) }
}
