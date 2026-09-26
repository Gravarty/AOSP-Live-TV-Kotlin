package com.android.tv.data

import com.android.tv.data.api.Program

fun interface OnCurrentProgramUpdatedListener {
    /** Aktuelle Sendung eines Kanals geändert (null = keine). */
    fun onCurrentProgramUpdated(channelId: Long, program: Program?)
}
