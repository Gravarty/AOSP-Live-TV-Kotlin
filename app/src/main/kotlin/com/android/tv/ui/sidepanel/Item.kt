package com.android.tv.ui.sidepanel

import android.view.View
import android.view.ViewGroup
import androidx.annotation.UiThread

/** Eintrag einer Seitenleiste. */
@UiThread
abstract class Item {
    private var itemView: View? = null
    var isEnabled = true
        private set
    private var clickable = true

    fun setEnabled(enabled: Boolean) {
        if (isEnabled != enabled) {
            isEnabled = enabled
            itemView?.let { setEnabledInternal(it, enabled) }
        }
    }

    fun setClickable(clickable: Boolean) {
        this.clickable = clickable
        itemView?.isClickable = clickable
    }

    fun notifyUpdated() {
        if (itemView != null) onUpdate()
    }

    abstract fun getResourceId(): Int

    open fun onBind(view: View) { itemView = view }
    open fun onUnbind() { itemView = null }

    open fun onUpdate() {
        itemView?.let {
            setEnabledInternal(it, isEnabled)
            it.isClickable = clickable
        }
    }

    abstract fun onSelected()
    open fun onFocused() {}

    protected val isBound: Boolean get() = itemView != null

    private fun setEnabledInternal(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is ViewGroup) for (i in 0 until view.childCount) setEnabledInternal(view.getChildAt(i), enabled)
    }
}
