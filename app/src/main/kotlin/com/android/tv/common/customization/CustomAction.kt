package com.android.tv.common.customization

import android.content.Intent
import android.graphics.drawable.Drawable

/** Menüeintrag aus einem OEM-Anpassungspaket; Priorität < 100 = vor den Standardeinträgen. */
class CustomAction(
    private val positionPriority: Int,
    val title: String,
    val iconDrawable: Drawable?,
    val intent: Intent,
) : Comparable<CustomAction> {
    val isFront: Boolean get() = positionPriority < POSITION_THRESHOLD

    override fun compareTo(other: CustomAction) = positionPriority - other.positionPriority

    companion object {
        private const val POSITION_THRESHOLD = 100
    }
}
