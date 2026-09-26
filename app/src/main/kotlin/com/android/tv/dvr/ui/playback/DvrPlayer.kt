package com.android.tv.dvr.ui.playback

import android.content.Context
import android.media.PlaybackParams
import android.media.session.PlaybackState
import android.media.tv.TvContentRating
import android.media.tv.TvInputManager
import android.media.tv.TvTrackInfo
import android.media.tv.TvView
import android.text.TextUtils
import android.util.Log
import com.android.tv.common.compat.TvViewCompat.TvInputCallbackCompat
import com.android.tv.dvr.DvrTvView
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.ui.AppLayerTvView
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/** Player für Aufnahmen (über die Timeshift-API des Inputs). */
class DvrPlayer(tvView: AppLayerTvView, context: Context) {

    /** Aktuell abgespielte Aufnahme. */
    var program: RecordedProgram? = null
        private set
    private var initialSeekPositionMs = 0L

    /** Die DVR-TvView. */
    val view: DvrTvView = DvrTvView(context, tvView, this)
    private var callback: DvrPlayerCallback = DvrPlayerCallback()
    private var onAspectRatioChangedListener: OnAspectRatioChangedListener? = null
    private var onContentBlockedListener: OnContentBlockedListener? = null
    private var onTracksAvailabilityChangedListener: OnTracksAvailabilityChangedListener? = null
    private var onAudioTrackSelectedListener: OnTrackSelectedListener? = null
    private var onSubtitleTrackSelectedListener: OnTrackSelectedListener? = null
    private var selectedAudioTrackId: String? = null
    private var selectedSubtitleTrackId: String? = null
    private var aspectRatio = Float.NaN

    /** Wiedergabezustand gemäß [PlaybackState]. */
    var playbackState = PlaybackState.STATE_NONE
        private set
    private var timeShiftCurrentPositionMs = 0L
    private var pauseOnPrepared = false
    private var hasClosedCaption = false
    private var hasMultiAudio = false
    private val playbackParams = PlaybackParams()
    private val emptyCallback = DvrPlayerCallback()
    private var startPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME
    private var timeShiftPlayAvailable = false

    /** Callback des DVR-Players. */
    open class DvrPlayerCallback {
        /** Position geändert (etwa sekündlich, abhängig vom TvInputService). */
        open fun onPlaybackPositionChanged(positionMs: Long) {}
        /** Zustand oder Geschwindigkeit geändert. */
        open fun onPlaybackStateChanged(playbackState: Int, playbackSpeed: Int) {}
        /** Wiedergabe hat das Ende erreicht. */
        open fun onPlaybackEnded() {}
        /** Wiedergabe läuft wieder normal (Ende von Vor-/Rücklauf). */
        open fun onPlaybackResume() {}
    }

    /** Listener für geänderte Seitenverhältnisse. */
    fun interface OnAspectRatioChangedListener {
        /** [videoAspectRatio] 0 steht für unbekannt. */
        fun onAspectRatioChanged(videoAspectRatio: Float)
    }

    /** Listener für gesperrte Inhalte. */
    fun interface OnContentBlockedListener {
        fun onContentBlocked(rating: TvContentRating)
    }

    /** Listener für geänderte Verfügbarkeit von Untertitel-/Tonspuren. */
    fun interface OnTracksAvailabilityChangedListener {
        fun onTracksAvailabilityChanged(hasClosedCaption: Boolean, hasMultiAudio: Boolean)
    }

    /** Listener für gewählte Spuren. */
    fun interface OnTrackSelectedListener {
        fun onTrackSelected(selectedTrackId: String?)
    }

    init {
        view.setCaptionEnabled(true)
        playbackParams.setSpeed(1.0f)
        setTvViewCallbacks()
        setCallback(null)
        view.init()
    }

