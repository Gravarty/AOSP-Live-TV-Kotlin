package com.android.tv.ui.sidepanel

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.android.tv.R
import com.android.tv.ui.hideable.AutoHideScheduler

/** Stapel der Seitenleisten mit Ein-/Ausblend-Animation und automatischem Ausblenden. */
class SideFragmentManager(
    activity: FragmentActivity,
    private val preShowRunnable: Runnable?,
    private val postHideRunnable: Runnable?,
) : AccessibilityStateChangeListener {

    private val activity = activity
    private val fragmentManager: FragmentManager = activity.supportFragmentManager
    private var showOnGlobalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    var count = 0
        private set
    private val panel: View = activity.findViewById(R.id.side_panel)
    private val showAnimator: Animator = AnimatorInflater.loadAnimator(activity, R.animator.side_panel_enter).apply { setTarget(panel) }
    private val hideAnimator: Animator = AnimatorInflater.loadAnimator(activity, R.animator.side_panel_exit).apply {
        setTarget(panel)
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = hideAllInternal()
        })
    }
    private val autoHideScheduler = AutoHideScheduler(activity) { hideAll(true) }
    private val showDurationMillis = activity.resources.getInteger(R.integer.side_panel_show_duration).toLong()

    val isActive: Boolean get() = count != 0 && !isHiding
    val isHiding: Boolean get() = hideAnimator.isStarted

    @JvmOverloads
    fun show(sideFragment: SideFragment<*>, showEnterAnimation: Boolean = true) {
        if (isHiding) hideAnimator.end()
        val isFirst = count == 0
        val ft = fragmentManager.beginTransaction()
        if (!isFirst) {
            ft.setCustomAnimations(
                if (showEnterAnimation) R.animator.side_panel_fragment_enter else 0,
                R.animator.side_panel_fragment_exit,
                R.animator.side_panel_fragment_pop_enter,
                R.animator.side_panel_fragment_pop_exit)
        }
        ft.replace(R.id.side_fragment_container, sideFragment).addToBackStack(count.toString()).commit()
        count++
        if (isFirst) {
            // Einblenden erst nach dem Layout (Höhe bekannt)
            panel.visibility = View.VISIBLE
            val listener = object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    panel.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    showOnGlobalLayoutListener = null
                    preShowRunnable?.run()
                    showAnimator.start()
                }
            }
            showOnGlobalLayoutListener = listener
            panel.viewTreeObserver.addOnGlobalLayoutListener(listener)
        }
        scheduleHideAll()
    }

    fun popSideFragment() {
        if (!isActive) return
        if (count == 1) {
            hideAll(true)
            return
        }
        fragmentManager.popBackStack()
        count--
    }

    fun hideAll(withAnimation: Boolean) {
        if (showAnimator.isStarted) showAnimator.end()
        showOnGlobalLayoutListener?.let {
            // Einblenden stand noch aus: Vorbereitung trotzdem ausführen
            panel.viewTreeObserver.removeOnGlobalLayoutListener(it)
            showOnGlobalLayoutListener = null
            preShowRunnable?.run()
        }
        if (withAnimation) {
            if (!isHiding) hideAnimator.start()
            return
        }
        if (isHiding) {
            hideAnimator.end()
            return
        }
        hideAllInternal()
    }

    private fun hideAllInternal() {
        autoHideScheduler.cancel()
        if (count == 0) return
        panel.visibility = View.GONE
        fragmentManager.popBackStack(FIRST_BACKSTACK_RECORD_NAME, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        count = 0
        postHideRunnable?.run()
    }

    /** Zeigt die (verborgene) Leiste wieder, ohne den Stapel zu ändern. */
    fun showSidePanel(withAnimation: Boolean) {
        if (count == 0) return
        panel.visibility = View.VISIBLE
        if (withAnimation) showAnimator.start()
        scheduleHideAll()
    }

    /** Verbirgt die Leiste, behält aber den Stapel (z. B. während einer Einrichtung). */
    fun hideSidePanel(withAnimation: Boolean) {
        autoHideScheduler.cancel()
        if (withAnimation) {
            AnimatorInflater.loadAnimator(activity, R.animator.side_panel_exit).apply {
                setTarget(panel)
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { panel.visibility = View.GONE }
                })
                start()
            }
        } else {
            panel.visibility = View.GONE
        }
    }

    val isSidePanelVisible: Boolean get() = panel.visibility == View.VISIBLE

    fun scheduleHideAll() = autoHideScheduler.schedule(showDurationMillis)

    fun isHideKeyForCurrentPanel(keyCode: Int): Boolean {
        if (!isActive) return false
        val current = fragmentManager.findFragmentById(R.id.side_fragment_container) as? SideFragment<*>
        return current != null && current.isHideKeyForThisPanel(keyCode)
    }

    override fun onAccessibilityStateChanged(enabled: Boolean) = autoHideScheduler.onAccessibilityStateChanged(enabled)

    companion object {
        private const val FIRST_BACKSTACK_RECORD_NAME = "0"
    }
}
