package com.android.tv.common.ui.setup

import android.os.Bundle
import android.transition.Transition
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.android.tv.common.ui.setup.animation.FadeAndShortSlide
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper

/** Basis der Einrichtungs-Fragmente mit Schiebe-/Blend-Übergängen (AndroidX). */
abstract class SetupFragment : Fragment() {

    protected var isEnterTransitionRunning = false
        private set

    private val transitionListener = object : Transition.TransitionListener {
        override fun onTransitionStart(transition: Transition) { isEnterTransitionRunning = true }
        override fun onTransitionEnd(transition: Transition) {
            isEnterTransitionRunning = false
            onEnterTransitionEnd()
        }
        override fun onTransitionCancel(transition: Transition) {}
        override fun onTransitionPause(transition: Transition) {}
        override fun onTransitionResume(transition: Transition) {}
    }

    init {
        allowEnterTransitionOverlap = false
        allowReturnTransitionOverlap = false
        enableFragmentTransition(FRAGMENT_ENTER_TRANSITION or FRAGMENT_EXIT_TRANSITION or
            FRAGMENT_REENTER_TRANSITION or FRAGMENT_RETURN_TRANSITION)
    }

    protected open fun onEnterTransitionEnd() {}

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(getLayoutResourceId(), container, false).also { it.requestFocus() }

    protected abstract fun getLayoutResourceId(): Int

    protected fun setOnClickAction(view: View, category: String, actionId: Int) =
        view.setOnClickListener { onActionClick(category, actionId) }

    protected open fun onActionClick(category: String, actionId: Int): Boolean =
        SetupActionHelper.onActionClick(this, category, actionId)

    protected fun onActionClick(category: String, actionId: Int, params: Bundle?): Boolean =
        SetupActionHelper.onActionClick(this, category, actionId, params)

    override fun setEnterTransition(transition: Any?) {
        super.setEnterTransition(transition)
        (transition as? Transition)?.addListener(transitionListener)
    }

    override fun setReenterTransition(transition: Any?) {
        super.setReenterTransition(transition)
        (transition as? Transition)?.addListener(transitionListener)
    }

    fun enableFragmentTransition(mask: Int) {
        enterTransition = if (mask and FRAGMENT_ENTER_TRANSITION == 0) null else createTransition(Gravity.END)
        exitTransition = if (mask and FRAGMENT_EXIT_TRANSITION == 0) null else createTransition(Gravity.START)
        reenterTransition = if (mask and FRAGMENT_REENTER_TRANSITION == 0) null else createTransition(Gravity.START)
        returnTransition = if (mask and FRAGMENT_RETURN_TRANSITION == 0) null else createTransition(Gravity.END)
    }

    fun setFragmentTransition(transitionType: Int, slideEdge: Int) {
        when (transitionType) {
            FRAGMENT_ENTER_TRANSITION -> enterTransition = createTransition(slideEdge)
            FRAGMENT_EXIT_TRANSITION -> exitTransition = createTransition(slideEdge)
            FRAGMENT_REENTER_TRANSITION -> reenterTransition = createTransition(slideEdge)
            FRAGMENT_RETURN_TRANSITION -> returnTransition = createTransition(slideEdge)
        }
    }

    private fun createTransition(slideEdge: Int): Transition = SetupAnimationHelper.TransitionBuilder()
        .setSlideEdge(slideEdge)
        .setParentIdsForDelay(getParentIdsForDelay())
        .setExcludeIds(getExcludedTargetIds())
        .build()

    fun setShortDistance(mask: Int) {
        fun shorten(t: Any?) { (t as? FadeAndShortSlide)?.let { SetupAnimationHelper.setShortDistance(it) } }
        if (mask and FRAGMENT_ENTER_TRANSITION != 0) shorten(enterTransition)
        if (mask and FRAGMENT_EXIT_TRANSITION != 0) shorten(exitTransition)
        if (mask and FRAGMENT_REENTER_TRANSITION != 0) shorten(reenterTransition)
        if (mask and FRAGMENT_RETURN_TRANSITION != 0) shorten(returnTransition)
    }

    protected open fun getParentIdsForDelay(): IntArray? = null
    protected open fun getExcludedTargetIds(): IntArray? = null
    open fun getSharedElementIds(): IntArray? = null

    companion object {
        const val FRAGMENT_ENTER_TRANSITION = 0x01
        const val FRAGMENT_EXIT_TRANSITION = FRAGMENT_ENTER_TRANSITION shl 1
        const val FRAGMENT_REENTER_TRANSITION = FRAGMENT_ENTER_TRANSITION shl 2
        const val FRAGMENT_RETURN_TRANSITION = FRAGMENT_ENTER_TRANSITION shl 3
    }
}