    /**
     * Bereitet die Wiedergabe vor.
     * @param doPlay ob nach dem Vorbereiten direkt abgespielt wird.
     */
    @Throws(IllegalStateException::class)
    fun prepare(doPlay: Boolean) {
        if (DEBUG) Log.d(TAG, "prepare()")
        val program = checkNotNull(program) { "Recorded program not set" }
        check(playbackState == PlaybackState.STATE_NONE) { "Playback is already prepared" }
        // inputId ist bei gültigen Aufnahmen immer gesetzt
        view.timeShiftPlay(program.inputId.orEmpty(), program.uri)
        playbackState = PlaybackState.STATE_CONNECTING
        pauseOnPrepared = !doPlay
        callback.onPlaybackStateChanged(playbackState, 1)
    }

    /** Setzt die Wiedergabe fort. */
    @Throws(IllegalStateException::class)
    fun play() {
        if (DEBUG) Log.d(TAG, "play()")
        check(isPlaybackPrepared()) { "Recorded program not set or video not ready yet" }
        when (playbackState) {
            PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING -> setPlaybackSpeed(1)
            else -> view.timeShiftResume()
        }
        playbackState = PlaybackState.STATE_PLAYING
        callback.onPlaybackStateChanged(playbackState, 1)
    }

    /** Pausiert die Wiedergabe. */
    @Throws(IllegalStateException::class)
    fun pause() {
        if (DEBUG) Log.d(TAG, "pause()")
        check(isPlaybackPrepared()) { "Recorded program not set or playback not started yet" }
        when (playbackState) {
            PlaybackState.STATE_FAST_FORWARDING, PlaybackState.STATE_REWINDING, PlaybackState.STATE_PLAYING -> {
                if (playbackState != PlaybackState.STATE_PLAYING) setPlaybackSpeed(1)
                view.timeShiftPause()
                playbackState = PlaybackState.STATE_PAUSED
            }
            else -> {}
        }
        callback.onPlaybackStateChanged(playbackState, 1)
    }

    /** Vorlauf mit [speed], höchstens [MAX_FAST_FORWARD_SPEED]. */
    @Throws(IllegalStateException::class)
    fun fastForward(speed: Int) {
        if (DEBUG) Log.d(TAG, "fastForward()")
        check(isPlaybackPrepared()) { "Recorded program not set or playback not started yet" }
        require(speed > 0) { "Speed cannot be negative or 0" }
        if (timeShiftCurrentPositionMs >= program!!.durationMillis - SEEK_POSITION_MARGIN_MS) return
        val newSpeed = min(speed, MAX_FAST_FORWARD_SPEED)
        if (DEBUG) Log.d(TAG, "Let's play with speed: $newSpeed")
        setPlaybackSpeed(newSpeed)
        playbackState = PlaybackState.STATE_FAST_FORWARDING
        callback.onPlaybackStateChanged(playbackState, newSpeed)
    }

    /** Rücklauf mit [speed], höchstens [MAX_REWIND_SPEED]. */
    @Throws(IllegalStateException::class)
    fun rewind(speed: Int) {
        if (DEBUG) Log.d(TAG, "rewind()")
        check(isPlaybackPrepared()) { "Recorded program not set or playback not started yet" }
        require(speed > 0) { "Speed cannot be negative or 0" }
        if (timeShiftCurrentPositionMs <= REWIND_POSITION_MARGIN_MS) return
        val newSpeed = min(speed, MAX_REWIND_SPEED)
        if (DEBUG) Log.d(TAG, "Let's play with speed: $newSpeed")
        setPlaybackSpeed(-newSpeed)
        playbackState = PlaybackState.STATE_REWINDING
        callback.onPlaybackStateChanged(playbackState, newSpeed)
    }

    /** Springt an die angegebene Position. */
    @Throws(IllegalStateException::class)
    fun seekTo(positionMs: Long) {
        if (DEBUG) Log.d(TAG, "seekTo()")
        check(isPlaybackPrepared()) { "Recorded program not set or playback not started yet" }
        if (program == null || playbackState == PlaybackState.STATE_NONE) return
        val realPositionMs = getRealSeekPosition(positionMs, SEEK_POSITION_MARGIN_MS)
        if (DEBUG) Log.d(TAG, "Now: $playbackPosition, shift to: $realPositionMs")
        view.timeShiftSeekTo(realPositionMs + startPositionMs)
        if (playbackState == PlaybackState.STATE_FAST_FORWARDING || playbackState == PlaybackState.STATE_REWINDING) {
            playbackState = PlaybackState.STATE_PLAYING
            view.timeShiftResume()
            callback.onPlaybackStateChanged(playbackState, 1)
        }
    }

