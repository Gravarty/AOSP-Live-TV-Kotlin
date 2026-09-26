package com.android.tv.common.ui.setup.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TypeEvaluator
import android.content.Context
import android.transition.Transition
import android.transition.TransitionSet
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import com.android.tv.R

/** Werte und Hilfen für die Einrichtungs-Animationen. */
object SetupAnimationHelper {
    private const val ANIMATION_TIME_SCALE = 1.0f
    @JvmField val DELAY_BETWEEN_SIBLINGS_MS: Long = applyAnimationTimeScale(33)
    private var initialized = false
    private var fragmentTransitionDuration = 0L
    private var fragmentTransitionLongDistance = 0
    private var fragmentTransitionShortDistance = 0

    @JvmStatic
    fun initialize(context: Context) {
        if (initialized) return
        val res = context.resources
        fragmentTransitionDuration = res.getInteger(R.integer.setup_fragment_transition_duration).toLong()
        fragmentTransitionLongDistance = res.getDimensionPixelOffset(R.dimen.setup_fragment_transition_long_distance)
        fragmentTransitionShortDistance = res.getDimensionPixelOffset(R.dimen.setup_fragment_transition_short_distance)
        initialized = true
    }

    private fun checkInitialized() = check(initialized) { "SetupAnimationHelper not initialized" }

    class TransitionBuilder {
        private var slideEdge = Gravity.START
        private val distance: Int
        private var duration: Long
        private var parentIdForDelay: IntArray? = null
        private var excludeIds: IntArray? = null

        init {
            checkInitialized()
            distance = fragmentTransitionLongDistance
            duration = fragmentTransitionDuration
        }

        fun setSlideEdge(v: Int) = apply { slideEdge = v }
        fun setDuration(v: Long) = apply { duration = v }
        fun setParentIdsForDelay(v: IntArray?) = apply { parentIdForDelay = v }
        fun setExcludeIds(v: IntArray?) = apply { excludeIds = v }

        fun build(): Transition = FadeAndShortSlide(slideEdge, parentIdForDelay).also { t ->
            t.setDistance(distance)
            t.duration = duration
            excludeIds?.forEach { t.excludeTarget(it, true) }
        }
    }

    @JvmStatic fun setLongDistance(t: FadeAndShortSlide) { checkInitialized(); t.setDistance(fragmentTransitionLongDistance) }
    @JvmStatic fun setShortDistance(t: FadeAndShortSlide) { checkInitialized(); t.setDistance(fragmentTransitionShortDistance) }

    @JvmStatic
    fun applyAnimationTimeScale(animator: Animator): Animator {
        if (animator is AnimatorSet) animator.childAnimations.forEach { applyAnimationTimeScale(it) }
        if (animator.duration > 0) animator.duration = (animator.duration * ANIMATION_TIME_SCALE).toLong()
        animator.startDelay = (animator.startDelay * ANIMATION_TIME_SCALE).toLong()
        return animator
    }

    @JvmStatic
    fun applyAnimationTimeScale(transition: Transition): Transition {
        if (transition is TransitionSet) for (i in 0 until transition.transitionCount) applyAnimationTimeScale(transition.getTransitionAt(i))
        if (transition.duration > 0) transition.duration = (transition.duration * ANIMATION_TIME_SCALE).toLong()
        transition.startDelay = (transition.startDelay * ANIMATION_TIME_SCALE).toLong()
        return transition
    }

    @JvmStatic
    fun applyAnimationTimeScale(time: Long): Long = (time * ANIMATION_TIME_SCALE).toLong()

    @JvmStatic
    fun createFrameAnimator(imageView: ImageView, frames: IntArray): ObjectAnimator = createFrameAnimatorWithDelay(imageView, frames, 0)

    /** Einzelbild-Animation mit 60 fps (setzt nacheinander imageResource). */
    @JvmStatic
    fun createFrameAnimatorWithDelay(imageView: ImageView, frames: IntArray, startDelay: Long): ObjectAnimator =
        ObjectAnimator.ofInt(imageView, "imageResource", *frames).apply {
            duration = frames.size * 1000L / 60
            interpolator = null
            this.startDelay = startDelay
            setEvaluator(TypeEvaluator<Int> { _, startValue, _ -> startValue })
        }

    @JvmStatic
    fun createFadeOutAnimator(view: View, duration: Long, makeVisibleAfterAnimation: Boolean): Animator =
        ObjectAnimator.ofFloat(view, View.ALPHA, 1.0f, 0.0f).setDuration(duration).apply {
            if (makeVisibleAfterAnimation) {
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { view.alpha = 1.0f }
                })
            }
        }
}
