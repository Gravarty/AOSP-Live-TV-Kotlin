package com.android.tv.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.view.View

/** Nutzt während einer Animation einen Hardware-Layer (flüssigere Überblendungen). */
open class HardwareLayerAnimatorListenerAdapter(private val view: View) : AnimatorListenerAdapter() {
    private var layerTypeChanged = false

    override fun onAnimationStart(animator: Animator) {
        if (view.hasOverlappingRendering() && view.layerType == View.LAYER_TYPE_NONE) {
            layerTypeChanged = true
            view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
        }
    }

    override fun onAnimationEnd(animator: Animator) {
        if (layerTypeChanged) {
            layerTypeChanged = false
            view.setLayerType(View.LAYER_TYPE_NONE, null)
        }
    }
}
