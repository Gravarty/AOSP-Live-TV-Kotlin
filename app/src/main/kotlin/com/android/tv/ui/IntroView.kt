package com.android.tv.ui

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.drawable.AnimationDrawable
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.View
import com.android.tv.R
import com.android.tv.menu.Menu

/** Willkommens-Dialog beim ersten Start; OK öffnet anschließend das Menü. */
class IntroView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : FullscreenDialogView(context, attrs, defStyle) {

    private var rippleDrawable: AnimationDrawable? = null
    private var openMenu = false

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_UP &&
            (event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER)
        ) {
            dismiss()
            openMenu = true
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onBackPressed() = dismiss()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        rippleDrawable = findViewById<View>(R.id.welcome_ripple).background as AnimationDrawable
        rippleDrawable?.start()
    }

    override fun onDetachedFromWindow() {
        rippleDrawable?.stop()
        super.onDetachedFromWindow()
    }

    override fun onDestroy() {
        if (openMenu) activity?.overlayManager?.showMenu(Menu.REASON_GUIDE)
    }

    override fun onStartEnterAnimation(interpolator: TimeInterpolator, duration: Long) {
        val v = findViewById<View>(R.id.container)
        v.alpha = 0f
        v.animate().alpha(1.0f).setInterpolator(interpolator).setDuration(duration).withLayer().start()
    }

    override fun onStartExitAnimation(interpolator: TimeInterpolator, duration: Long, onAnimationEnded: Runnable) {
        findViewById<View>(R.id.container).animate().alpha(0.0f).setInterpolator(interpolator)
            .setDuration(duration).withLayer().withEndAction(onAnimationEnded).start()
    }
}
