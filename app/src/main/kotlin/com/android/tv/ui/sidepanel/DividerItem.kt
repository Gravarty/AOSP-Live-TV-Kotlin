package com.android.tv.ui.sidepanel

import android.view.View
import android.widget.TextView
import com.android.tv.R

/** Trennlinie, optional mit Überschrift. */
class DividerItem @JvmOverloads constructor(private val title: String? = null) : Item() {
    private var titleView: TextView? = null

    override fun getResourceId() = R.layout.option_item_divider

    override fun onBind(view: View) {
        super.onBind(view)
        val tv = view.findViewById<TextView>(R.id.title).also { titleView = it }
        if (title == null) {
            tv.visibility = View.GONE
            view.minimumHeight = 0
        } else {
            tv.visibility = View.VISIBLE
            tv.text = title
            view.minimumHeight = view.resources.getDimensionPixelOffset(R.dimen.option_item_height)
        }
    }

    /** Bugfix: Original rief super.onUnbind() nicht auf – das Item blieb an einer alten View hängen. */
    override fun onUnbind() {
        super.onUnbind()
        titleView = null
    }

    override fun onSelected() {}
}
