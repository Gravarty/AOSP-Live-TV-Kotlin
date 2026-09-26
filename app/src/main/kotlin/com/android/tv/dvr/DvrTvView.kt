package com.android.tv.dvr

import android.content.Context
import android.media.PlaybackParams
import android.media.session.PlaybackState
import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.net.Uri
import com.android.tv.InputSessionManager
import com.android.tv.TvSingletons
import com.android.tv.common.compat.TvViewCompat.TvInputCallbackCompat
import com.android.tv.dvr.ui.playback.DvrPlayer
import com.android.tv.ui.AppLayerTvView
import com.android.tv.ui.api.TunableTvViewPlayingApi

/** TvView für die Wiedergabe von Aufnahmen (Timeshift-API des Inputs). */
class DvrTvView(context: Context, private val tvView: AppLayerTvView, private var dvrPlayer: DvrPlayer?) : TunableTvViewPlayingApi {

    private var inputId: String? = null
    private var recordedProgramUri: Uri? = null
    private var callback: TvInputCallbackCompat? = null
    private var inputSessionManager: InputSessionManager? = TvSingletons.getSingletons(context).getInputSessionManager()
    private var session: InputSessionManager.TvViewSession? = null

    override val isPlaying: Boolean get() = dvrPlayer?.playbackState == PlaybackState.STATE_PLAYING
    override fun setStreamVolume(volume: Float) = tvView.setStreamVolume(volume)
    override fun setTimeShiftListener(listener: TunableTvViewPlayingApi.TimeShiftListener?) {}
    override val isTimeShiftAvailable: Boolean get() = true

    override fun timeShiftPlay() {
        val id = inputId
        val uri = recordedProgramUri
        if (id != null && uri != null) tvView.timeShiftPlay(id, uri)
    }

    fun timeShiftPlay(inputId: String, recordedProgramUri: Uri) {
        this.inputId = inputId
        this.recordedProgramUri = recordedProgramUri
        session?.timeShiftPlay(inputId, recordedProgramUri)
    }

    override fun timeShiftPause() = tvView.timeShiftPause()
    override fun timeShiftRewind(speed: Int) = tvView.timeShiftSetPlaybackParams(PlaybackParams().setSpeed(-speed.toFloat()))
    override fun timeShiftFastForward(speed: Int) = tvView.timeShiftSetPlaybackParams(PlaybackParams().setSpeed(speed.toFloat()))
    override fun timeShiftSeekTo(timeMs: Long) = tvView.timeShiftSeekTo(timeMs)
    override fun timeShiftGetCurrentPositionMs(): Long = dvrPlayer?.playbackPosition ?: 0L

    fun setCaptionEnabled(enabled: Boolean) = tvView.setCaptionEnabled(enabled)
    fun timeShiftResume() = tvView.timeShiftResume()
    fun reset() { session?.reset() }
    fun getTracks(type: Int): List<TvTrackInfo>? = tvView.getTracks(type)
    fun selectTrack(type: Int, trackId: String?) = tvView.selectTrack(type, trackId)
    fun timeShiftSetPlaybackParams(params: PlaybackParams) = tvView.timeShiftSetPlaybackParams(params)
    fun setTimeShiftPositionCallback(callback: TvView.TimeShiftPositionCallback?) = tvView.setTimeShiftPositionCallback(callback)

    fun setCallback(callback: TvInputCallbackCompat?) {
        this.callback = callback
        tvView.setCallback(callback)
    }

    fun init() {
        session = inputSessionManager?.createTvViewSession(tvView, this, callback ?: TvInputCallbackCompat())
    }

    fun release() {
        session?.let { inputSessionManager?.releaseTvViewSession(it) }
        inputSessionManager = null
        dvrPlayer = null
    }
}
