package com.android.tv.dvr.ui

import android.content.Context
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R

/** Stylist für die DVR-Einstellungs-GuidedSteps. */
open class DvrGuidedActionsStylist(private val isButtonActions: Boolean) : GuidedActionsStylist() {

    init {
        if (isButtonActions) setAsButtonActions()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup): View {
        initializeIfNeeded(container.context)
        val v = super.onCreateView(inflater, container)
        if (isButtonActions) (v.layoutParams as LinearLayout.LayoutParams).weight = widthWeight
        return v
    }

    override fun onCreateViewHolder(parent: ViewGroup): ViewHolder {
        initializeIfNeeded(parent.context)
        val viewHolder = super.onCreateViewHolder(parent)
        viewHolder.itemView.layoutParams.height = itemHeight
        return viewHolder
    }

    private fun initializeIfNeeded(context: Context) {
        if (initialized) return
        initialized = true
        itemHeight = context.resources.getDimensionPixelSize(R.dimen.dvr_settings_one_line_action_container_height)
        val outValue = TypedValue()
        context.resources.getValue(R.dimen.dvr_settings_button_actions_list_width_weight, outValue, true)
        widthWeight = outValue.float
    }

    private companion object {
        var initialized = false
        var widthWeight = 0f
        var itemHeight = 0
    }
}
