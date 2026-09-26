package com.android.tv.common.ui.setup.animation

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.TimeInterpolator
import android.transition.Fade
import android.transition.Transition
import android.transition.TransitionValues
import android.transition.Visibility
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator

/**
 * Einblenden + kurzes Hereinschieben (bzw. umgekehrt), optional gestaffelt nach Position
 * innerhalb eines Eltern-Views.
 */
class FadeAndShortSlide @JvmOverloads constructor(
    slideEdge: Int = Gravity.START,
    private val parentIdsForDelay: IntArray? = null,
) : Visibility() {

    private var slideEdge = 0
    private var slideCalculator: (ViewGroup, View, Int) -> Float = ::calculateEnd
    private var fade: Visibility = Fade()
    private var distance = DEFAULT_DISTANCE

    init {
        setSlideEdge(slideEdge)
    }

    override fun setEpicenterCallback(epicenterCallback: EpicenterCallback?) {
        super.setEpicenterCallback(epicenterCallback)
        fade.epicenterCallback = epicenterCallback
    }

    private fun captureValues(transitionValues: TransitionValues) {
        val position = IntArray(2)
        transitionValues.view.getLocationOnScreen(position)
        transitionValues.values[PROPNAME_SCREEN_POSITION] = position
    }

    /** Reihenfolge des Views innerhalb des Eltern-Views (für gestaffelte Verzögerung). */
    private fun getDelayOrder(view: View, appear: Boolean): Int {
        if (parentIdsForDelay == null) return -1
        val parentForDelay = findParentForDelay(view) as? ViewGroup ?: return -1
        val targets = ArrayList<View>()
        getTransitionTargets(parentForDelay, targets)
        val isLtr = view.layoutDirection == View.LAYOUT_DIRECTION_LTR
        val toLeft = if (isLtr) slideEdge == (if (appear) Gravity.END else Gravity.START)
        else slideEdge == (if (appear) Gravity.START else Gravity.END)
        targets.sortWith(ViewPositionComparator(parentForDelay, isLtr, toLeft))
        return targets.indexOf(view)
    }

    private fun findParentForDelay(view: View): View? {
        if (isParentForDelay(view.id)) return view
        var parent: View = view
        while (parent.parent is View) {
            parent = parent.parent as View
            if (isParentForDelay(parent.id)) return parent
        }
        return null
    }

    private fun isParentForDelay(viewId: Int) = parentIdsForDelay?.contains(viewId) == true

    private fun getTransitionTargets(parent: ViewGroup, out: MutableList<View>) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child is ViewGroup && !child.isTransitionGroup) getTransitionTargets(child, out) else out.add(child)
        }
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        super.captureStartValues(transitionValues)
        fade.captureStartValues(transitionValues)
        captureValues(transitionValues)
        val delayIndex = getDelayOrder(transitionValues.view, false)
        if (delayIndex > 0) transitionValues.values[PROPNAME_DELAY] = delayIndex * SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        super.captureEndValues(transitionValues)
        fade.captureEndValues(transitionValues)
        captureValues(transitionValues)
        val delayIndex = getDelayOrder(transitionValues.view, true)
        if (delayIndex > 0) transitionValues.values[PROPNAME_DELAY] = delayIndex * SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS
    }

    fun setSlideEdge(slideEdge: Int) {
        this.slideEdge = slideEdge
        slideCalculator = when (slideEdge) {
            Gravity.START -> ::calculateStart
            Gravity.END -> ::calculateEnd
            else -> throw IllegalArgumentException("Invalid slide direction")
        }
    }

    override fun onAppear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (endValues == null) return null
        val position = endValues.values[PROPNAME_SCREEN_POSITION] as IntArray
        val endX = view.translationX
        val startX = slideCalculator(sceneRoot, view, distance)
        val slide = TranslationAnimationCreator.createAnimation(view, endValues, position[0], startX, endX, APPEAR_INTERPOLATOR, this) ?: return null
        fade.interpolator = APPEAR_INTERPOLATOR
        return AnimatorSet().apply {
            play(slide).with(fade.onAppear(sceneRoot, view, startValues, endValues))
            (endValues.values[PROPNAME_DELAY] as Long?)?.let { startDelay = it }
        }
    }

    override fun onDisappear(sceneRoot: ViewGroup, view: View, startValues: TransitionValues?, endValues: TransitionValues?): Animator? {
        if (startValues == null) return null
        val position = startValues.values[PROPNAME_SCREEN_POSITION] as IntArray
        val startX = view.translationX
        val endX = slideCalculator(sceneRoot, view, distance)
        val slide = TranslationAnimationCreator.createAnimation(view, startValues, position[0], startX, endX, DISAPPEAR_INTERPOLATOR, this)
            ?: return null // gleiche Start-/Endposition
        fade.interpolator = DISAPPEAR_INTERPOLATOR
        val fadeAnimator = fade.onDisappear(sceneRoot, view, startValues, endValues) ?: return null
        fadeAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                fadeAnimator.removeListener(this)
                view.alpha = 0.0f
            }
        })
        return AnimatorSet().apply {
            play(slide).with(fadeAnimator)
            (startValues.values[PROPNAME_DELAY] as Long?)?.let { startDelay = it }
        }
    }

    override fun addListener(listener: TransitionListener): Transition {
        fade.addListener(listener)
        return super.addListener(listener)
    }

    override fun removeListener(listener: TransitionListener): Transition {
        fade.removeListener(listener)
        return super.removeListener(listener)
    }

    override fun clone(): Transition = (super.clone() as FadeAndShortSlide).also { it.fade = fade.clone() as Visibility }

    override fun setDuration(duration: Long): Transition {
        val scaled = SetupAnimationHelper.applyAnimationTimeScale(duration)
        fade.duration = scaled
        return super.setDuration(scaled)
    }

    fun setDistance(distance: Int) { this.distance = distance }

    private class ViewPositionComparator(val parentForDelay: View, val isLtr: Boolean, val toLeft: Boolean) : Comparator<View> {
        override fun compare(lhs: View, rhs: View): Int {
            val s1 = if (isLtr) relativeLeft(lhs) else relativeRight(lhs)
            val s2 = if (isLtr) relativeLeft(rhs) else relativeRight(rhs)
            if (s1 != s2) return if (toLeft) s1.compareTo(s2) else s2.compareTo(s1)
            return relativeTop(lhs).compareTo(relativeTop(rhs))
        }

        private fun relative(child: View, start: Int, add: (View) -> Int): Int {
            var value = start
            var parent = child.parent
            while (parent is View && parent !== parentForDelay) {
                value += add(parent)
                parent = parent.parent
            }
            return value
        }

        private fun relativeLeft(child: View) = relative(child, child.left) { it.left }
        private fun relativeRight(child: View) = relative(child, child.right) { it.left }
        private fun relativeTop(child: View) = relative(child, child.top) { it.top }
    }

    companion object {
        private val APPEAR_INTERPOLATOR: TimeInterpolator = DecelerateInterpolator()
        private val DISAPPEAR_INTERPOLATOR: TimeInterpolator = AccelerateInterpolator()
        private const val PROPNAME_SCREEN_POSITION = "android_fadeAndShortSlideTransition_screenPosition"
        private const val PROPNAME_DELAY = "propname_delay"
        private const val DEFAULT_DISTANCE = 200

        private fun calculateStart(sceneRoot: ViewGroup, view: View, distance: Int): Float =
            if (sceneRoot.layoutDirection == View.LAYOUT_DIRECTION_RTL) view.translationX + distance else view.translationX - distance

        private fun calculateEnd(sceneRoot: ViewGroup, view: View, distance: Int): Float =
            if (sceneRoot.layoutDirection == View.LAYOUT_DIRECTION_RTL) view.translationX - distance else view.translationX + distance
    }
}
