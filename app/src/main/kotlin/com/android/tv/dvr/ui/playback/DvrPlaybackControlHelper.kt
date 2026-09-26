package com.android.tv.dvr.ui.playback

import android.app.Activity
import android.content.Context
import android.graphics.drawable.Drawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.PlaybackState
import android.media.tv.TvTrackInfo
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.leanback.media.PlaybackControlGlue
import androidx.leanback.widget.AbstractDetailsDescriptionPresenter
import androidx.leanback.widget.Action
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.PlaybackControlsRow
import androidx.leanback.widget.PlaybackControlsRow.ClosedCaptioningAction
import androidx.leanback.widget.PlaybackControlsRow.MultiAction
import androidx.leanback.widget.PlaybackControlsRowPresenter
import androidx.leanback.widget.RowPresenter
import com.android.tv.R
import com.android.tv.util.TimeShiftUtils

/**
 * Hilft [DvrPlaybackOverlayFragment] bei der Steuerzeile: sendet Befehle an den MediaController
 * und zeigt dessen Wiedergabezustand an.
 */
@Suppress("DEPRECATION") // PlaybackControlGlue ist veraltet, wird aber wie im Original genutzt
internal class DvrPlaybackControlHelper(
    activity: Activity,
    private val fragment: DvrPlaybackOverlayFragment,
) : PlaybackControlGlue(activity, IntArray(TimeShiftUtils.MAX_SPEED_LEVEL + 1)) {

    private var playbackState = PlaybackState.STATE_NONE
    private var playbackSpeedLevel = 0
    private var playbackSpeedId = 0
    private var programStartTimeMs = INVALID_TIME
    private var enableBuffering = false
    private var readyToControl = false

    private val mediaController: MediaController = activity.mediaController
    private val mediaControllerCallback: MediaController.Callback = MediaControllerCallback()
    private val transportControls: MediaController.TransportControls
    private val extraPaddingTopForNoDescription: Int =
        activity.resources.getDimensionPixelOffset(R.dimen.dvr_playback_controls_extra_padding_top)
    private val closedCaptioningAction: MultiAction = ClosedCaptioningAction(activity)
    private val multiAudioAction: MultiAction = MultiAudioAction(activity)
    private var secondaryActionsAdapter: ArrayObjectAdapter? = null
    private var playbackControlsRow: PlaybackControlsRow? = null
    private var playPauseButton: View? = null

    init {
        mediaController.registerCallback(mediaControllerCallback)
        transportControls = mediaController.transportControls
        programStartTimeMs = fragment.getProgramStartTimeMs()
        if (programStartTimeMs != INVALID_TIME) enableBuffering = true
        createControlsRowPresenter()
    }

    fun createControlsRow() {
        val row = PlaybackControlsRow(this)
        playbackControlsRow = row
        controlsRow = row
        secondaryActionsAdapter = row.secondaryActionsAdapter as ArrayObjectAdapter
    }

    private fun createControlsRowPresenter() {
        val detailsPresenter = object : AbstractDetailsDescriptionPresenter() {
            override fun onBindDescription(viewHolder: ViewHolder, item: Any) {
                val glue = item as PlaybackControlGlue
                if (glue.hasValidMedia()) {
                    viewHolder.title.text = glue.mediaTitle
                    viewHolder.subtitle.text = glue.mediaSubtitle
                } else {
                    viewHolder.title.text = ""
                    viewHolder.subtitle.text = ""
                }
                if (TextUtils.isEmpty(viewHolder.subtitle.text)) {
                    viewHolder.view.setPadding(
                        viewHolder.view.paddingLeft,
                        extraPaddingTopForNoDescription,
                        viewHolder.view.paddingRight,
                        viewHolder.view.paddingBottom,
                    )
                }
            }
        }
        val presenter = object : PlaybackControlsRowPresenter(detailsPresenter) {
            override fun onBindRowViewHolder(vh: RowPresenter.ViewHolder, item: Any) {
                super.onBindRowViewHolder(vh, item)
                vh.onKeyListener = this@DvrPlaybackControlHelper
                val controlBar = vh.view.findViewById<ViewGroup>(R.id.control_bar)
                playPauseButton = controlBar.getChildAt(1)
            }

            override fun onUnbindRowViewHolder(vh: RowPresenter.ViewHolder) {
                super.onUnbindRowViewHolder(vh)
                vh.onKeyListener = null
            }
        }
        presenter.progressColor = context.getColor(R.color.play_controls_progress_bar_watched)
        presenter.backgroundColor = context.getColor(R.color.play_controls_body_background_enabled)
        controlsRowPresenter = presenter
    }

    override fun onActionClicked(action: Action) {
        if (!readyToControl) return
        val trackType = when (action.id) {
            closedCaptioningAction.id -> TvTrackInfo.TYPE_SUBTITLE
            AUDIO_ACTION_ID.toLong() -> TvTrackInfo.TYPE_AUDIO
            else -> {
                super.onActionClicked(action)
                return
            }
        }
        val trackInfos = fragment.getTracks(trackType)
        if (!trackInfos.isNullOrEmpty()) {
            showSideFragment(trackInfos, fragment.getSelectedTrackId(trackType))
        }
    }

    override fun onKey(v: View, keyCode: Int, event: KeyEvent): Boolean =
        readyToControl && super.onKey(v, keyCode, event)

    override fun hasValidMedia(): Boolean = mediaController.playbackState != null

    override fun isMediaPlaying(): Boolean {
        val state = mediaController.playbackState?.state ?: return false
        return state != PlaybackState.STATE_NONE &&
            state != PlaybackState.STATE_CONNECTING &&
            state != PlaybackState.STATE_PAUSED
    }

    /** ID des laufenden Mediums. */
    fun getMediaId(): String? = mediaController.metadata?.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)

    override fun getMediaTitle(): CharSequence =
        mediaController.metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""

    override fun getMediaSubtitle(): CharSequence =
        mediaController.metadata?.getString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE) ?: ""

    override fun getMediaDuration(): Int =
        mediaController.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION)?.toInt() ?: 0

    // Kein Poster in der Steuerzeile
    override fun getMediaArt(): Drawable? = null

    override fun getSupportedActions(): Long = (ACTION_PLAY_PAUSE or ACTION_FAST_FORWARD or ACTION_REWIND).toLong()

    override fun getCurrentSpeedId(): Int = playbackSpeedId

    override fun getCurrentPosition(): Int = mediaController.playbackState?.position?.toInt() ?: 0

    /** Callback am MediaController abmelden. */
    fun unregisterCallback() {
        mediaController.unregisterCallback(mediaControllerCallback)
    }

    /** Zweite Steuerzeile aktualisieren: Untertitel- bzw. Tonspur-Knopf ein-/ausblenden. */
    fun updateSecondaryRow(hasClosedCaption: Boolean, hasMultiAudio: Boolean) {
        val adapter = secondaryActionsAdapter ?: return // Absicherung: Zeile noch nicht erzeugt
        if (hasClosedCaption) {
            if (adapter.indexOf(closedCaptioningAction) < 0) adapter.add(0, closedCaptioningAction)
        } else {
            adapter.remove(closedCaptioningAction)
        }
        if (hasMultiAudio) {
            if (adapter.indexOf(multiAudioAction) < 0) adapter.add(multiAudioAction)
        } else {
            adapter.remove(multiAudioAction)
        }
        host?.notifyPlaybackRowChanged()
    }

    /** Fokus auf den Play/Pause-Knopf setzen. */
    fun onPlaybackResume() {
        playPauseButton?.requestFocus()
    }

    /** null, solange die Steuerzeile noch nicht erzeugt ist. */
    fun hasSecondaryRow(): Boolean? = secondaryActionsAdapter?.let { it.size() != 0 }

    override fun play(speedId: Int) {
        if (currentSpeedId == speedId) return
        if (speedId == PLAYBACK_SPEED_NORMAL) {
            transportControls.play()
        } else if (speedId <= -PLAYBACK_SPEED_FAST_L0) {
            transportControls.rewind()
        } else if (speedId >= PLAYBACK_SPEED_FAST_L0) {
            transportControls.fastForward()
        }
    }

    override fun pause() {
        transportControls.pause()
    }

    override fun updateProgress() {
        if (enableBuffering) {
            super.updateProgress()
            val bufferedTimeMs = System.currentTimeMillis() - programStartTimeMs
            playbackControlsRow?.bufferedPosition = bufferedTimeMs
        }
    }

    /** Untertitel an/aus in der UI anzeigen. */
    fun onSubtitleTrackStateChanged(enabled: Boolean) {
        closedCaptioningAction.index = if (enabled) ClosedCaptioningAction.INDEX_ON else ClosedCaptioningAction.INDEX_OFF
    }

    private fun onStateChanged(state: Int, positionMs: Long, speedLevel: Int) {
        if (DEBUG) Log.d(TAG, "onStateChanged")
        // setCurrentPosition(long) statt des veralteten setCurrentTime(int)
        controlsRow.currentPosition = positionMs
        if (state == playbackState && playbackSpeedLevel == speedLevel) {
            // Nur die Position hat sich geändert
            return
        }
        // Nur hier benutzt, um Zustandswechsel zu erkennen
        playbackState = state
        playbackSpeedLevel = speedLevel
        when (state) {
            PlaybackState.STATE_PLAYING -> {
                playbackSpeedId = PLAYBACK_SPEED_NORMAL
                setFadingEnabled(true)
                readyToControl = true
            }
            PlaybackState.STATE_PAUSED -> {
                playbackSpeedId = PLAYBACK_SPEED_PAUSED
                setFadingEnabled(true)
                readyToControl = true
            }
            PlaybackState.STATE_FAST_FORWARDING -> {
                playbackSpeedId = PLAYBACK_SPEED_FAST_L0 + speedLevel
                setFadingEnabled(false)
                readyToControl = true
            }
            PlaybackState.STATE_REWINDING -> {
                playbackSpeedId = -PLAYBACK_SPEED_FAST_L0 - speedLevel
                setFadingEnabled(false)
                readyToControl = true
            }
            PlaybackState.STATE_CONNECTING -> {
                setFadingEnabled(false)
                readyToControl = false
            }
            PlaybackState.STATE_NONE -> readyToControl = false
            else -> setFadingEnabled(true)
        }
        onStateChanged()
    }

    private fun showSideFragment(trackInfos: ArrayList<TvTrackInfo>, selectedTrackId: String?) {
        val args = Bundle().apply {
            putParcelableArrayList(DvrPlaybackSideFragment.TRACK_INFOS, trackInfos)
            putString(DvrPlaybackSideFragment.SELECTED_TRACK_ID, selectedTrackId)
        }
        val sideFragment = DvrPlaybackSideFragment().apply { arguments = args }
        fragment.parentFragmentManager
            .beginTransaction()
            .hide(fragment)
            .replace(R.id.dvr_playback_side_fragment, sideFragment)
            .addToBackStack(null)
            .commit()
    }

    private inner class MediaControllerCallback : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            if (state == null) return // Absicherung: Framework darf null melden
            if (DEBUG) Log.d(TAG, "Playback state changed: ${state.state}")
            onStateChanged(state.state, state.position, state.playbackSpeed.toInt())
        }

        override fun onMetadataChanged(metadata: MediaMetadata?) {
            this@DvrPlaybackControlHelper.onMetadataChanged()
        }
    }

    private class MultiAudioAction(context: Context) : MultiAction(AUDIO_ACTION_ID) {
        init {
            setDrawables(arrayOf(context.getDrawable(R.drawable.ic_tvoption_multi_track)))
        }
    }

    companion object {
        private const val TAG = "DvrPlaybackControlHelpr"
        private const val DEBUG = false
        private const val AUDIO_ACTION_ID = 1001
        private const val INVALID_TIME = -1L
    }
}
