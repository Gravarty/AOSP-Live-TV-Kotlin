package com.android.tv.common.ui.setup.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.graphics.Path
import android.transition.Transition
import android.transition.TransitionValues
import android.view.View
import com.android.tv.R
import kotlin.math.roundToInt

/** Verschiebe-Animation für Übergänge (setzt bei Abbruch an der aktuellen Position fort). */
internal object TranslationAnimationCreator {

    fun createAnimation(
        view: View, values: TransitionValues, viewPosX: Int, startXIn: Float, endX: Float,
        interpolator: TimeInterpolator, transition: Transition,
    ): Animator? {
        val terminalX = view.translationX
        var startX = startXIn
        (values.view.getTag(R.id.transitionPosition) as Int?)?.let { startX = it - viewPosX + terminalX }
        val startPosX = viewPosX + (startX - terminalX).roundToInt()
        view.translationX = startX
        if (startX == endX) return null
        val path = Path().apply {
            moveTo(startX, 0f)
            lineTo(endX, 0f)
        }
        val anim = ObjectAnimator.ofFloat(view, View.TRANSLATION_X, View.TRANSLATION_Y, path)
        val listener = TransitionPositionListener(view, values.view, startPosX, terminalX)
        transition.addListener(listener)
        anim.addListener(listener)
        anim.addPauseListener(listener)
        anim.interpolator = interpolator
        return anim
    }

    private class TransitionPositionListener(
        private val movingView: View,
        private val viewInHierarchy: View,
        startX: Int,
        private val terminalX: Float,
    ) : AnimatorListenerAdapter(), Transition.TransitionListener {
        private val startX = startX - movingView.translationX.roundToInt()
        private var transitionPosition: Int? = viewInHierarchy.getTag(R.id.transitionPosition) as Int?
        private var pausedX = 0f

        init {
            if (transitionPosition != null) viewInHierarchy.setTag(R.id.transitionPosition, null)
        }

        override fun onAnimationCancel(animation: Animator) {
            transitionPosition = (startX + movingView.translationX).roundToInt()
            viewInHierarchy.setTag(R.id.transitionPosition, transitionPosition)
        }

        override fun onAnimationEnd(animator: Animator) {}

        override fun onAnimationPause(animator: Animator) {
            pausedX = movingView.translationX
            movingView.translationX = terminalX
        }

        override fun onAnimationResume(animator: Animator) {
            movingView.translationX = pausedX
        }

        override fun onTransitionStart(transition: Transition) {}
        override fun onTransitionEnd(transition: Transition) { movingView.translationX = terminalX }
        override fun onTransitionCancel(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionResume(transition: Transition) {}
    }
}
