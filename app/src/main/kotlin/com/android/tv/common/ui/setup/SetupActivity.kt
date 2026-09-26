package com.android.tv.common.ui.setup

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.TransitionInflater
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentTransaction
import com.android.tv.R
import com.android.tv.common.ui.setup.animation.SetupAnimationHelper

/** Basis der Einrichtungs-Activities: Fragment-Wechsel mit geteilten Elementen, verzögerte Aktionen. */
abstract class SetupActivity : FragmentActivity(), OnActionClickListener {
    private var showInitialFragment = true
    private var fragmentTransitionDuration = 0L
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_EXECUTE_ACTION) (msg.obj as Runnable).run()
        true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SetupAnimationHelper.initialize(this)
        setContentView(R.layout.activity_setup)
        fragmentTransitionDuration = resources.getInteger(R.integer.setup_fragment_transition_duration).toLong()
        if (savedInstanceState == null) showInitialFragment() else showInitialFragment = false
    }

    /** Erstes Fragment (null = später über showInitialFragment()). */
    protected abstract fun onCreateInitialFragment(): Fragment?

    protected fun showInitialFragment() {
        if (!showInitialFragment) return
        val fragment = onCreateInitialFragment() ?: return
        showFragment(fragment, false)
        showInitialFragment = false
    }

    /** Fragment zeigen; geteilte Elemente von SetupFragment animiert übernehmen. */
    protected fun showFragment(fragment: Fragment, addToBackStack: Boolean): FragmentTransaction {
        val ft = supportFragmentManager.beginTransaction()
        if (fragment is SetupFragment) {
            val sharedElements = fragment.getSharedElementIds()
            if (sharedElements != null && sharedElements.isNotEmpty()) {
                val sharedTransition = TransitionInflater.from(this).inflateTransition(R.transition.transition_action_background)
                sharedTransition.duration = getSharedElementTransitionDuration()
                SetupAnimationHelper.applyAnimationTimeScale(sharedTransition)
                fragment.sharedElementEnterTransition = sharedTransition
                fragment.sharedElementReturnTransition = sharedTransition
                for (id in sharedElements) findViewById<android.view.View>(id)?.let { ft.addSharedElement(it, it.transitionName) }
            }
        }
        val tag = fragment.javaClass.canonicalName
        if (addToBackStack) ft.addToBackStack(tag)
        ft.replace(R.id.fragment_container, fragment, tag).commit()
        return ft
    }

    /** Während eine verzögerte Aktion aussteht, werden weitere Aktionen ignoriert. */
    override fun onActionClick(category: String, id: Int, params: Bundle?): Boolean {
        if (handler.hasMessages(MSG_EXECUTE_ACTION)) return false
        return executeAction(category, id, params)
    }

    protected fun executeActionWithDelay(action: Runnable, delayMs: Int) {
        handler.sendMessageDelayed(handler.obtainMessage(MSG_EXECUTE_ACTION, action), delayMs.toLong())
    }

    protected open fun executeAction(category: String, actionId: Int, params: Bundle?): Boolean = false

    private fun getSharedElementTransitionDuration(): Long =
        (fragmentTransitionDuration + SetupAnimationHelper.DELAY_BETWEEN_SIBLINGS_MS) * 2

    companion object {
        private const val MSG_EXECUTE_ACTION = 1
    }
}
