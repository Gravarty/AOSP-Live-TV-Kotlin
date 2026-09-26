package com.android.tv.recommendation

import com.android.tv.data.api.Program

/** Eine gesehene Sendung mit Sehzeitraum. */
class WatchedProgram(val program: Program, val watchStartTimeMs: Long, val watchEndTimeMs: Long) {
    val watchedDurationMs: Long get() = watchEndTimeMs - watchStartTimeMs
}
