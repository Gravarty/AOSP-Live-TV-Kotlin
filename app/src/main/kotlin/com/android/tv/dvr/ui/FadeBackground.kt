package com.android.tv.dvr.ui

import android.animation.Animator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.transition.Transition
import android.transition.TransitionValues
import android.transition.Visibility
import android.util.AttributeSet
import android.view.ViewGroup
import com.android.tv.R

/** Transition, die den Hintergrund einer View über die Hintergrundfarbe ein- bzw. ausblendet. */
class FadeBackground(context: Context, attrs: AttributeSet?) : Transition(context, attrs) {
    private val mode: Int

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.FadeBackground)
        mode = a.getInt(R.styleable.FadeBackground_fadingMode, Visibility.MODE_IN)
        a.recycle()
    }

    override fun captureStartValues(transitionValues: TransitionValues) {}

    override fun captureEndValues(transitionValues: TransitionValues) {}

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) return null
        val background = endValues.view.background
        if (background is ColorDrawable) {
            val color = background.color
            val transparentColor = Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            return if (mode == Visibility.MODE_OUT) {
                ObjectAnimator.ofArgb(background, "color", transparentColor)
            } else {
                ObjectAnimator.ofArgb(background, "color", transparentColor, color)
            }
        }
        return null
    }
}
