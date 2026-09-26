package com.android.tv.guide

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.AccessibilityDelegateCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerViewAccessibilityDelegate

/** Screenreader: Fokus folgt der Bedienungshilfe, Scroll-Aktionen werden ignoriert. */
internal class ProgramRowAccessibilityDelegate(recyclerView: RecyclerView) : RecyclerViewAccessibilityDelegate(recyclerView) {
    private val itemDelegate = object : ItemDelegate(this) {
        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean {
            // Aktionen über SET_TEXT (Scrollen usw.) verwerfen
            if (action > AccessibilityNodeInfo.ACTION_SET_TEXT) return false
            return super.performAccessibilityAction(host, action, args)
        }
    }

    override fun getItemDelegate(): AccessibilityDelegateCompat = itemDelegate

    override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean {
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED) (host as ProgramRow).focusSearchAccessibility(child, View.FOCUS_FORWARD)
        return super.onRequestSendAccessibilityEvent(host, child, event)
    }
}
