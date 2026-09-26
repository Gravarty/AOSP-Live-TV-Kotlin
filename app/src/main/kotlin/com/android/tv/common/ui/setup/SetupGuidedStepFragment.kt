package com.android.tv.common.ui.setup

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R

/** GuidedStep-Inhalt für Setup-Seiten (2/3 Spalten, Screenreader-Anpassungen). GuidedStepFragment → SupportFragment. */
abstract class SetupGuidedStepFragment : GuidedStepSupportFragment() {
    private lateinit var contentFragment: View
    private var fromContentFragment = false
    private var accessibilityMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)!!
        view.findViewById<View>(androidx.leanback.R.id.action_fragment_root).setPadding(0, 0, 0, 0)
        contentFragment = view.findViewById(androidx.leanback.R.id.content_fragment)
        val guidanceLayoutParams = contentFragment.layoutParams as LinearLayout.LayoutParams
        guidanceLayoutParams.weight = 0f
        if (arguments?.getBoolean(KEY_THREE_PANE, false) == true) {
            // Rechts Platz für "Fertig"
            guidanceLayoutParams.width = resources.getDimensionPixelOffset(R.dimen.setup_guidedstep_guidance_section_width_3pane)
            val doneButtonWidth = resources.getDimensionPixelOffset(R.dimen.setup_done_button_container_width)
            val list = view.findViewById<View>(androidx.leanback.R.id.guidedactions_list)
            val lp = list.layoutParams as MarginLayoutParams
            if (resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR) lp.rightMargin = doneButtonWidth
            else lp.leftMargin = doneButtonWidth
        } else {
            guidanceLayoutParams.width = resources.getDimensionPixelOffset(R.dimen.setup_guidedstep_guidance_section_width_2pane)
        }
        // Auswahl-Hintergrund oben ausrichten
        guidedActionsStylist.actionsGridView?.apply {
            windowAlignmentOffset = resources.getDimensionPixelOffset(R.dimen.setup_guidedactions_selector_margin_top)
            windowAlignmentOffsetPercent = 0f
            itemAlignmentOffsetPercent = 0f
        }
        view.findViewById<ViewGroup>(androidx.leanback.R.id.guidedactions_list).isTransitionGroup = false
        view.findViewById<ViewGroup>(androidx.leanback.R.id.content_frame).apply {
            clipChildren = false
            clipToPadding = false
        }
        return view
    }

    override fun onCreateActionsStylist(): GuidedActionsStylist = object : GuidedActionsStylist() {
        override fun onBindViewHolder(vh: ViewHolder, action: GuidedAction) {
            super.onBindViewHolder(vh, action)
            setActionAccessibilityDelegate(vh, action)
        }
    }

    override fun onResume() {
        super.onResume()
        val am = requireActivity().getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager?
        accessibilityMode = am != null && am.isEnabled && am.isTouchExplorationEnabled
        contentFragment.isFocusable = accessibilityMode
        if (accessibilityMode) {
            contentFragment.accessibilityDelegate = object : View.AccessibilityDelegate() {
                override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
                    if (action == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS && actions.isNotEmpty()) {
                        // Vorlesen soll vom ersten Eintrag weitergehen
                        guidedActionsStylist.actionsGridView?.scrollToPosition(0)
                        fromContentFragment = true
                    }
                    return super.performAccessibilityAction(host, action, args)
                }
            }
            contentFragment.requestFocus()
        }
    }

    override fun onCreateGuidanceStylist(): GuidanceStylist = object : GuidanceStylist() {
        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, guidance: Guidance): View =
            super.onCreateView(inflater, container, guidance).also {
                if (guidance.iconDrawable == null) iconView?.visibility = View.GONE
            }
    }

    protected abstract fun getActionCategory(): String

    protected fun getDoneButton(): View? = activity?.findViewById(R.id.button_done)

    override fun onGuidedActionClicked(action: GuidedAction) {
        if (!action.isFocusable) return
        SetupActionHelper.onActionClick(this, getActionCategory(), action.id.toInt())
    }

    // Übergänge steuert das umgebende SetupMultiPaneFragment
    override fun onProvideFragmentTransitions() {}

    override fun isFocusOutEndAllowed() = true

    protected open fun setActionAccessibilityDelegate(vh: GuidedActionsStylist.ViewHolder, action: GuidedAction) {
        if (!accessibilityMode || findActionPositionById(action.id) == 0) return
        vh.itemView.accessibilityDelegate = object : View.AccessibilityDelegate() {
            override fun performAccessibilityAction(host: View, act: Int, args: Bundle?): Boolean {
                if ((act == AccessibilityNodeInfo.ACTION_FOCUS || act == AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS) &&
                    fromContentFragment
                ) {
                    getActionItemView(0)?.let {
                        it.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED)
                        fromContentFragment = false
                        return true
                    }
                }
                return super.performAccessibilityAction(host, act, args)
            }
        }
    }

    companion object {
        const val KEY_THREE_PANE = "key_three_pane"
    }
}
