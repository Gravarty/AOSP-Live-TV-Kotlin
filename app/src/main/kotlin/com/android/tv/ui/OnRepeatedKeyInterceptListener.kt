package com.android.tv.ui

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import androidx.leanback.widget.BaseGridView
import androidx.leanback.widget.VerticalGridView

/**
 * Beschleunigt das Scrollen bei gehaltener Hoch/Runter-Taste: ab 2 s wird 1 Eintrag, ab 5 s
 * werden 4 Einträge zusätzlich übersprungen.
 */
open class OnRepeatedKeyInterceptListener(private val view: VerticalGridView) : BaseGridView.OnKeyInterceptListener {

    private var direction = 0
    var isFocusAccelerated = false
        private set
    private var repeatedKeyInterval = 0L

    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_MOVE_FOCUS) {
            val focused = view.findFocus()
            val v = focused?.focusSearch(direction)
            if (v != null && v !== focused) v.requestFocus(direction)
        }
        true
    }

    override fun onInterceptKeyEvent(event: KeyEvent): Boolean {
        handler.removeMessages(MSG_MOVE_FOCUS)
        if (event.keyCode != KeyEvent.KEYCODE_DPAD_UP && event.keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false
        val duration = event.eventTime - event.downTime
        if (duration < THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[0] || event.isCanceled) {
            isFocusAccelerated = false
            return false
        }
        direction = if (event.keyCode == KeyEvent.KEYCODE_DPAD_UP) View.FOCUS_UP else View.FOCUS_DOWN
        var skippedViewCount = MAX_SKIPPED_VIEW_COUNT[0]
        for (i in 1 until THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS.size) {
            if (THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS[i] < duration) skippedViewCount = MAX_SKIPPED_VIEW_COUNT[i] else break
        }
        if (event.action == KeyEvent.ACTION_DOWN) {
            // Bugfix: Division durch 0 bei repeatCount 0 vermeiden
            repeatedKeyInterval = duration / maxOf(1, event.repeatCount)
            isFocusAccelerated = true
        } else {
            isFocusAccelerated = false
        }
        for (i in 0 until skippedViewCount) {
            handler.sendEmptyMessageDelayed(MSG_MOVE_FOCUS, repeatedKeyInterval * i / (skippedViewCount + 1))
        }
        return false
    }

    companion object {
        private val THRESHOLD_FAST_FOCUS_CHANGE_TIME_MS = longArrayOf(2000, 5000)
        private val MAX_SKIPPED_VIEW_COUNT = intArrayOf(1, 4)
        private const val MSG_MOVE_FOCUS = 1000
    }
}
