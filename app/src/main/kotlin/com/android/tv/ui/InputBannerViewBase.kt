package com.android.tv.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.LinearLayout
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.StreamInfo

/** Basis des Input-Banners (HDMI usw.); blendet sich nach select_input_show_duration aus. */
abstract class InputBannerViewBase @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : LinearLayout(context, attrs, defStyle), TvTransitionManager.TransitionLayout {

    protected val showDurationMillis = context.resources.getInteger(R.integer.select_input_show_duration).toLong()

    protected val hideRunnable = Runnable {
        (context as MainActivity).overlayManager.hideOverlays(
            TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_DIALOG or TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_SIDE_PANELS or
                TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_PROGRAM_GUIDE or TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_MENU or
                TvOverlayManager.FLAG_HIDE_OVERLAYS_KEEP_FRAGMENT)
    }

    abstract fun updateLabel()

    override fun onEnterAction(fromEmptyScene: Boolean) {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, showDurationMillis)
    }

    override fun onExitAction() {
        removeCallbacks(hideRunnable)
    }

    open fun onStreamInfoUpdated(info: StreamInfo) {}
}
