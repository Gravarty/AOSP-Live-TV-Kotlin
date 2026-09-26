package com.android.tv.ui.sidepanel

import android.view.View
import android.widget.TextView
import com.android.tv.R

/** Eintrag mit Titel und optionaler Beschreibung. */
abstract class ActionItem @JvmOverloads constructor(
    private val title: String?, private val description: String? = null,
) : Item() {
    override fun getResourceId() = R.layout.option_item_action

    override fun onBind(view: View) {
        super.onBind(view)
        view.findViewById<TextView>(R.id.title).text = title
        val descriptionView = view.findViewById<TextView>(R.id.description)
        if (description != null) {
            descriptionView.visibility = View.VISIBLE
            descriptionView.text = description
        } else {
            descriptionView.visibility = View.GONE
        }
    }
}

open class SimpleActionItem @JvmOverloads constructor(title: String?, description: String? = null) :
    ActionItem(title, description) {
    override fun onSelected() {}
}

/** Öffnet beim Auswählen eine weitere Seitenleiste. */
abstract class SubMenuItem(
    title: String?, description: String?, private val sideFragmentManager: SideFragmentManager,
) : ActionItem(title, description) {
    constructor(title: String?, fragmentManager: SideFragmentManager) : this(title, null, fragmentManager)

    override fun onSelected() = launchFragment()

    protected open fun launchFragment() = sideFragmentManager.show(getFragment())

    protected abstract fun getFragment(): SideFragment<*>
}
