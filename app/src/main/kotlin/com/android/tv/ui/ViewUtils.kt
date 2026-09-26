package com.android.tv.ui

import android.animation.Animator
import android.animation.ValueAnimator
import android.view.View

object ViewUtils {
    /** Ab Android 10 öffentliche API – die Reflection des Originals entfällt. */
    @JvmStatic
    fun setTransitionAlpha(v: View, alpha: Float) {
        v.transitionAlpha = alpha
    }

    /** Höhen-Animation; bei 0 wird die View ausgeblendet (GONE). */
    @JvmStatic
    fun createHeightAnimator(target: View, initialHeight: Int, targetHeight: Int): Animator =
        ValueAnimator.ofInt(initialHeight, targetHeight).apply {
            addUpdateListener {
                val value = it.animatedValue as Int
                if (value == 0) {
                    if (target.visibility != View.GONE) target.visibility = View.GONE
                } else {
                    if (target.visibility != View.VISIBLE) target.visibility = View.VISIBLE
                    setLayoutHeight(target, value)
                }
            }
        }

    @JvmStatic
    fun getLayoutHeight(view: View): Int = view.layoutParams.height

    @JvmStatic
    fun setLayoutHeight(view: View, height: Int) {
        val lp = view.layoutParams
        if (height != lp.height) {
            lp.height = height
            view.layoutParams = lp
        }
    }
}
