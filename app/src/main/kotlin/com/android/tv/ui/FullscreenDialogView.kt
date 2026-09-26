package com.android.tv.ui

import android.animation.TimeInterpolator
import android.app.Dialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import android.view.ViewTreeObserver
import android.view.animation.AnimationUtils
import android.widget.FrameLayout
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.dialog.FullscreenDialogFragment

/** Basis für Vollbild-Dialoginhalte mit Ein-/Ausblend-Animation des Hintergrunds. */
open class FullscreenDialogView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), FullscreenDialogFragment.DialogView {

    protected var activity: MainActivity? = null
        private set
    protected var dialog: Dialog? = null
        private set
    private var skipEnterAlphaAnimation = false
    private var skipExitAlphaAnimation = false
    private val linearOutSlowIn: TimeInterpolator =
        AnimationUtils.loadInterpolator(context, android.R.interpolator.linear_out_slow_in)
    private val fastOutLinearIn: TimeInterpolator =
        AnimationUtils.loadInterpolator(context, android.R.interpolator.fast_out_linear_in)

    init {
        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                viewTreeObserver.removeOnGlobalLayoutListener(this)
                startEnterAnimation()
            }
        })
    }

    /** Schließt den Dialog nach der Ausblend-Animation (die Unterklasse ruft den Runnable). */
    protected fun dismiss() = startExitAnimation { dialog?.dismiss() }

    override fun initialize(activity: MainActivity, dialog: Dialog) {
        this.activity = activity
        this.dialog = dialog
    }

    override fun onBackPressed() {}
    override fun onDestroy() {}

    /** Wechsel zu einer anderen Dialog-View ohne erneutes Ein-/Ausblenden des Hintergrunds. */
    fun transitionTo(v: FullscreenDialogView) {
        skipExitAlphaAnimation = true
        v.skipEnterAlphaAnimation = true
        v.initialize(activity!!, dialog!!)
        startExitAnimation {
            Handler(Looper.getMainLooper()).postDelayed({
                v.initialize(activity!!, dialog!!)
                dialog!!.setContentView(v)
            }, TRANSITION_INTERVAL_MS)
        }
    }

    protected open fun onStartEnterAnimation(interpolator: TimeInterpolator, duration: Long) {}
    protected open fun onStartExitAnimation(interpolator: TimeInterpolator, duration: Long, onAnimationEnded: Runnable) {}

    private fun startEnterAnimation() {
        val backgroundView = findViewById<View>(R.id.background)
        if (!skipEnterAlphaAnimation) {
            backgroundView.alpha = 0f
            backgroundView.animate().alpha(1.0f).setInterpolator(linearOutSlowIn)
                .setDuration(FADE_IN_DURATION_MS).withLayer().start()
        }
        onStartEnterAnimation(linearOutSlowIn, FADE_IN_DURATION_MS)
    }

    private fun startExitAnimation(onAnimationEnded: Runnable) {
        val backgroundView = findViewById<View>(R.id.background)
        if (!skipExitAlphaAnimation) {
            backgroundView.animate().alpha(0.0f).setInterpolator(fastOutLinearIn)
                .setDuration(FADE_OUT_DURATION_MS).withLayer().start()
        }
        onStartExitAnimation(fastOutLinearIn, FADE_OUT_DURATION_MS, onAnimationEnded)
    }

    companion object {
        private const val FADE_IN_DURATION_MS = 400L
        private const val FADE_OUT_DURATION_MS = 250L
        private const val TRANSITION_INTERVAL_MS = 300L
    }
}
