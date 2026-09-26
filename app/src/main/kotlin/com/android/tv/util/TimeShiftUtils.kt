package com.android.tv.util

import java.util.concurrent.TimeUnit

/** Timeshift-Geschwindigkeiten: kurze Sendungen (≤ 46 min) 2/4/12/48x, lange 2/8/32/128x. */
object TimeShiftUtils {
    private val SHORT_PROGRAM_THRESHOLD_MILLIS = TimeUnit.MINUTES.toMillis(46)
    private val SHORT_PROGRAM_SPEED_FACTORS = intArrayOf(2, 4, 12, 48)
    private val LONG_PROGRAM_SPEED_FACTORS = intArrayOf(2, 8, 32, 128)
    val MAX_SPEED_LEVEL = SHORT_PROGRAM_SPEED_FACTORS.size - 1

    @JvmStatic
    fun getPlaybackSpeed(speedLevel: Int, programDurationMillis: Long): Int =
        (if (programDurationMillis > SHORT_PROGRAM_THRESHOLD_MILLIS) LONG_PROGRAM_SPEED_FACTORS
        else SHORT_PROGRAM_SPEED_FACTORS)[speedLevel]

    @JvmStatic
    fun getMaxPlaybackSpeed(programDurationMillis: Long): Int = getPlaybackSpeed(MAX_SPEED_LEVEL, programDurationMillis)
}
