package com.android.tv.common.util

import android.os.SystemClock

/** Zeitquelle (austauschbar für Tests). */
interface Clock {
    fun currentTimeMillis(): Long
    fun elapsedRealtime(): Long
    fun uptimeMillis(): Long
    fun sleep(ms: Long)

    companion object {
        @JvmField
        val SYSTEM: Clock = object : Clock {
            override fun currentTimeMillis() = System.currentTimeMillis()
            override fun elapsedRealtime() = SystemClock.elapsedRealtime()
            override fun uptimeMillis() = SystemClock.uptimeMillis()
            override fun sleep(ms: Long) = SystemClock.sleep(ms)
        }
    }
}
