package com.android.tv.ui

import android.content.Context
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R

/** GuidedActionsStylist mit Trennlinien-Einträgen (ID ACTION_DIVIDER). */
open class GuidedActionsStylistWithDivider : GuidedActionsStylist() {

    override fun getItemViewType(action: GuidedAction): Int =
        if (action.id == ACTION_DIVIDER.toLong()) VIEW_TYPE_DIVIDER else super.getItemViewType(action)

    override fun onProvideItemLayoutId(viewType: Int): Int =
        if (viewType == VIEW_TYPE_DIVIDER) R.layout.guided_action_divider else super.onProvideItemLayoutId(viewType)

    companion object {
        const val ACTION_DIVIDER = -100
        private const val VIEW_TYPE_DIVIDER = 1

        @JvmStatic
        fun createDividerAction(context: Context): GuidedAction = GuidedAction.Builder(context)
            .id(ACTION_DIVIDER.toLong())
            .title(null)
            .description(null)
            .focusable(false)
            .infoOnly(true)
            .build()
    }
}
