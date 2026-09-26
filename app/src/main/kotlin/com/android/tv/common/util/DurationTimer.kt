package com.android.tv.common.util

import android.os.SystemClock

/** Misst Dauern über elapsedRealtime. */
class DurationTimer {
    private var startTimeMs = TIME_NOT_SET

    val isRunning: Boolean get() = startTimeMs != TIME_NOT_SET

    fun start() { startTimeMs = SystemClock.elapsedRealtime() }

    val duration: Long get() = if (isRunning) SystemClock.elapsedRealtime() - startTimeMs else TIME_NOT_SET

    /** Stoppt und liefert die bisherige Dauer. */
    fun reset(): Long = duration.also { startTimeMs = TIME_NOT_SET }

    companion object {
        const val TIME_NOT_SET = -1L
    }
}
