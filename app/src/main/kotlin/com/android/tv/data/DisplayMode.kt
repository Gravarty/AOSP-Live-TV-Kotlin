package com.android.tv.data

import android.content.Context
import com.android.tv.R

/** Bildformate; Werte passend zu R.array.display_mode_labels. */
object DisplayMode {
    const val MODE_NORMAL = 0
    const val MODE_FULL = 1
    const val MODE_ZOOM = 2
    const val SIZE_OF_RATIO_TYPES = MODE_ZOOM + 1
    const val MODE_NOT_DEFINED = -1

    @JvmStatic
    fun getLabel(mode: Int, context: Context): String = context.resources.getStringArray(R.array.display_mode_labels)[mode]
}
