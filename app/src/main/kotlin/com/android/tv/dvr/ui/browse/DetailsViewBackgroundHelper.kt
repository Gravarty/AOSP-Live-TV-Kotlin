package com.android.tv.dvr.ui.browse

import android.app.Activity
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.leanback.app.BackgroundManager

/** Setzt den Hintergrund der Detailansichten über den [BackgroundManager]. */
class DetailsViewBackgroundHelper(activity: Activity) {
    private val backgroundManager: BackgroundManager = BackgroundManager.getInstance(activity).apply {
        attach(activity.window)
        isAutoReleaseOnStop = false
    }
    private var runnable: LoadBackgroundRunnable? = null
    // Handler() ist veraltet → expliziter Main-Looper.
    private val handler = Handler(Looper.getMainLooper())

    private inner class LoadBackgroundRunnable(private val background: Drawable?) : Runnable {
        override fun run() {
            if (!backgroundManager.isAttached) return
            if (background is BitmapDrawable) {
                backgroundManager.setBitmap(background.bitmap)
            }
            runnable = null
        }
    }

    /** Setzt das Bild als Hintergrund (verzögert, um teures Laden bei schnellen Wechseln zu vermeiden). */
    fun setBackground(background: Drawable?) {
        runnable?.let { handler.removeCallbacks(it) }
        val r = LoadBackgroundRunnable(background)
        runnable = r
        handler.postDelayed(r, SET_BACKGROUND_DELAY_MS)
    }

    /** Setzt die Hintergrundfarbe. */
    fun setBackgroundColor(color: Int) {
        if (backgroundManager.isAttached) backgroundManager.color = color
    }

    /** Setzt die Abdunklung (Scrim). */
    fun setScrim(color: Int) {
        if (backgroundManager.isAttached) backgroundManager.setDimLayer(ColorDrawable(color))
    }

    companion object {
        // Verzögerung, um teures Laden bei mehreren schnell gesetzten Hintergründen zu vermeiden.
        private const val SET_BACKGROUND_DELAY_MS = 100L
    }
}
