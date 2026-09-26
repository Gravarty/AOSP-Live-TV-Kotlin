package com.android.tv.common.ui.setup

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import com.android.tv.R

/** Setup-Seite mit GuidedStep-Inhalt und optionalen Buttons "Fertig"/"Überspringen". */
abstract class SetupMultiPaneFragment : SetupFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!
        if (savedInstanceState == null) {
            childFragmentManager.beginTransaction()
                .replace(R.id.guided_step_fragment_container, onCreateContentFragment(), CONTENT_FRAGMENT_TAG)
                .commit()
        }
        if (needsDoneButton()) setOnClickAction(view.findViewById(R.id.button_done), getActionCategory(), ACTION_DONE)
        if (needsSkipButton()) {
            view.findViewById<View>(R.id.button_skip).visibility = View.VISIBLE
            setOnClickAction(view.findViewById(R.id.button_skip), getActionCategory(), ACTION_SKIP)
        }
        if (!needsDoneButton() && !needsSkipButton()) {
            // Button-Spalte aus dem Bild schieben
            val lp = view.findViewById<View>(R.id.done_button_container).layoutParams as MarginLayoutParams
            val width = -resources.getDimensionPixelOffset(R.dimen.setup_done_button_container_width)
            if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) lp.rightMargin = width else lp.leftMargin = width
            view.findViewById<View>(R.id.button_done).isFocusable = false
        }
        return view
    }

    override fun getLayoutResourceId() = R.layout.fragment_setup_multi_pane

    protected abstract fun onCreateContentFragment(): SetupGuidedStepFragment

    protected fun getContentFragment(): SetupGuidedStepFragment? =
        childFragmentManager.findFragmentByTag(CONTENT_FRAGMENT_TAG) as SetupGuidedStepFragment?

    protected abstract fun getActionCategory(): String
    protected open fun needsDoneButton() = true
    protected open fun needsSkipButton() = false

    override fun getParentIdsForDelay(): IntArray =
        intArrayOf(androidx.leanback.R.id.content_fragment, androidx.leanback.R.id.guidedactions_list)

    override fun getSharedElementIds(): IntArray =
        intArrayOf(androidx.leanback.R.id.action_fragment_background, R.id.done_button_container)

    companion object {
        const val ACTION_DONE = Int.MAX_VALUE
        const val ACTION_SKIP = ACTION_DONE - 1
        const val MAX_SUBCLASSES_ID = ACTION_SKIP - 1
        const val CONTENT_FRAGMENT_TAG = "content_fragment"
    }
}
