package com.android.tv.ui.api

/** Gemeinsame Wiedergabe-API von TunableTvView und DvrTvView. */
interface TunableTvViewPlayingApi {
    val isPlaying: Boolean
    fun setStreamVolume(volume: Float)
    fun setTimeShiftListener(listener: TimeShiftListener?)
    val isTimeShiftAvailable: Boolean
    fun timeShiftPlay()
    fun timeShiftPause()
    fun timeShiftRewind(speed: Int)
    fun timeShiftFastForward(speed: Int)
    fun timeShiftSeekTo(timeMs: Long)
    fun timeShiftGetCurrentPositionMs(): Long

    abstract class TimeShiftListener {
        abstract fun onAvailabilityChanged()
        abstract fun onRecordStartTimeChanged(recordStartTimeMs: Long)
    }
}
