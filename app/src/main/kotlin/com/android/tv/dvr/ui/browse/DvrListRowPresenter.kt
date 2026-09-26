package com.android.tv.dvr.ui.browse

import android.content.Context
import android.view.ViewGroup
import androidx.leanback.widget.ListRowPresenter
import com.android.tv.R

/** ListRowPresenter für ein-/ausklappbare Kartenlisten. */
open class DvrListRowPresenter(context: Context) : ListRowPresenter() {
    init {
        rowHeight = ViewGroup.LayoutParams.WRAP_CONTENT
        expandedRowHeight = context.resources.getDimensionPixelSize(R.dimen.dvr_library_expanded_row_height)
    }
}
