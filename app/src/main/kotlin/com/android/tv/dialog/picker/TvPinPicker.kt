package com.android.tv.dialog.picker

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.view.accessibility.AccessibilityManager
import androidx.leanback.widget.picker.PinPicker

/** PIN-Eingabe; mit Bedienungshilfen springt der erste Klick zur nächsten Ziffer. */
@SuppressLint("RestrictedApi")
class TvPinPicker @JvmOverloads constructor(
    context: Context, attributeSet: AttributeSet?, defStyleAttr: Int = 0,
) : PinPicker(context, attributeSet, defStyleAttr) {

    private var skipPerformClick = true
    private val isAccessibilityEnabled = context.getSystemService(AccessibilityManager::class.java).isEnabled

    init {
        isActivated = true
    }

    override fun performClick(): Boolean {
        if (skipPerformClick && isAccessibilityEnabled) {
            skipPerformClick = false
            // Fokus zum nächsten Wert
            setColumnValue(selectedColumn, 1, true)
            return false
        }
        return super.performClick()
    }
}