    /** Setzt die Wiedergabe zurück. */
    fun reset() {
        if (DEBUG) Log.d(TAG, "reset()")
        callback.onPlaybackStateChanged(PlaybackState.STATE_NONE, 1)
        playbackState = PlaybackState.STATE_NONE
        view.reset()
        timeShiftPlayAvailable = false
        startPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME
        timeShiftCurrentPositionMs = 0
        playbackParams.setSpeed(1.0f)
        program = null
        selectedAudioTrackId = null
        selectedSubtitleTrackId = null
    }

    /** Setzt den Callback; null = leerer Callback. */
    fun setCallback(callback: DvrPlayerCallback?) {
        this.callback = callback ?: emptyCallback
    }

    fun setOnAspectRatioChangedListener(listener: OnAspectRatioChangedListener?) {
        onAspectRatioChangedListener = listener
    }

    fun setOnContentBlockedListener(listener: OnContentBlockedListener?) {
        onContentBlockedListener = listener
    }

    fun setOnTracksAvailabilityChangedListener(listener: OnTracksAvailabilityChangedListener?) {
        onTracksAvailabilityChangedListener = listener
    }

    /** Listener für [TvTrackInfo.TYPE_AUDIO] oder [TvTrackInfo.TYPE_SUBTITLE] setzen. */
    fun setOnTrackSelectedListener(trackType: Int, listener: OnTrackSelectedListener?) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            onAudioTrackSelectedListener = listener
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            onSubtitleTrackSelectedListener = listener
        }
    }

    fun getOnTrackSelectedListener(trackType: Int): OnTrackSelectedListener? = when (trackType) {
        TvTrackInfo.TYPE_AUDIO -> onAudioTrackSelectedListener
        TvTrackInfo.TYPE_SUBTITLE -> onSubtitleTrackSelectedListener
        else -> null
    }

    /** Setzt die abzuspielende Aufnahme; eine laufende andere Wiedergabe wird beendet. */
    fun setProgram(program: RecordedProgram, initialSeekPositionMs: Long) {
        if (this.program != null && this.program == program) return
        if (playbackState != PlaybackState.STATE_NONE) reset()
        this.initialSeekPositionMs = initialSeekPositionMs
        this.program = program
    }

    /** Aktuelle Wiedergabeposition in ms. */
    val playbackPosition: Long get() = timeShiftCurrentPositionMs

    /** Aktuelle Wiedergabegeschwindigkeit. */
    val playbackSpeed: Int get() = playbackParams.speed.toInt()

    /** Untertitelspuren der aktuellen Wiedergabe. */
    // Bugfix: getTracks() kann null liefern (NPE im Original)
    val subtitleTracks: ArrayList<TvTrackInfo> get() = ArrayList(view.getTracks(TvTrackInfo.TYPE_SUBTITLE).orEmpty())

    /** Tonspuren der aktuellen Wiedergabe. */
    val audioTracks: ArrayList<TvTrackInfo> get() = ArrayList(view.getTracks(TvTrackInfo.TYPE_AUDIO).orEmpty())

    /** ID der gewählten Spur des Typs. */
    fun getSelectedTrackId(trackType: Int): String? = when (trackType) {
        TvTrackInfo.TYPE_AUDIO -> selectedAudioTrackId
        TvTrackInfo.TYPE_SUBTITLE -> selectedSubtitleTrackId
        else -> null
    }

    /** Ob die Wiedergabe gestartet ist. */
    fun isPlaybackPrepared(): Boolean =
        playbackState != PlaybackState.STATE_NONE && playbackState != PlaybackState.STATE_CONNECTING

    fun release() {
        view.release()
    }

    /** Wählt die Spur und liefert die ID der danach gewählten Spur. */
    internal fun selectTrack(trackType: Int, selectedTrack: TvTrackInfo?): String? {
        val oldSelectedTrackId = getSelectedTrackId(trackType)
        val newSelectedTrackId = selectedTrack?.id
        if (!TextUtils.equals(oldSelectedTrackId, newSelectedTrackId)) {
            if (selectedTrack == null) {
                view.selectTrack(trackType, null)
                return null
            } else {
                val tracks = view.getTracks(trackType)
                if (tracks != null && tracks.contains(selectedTrack)) {
                    view.selectTrack(trackType, newSelectedTrackId)
                    return newSelectedTrackId
                } else if (trackType == TvTrackInfo.TYPE_SUBTITLE && oldSelectedTrackId != null) {
                    // Spur nicht gefunden: Untertitel aus
                    view.selectTrack(trackType, null)
                    return null
                }
            }
        }
        return oldSelectedTrackId
    }

    private fun setSelectedTrackId(trackType: Int, trackId: String?) {
        if (trackType == TvTrackInfo.TYPE_AUDIO) {
            selectedAudioTrackId = trackId
        } else if (trackType == TvTrackInfo.TYPE_SUBTITLE) {
            selectedSubtitleTrackId = trackId
        }
    }

    private fun setPlaybackSpeed(speed: Int) {
        playbackParams.setSpeed(speed.toFloat())
        view.timeShiftSetPlaybackParams(playbackParams)
    }

    private fun getRealSeekPosition(seekPositionMs: Long, endMarginMs: Long): Long =
        max(0L, min(seekPositionMs, program!!.durationMillis - endMarginMs))

    private fun setTvViewCallbacks() {
        view.setTimeShiftPositionCallback(object : TvView.TimeShiftPositionCallback() {
            override fun onTimeShiftStartPositionChanged(inputId: String, timeMs: Long) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStartPositionChanged:$timeMs")
                startPositionMs = timeMs
                if (timeShiftPlayAvailable) resumeToWatchedPositionIfNeeded()
            }

            override fun onTimeShiftCurrentPositionChanged(inputId: String, timeMs: Long) {
                if (DEBUG) Log.d(TAG, "onTimeShiftCurrentPositionChanged: $timeMs")
                if (!timeShiftPlayAvailable) {
                    // Workaround für b/31436263
                    return
                }
                // Absicherung: nach reset() ist program null
                val program = program ?: return
                // Workaround für b/32211561: TIF meldet Startposition 0 nicht. Dann beim ersten
                // Positions-Event vorbereiten, die Startposition ist dann 0.
                if (startPositionMs == TvInputManager.TIME_SHIFT_INVALID_TIME) {
                    startPositionMs = 0
                    resumeToWatchedPositionIfNeeded()
                }
                val positionMs = timeMs - startPositionMs
                val bufferedTimeMs = System.currentTimeMillis() - program.startTimeUtcMillis - FORWARD_POSITION_MARGIN_MS
                if ((playbackState == PlaybackState.STATE_REWINDING && positionMs <= REWIND_POSITION_MARGIN_MS) ||
                    (playbackState == PlaybackState.STATE_FAST_FORWARDING && positionMs > bufferedTimeMs)
                ) {
                    play()
                    callback.onPlaybackResume()
                } else {
                    timeShiftCurrentPositionMs = getRealSeekPosition(positionMs, 0)
                    callback.onPlaybackPositionChanged(timeShiftCurrentPositionMs)
                    if (positionMs >= program.durationMillis) {
                        pause()
                        callback.onPlaybackEnded()
                    }
                }
            }
        })
        view.setCallback(object : TvInputCallbackCompat() {
            override fun onTimeShiftStatusChanged(inputId: String, status: Int) {
                if (DEBUG) Log.d(TAG, "onTimeShiftStatusChanged:$status")
                if (status == TvInputManager.TIME_SHIFT_STATUS_AVAILABLE &&
                    playbackState == PlaybackState.STATE_CONNECTING
                ) {
                    timeShiftPlayAvailable = true
                    if (startPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                        // onTimeShiftStatusChanged kommt manchmal erst nach
                        // onTimeShiftStartPositionChanged; dann hier fortsetzen.
                        resumeToWatchedPositionIfNeeded()
                    }
                }
            }

            override fun onTracksChanged(inputId: String, tracks: List<TvTrackInfo>) {
                // Bugfix: getTracks() kann null liefern
                val hasClosedCaption = !view.getTracks(TvTrackInfo.TYPE_SUBTITLE).isNullOrEmpty()
                val hasMultiAudio = (view.getTracks(TvTrackInfo.TYPE_AUDIO)?.size ?: 0) > 1
                if ((hasClosedCaption != this@DvrPlayer.hasClosedCaption || hasMultiAudio != this@DvrPlayer.hasMultiAudio)) {
                    onTracksAvailabilityChangedListener?.onTracksAvailabilityChanged(hasClosedCaption, hasMultiAudio)
                }
                this@DvrPlayer.hasClosedCaption = hasClosedCaption
                this@DvrPlayer.hasMultiAudio = hasMultiAudio
            }

            override fun onTrackSelected(inputId: String, type: Int, trackId: String?) {
                if (type == TvTrackInfo.TYPE_AUDIO || type == TvTrackInfo.TYPE_SUBTITLE) {
                    setSelectedTrackId(type, trackId)
                    getOnTrackSelectedListener(type)?.onTrackSelected(trackId)
                } else if (type == TvTrackInfo.TYPE_VIDEO && trackId != null) {
                    val listener = onAspectRatioChangedListener ?: return
                    val trackInfos = view.getTracks(TvTrackInfo.TYPE_VIDEO) ?: return
                    for (trackInfo in trackInfos) {
                        if (trackInfo.id != trackId) continue
                        val videoPixelAspectRatio = trackInfo.videoPixelAspectRatio
                        val videoWidth = trackInfo.videoWidth
                        val videoHeight = trackInfo.videoHeight
                        val videoAspectRatio = if (videoWidth > 0 && videoHeight > 0) {
                            videoWidth.toFloat() / videoHeight *
                                (if (videoPixelAspectRatio > 0) videoPixelAspectRatio else 1f)
                        } else {
                            // Seitenverhältnis unbekannt, trotzdem an Listener melden
                            0f
                        }
                        if (DEBUG) Log.d(TAG, "Aspect Ratio: $videoAspectRatio")
                        if (aspectRatio != videoAspectRatio || videoAspectRatio == 0f) {
                            listener.onAspectRatioChanged(videoAspectRatio)
                            aspectRatio = videoAspectRatio
                            return
                        }
                    }
                }
            }

            override fun onContentBlocked(inputId: String, rating: TvContentRating) {
                onContentBlockedListener?.onContentBlocked(rating)
            }
        })
    }

    private fun resumeToWatchedPositionIfNeeded() {
        if (initialSeekPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
            view.timeShiftSeekTo(getRealSeekPosition(initialSeekPositionMs, SEEK_POSITION_MARGIN_MS) + startPositionMs)
            initialSeekPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME
        }
        if (pauseOnPrepared) {
            view.timeShiftPause()
            playbackState = PlaybackState.STATE_PAUSED
            pauseOnPrepared = false
        } else {
            view.timeShiftResume()
            playbackState = PlaybackState.STATE_PLAYING
        }
        callback.onPlaybackStateChanged(playbackState, 1)
    }

    companion object {
        private const val TAG = "DvrPlayer"
        private const val DEBUG = false

        /** Maximale Rücklaufgeschwindigkeit. */
        const val MAX_REWIND_SPEED = 256
        /** Maximale Vorlaufgeschwindigkeit. */
        const val MAX_FAST_FORWARD_SPEED = 256

        private val SEEK_POSITION_MARGIN_MS = TimeUnit.SECONDS.toMillis(2)
        private const val REWIND_POSITION_MARGIN_MS = 32L // Workaround, b/29994826
        private val FORWARD_POSITION_MARGIN_MS = TimeUnit.SECONDS.toMillis(5)
    }
}
