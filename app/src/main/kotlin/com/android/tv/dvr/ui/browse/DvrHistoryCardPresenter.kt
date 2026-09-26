package com.android.tv.dvr.ui.browse

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.View
import androidx.leanback.widget.Presenter
import com.android.tv.R
import com.android.tv.dvr.ui.DvrUiHelper

/** Zeigt die Karte „Aufnahmeverlauf“ in [DvrBrowseFragment]. */
internal class DvrHistoryCardPresenter(context: Context) : DvrItemPresenter<Any>(context) {
    private val iconDrawable: Drawable? = context.getDrawable(R.drawable.dvr_full_schedule)
    private val cardTitleText: String = context.getString(R.string.dvr_history_card_view_title)

    override fun onCreateDvrItemViewHolder(): DvrItemViewHolder = DvrItemViewHolder(RecordingCardView(context))

    override fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: Any) {
        val cardView = viewHolder.view as RecordingCardView
        cardView.setTitle(cardTitleText)
        cardView.setImage(iconDrawable)
    }

    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        (viewHolder.view as RecordingCardView).reset()
        super.onUnbindViewHolder(viewHolder)
    }

    override fun onCreateOnClickListener(): View.OnClickListener =
        View.OnClickListener { DvrUiHelper.startDvrHistoryActivity(context) }
}
