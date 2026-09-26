package com.android.tv.dvr.ui.playback

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.tv.TvContract
import android.text.TextUtils
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.ChannelDataManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.util.TimeShiftUtils
import com.android.tv.util.Utils
import com.android.tv.util.images.ImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Verbindet [DvrPlayer] mit einer Framework-MediaSession (wie im Original): Zustand/Metadaten
 * veröffentlichen und Transport-Befehle an den Player weiterreichen.
 * Activity als FragmentActivity, damit das Dekodieren des Standardbilds per lifecycleScope läuft.
 */
internal class DvrPlaybackMediaSessionHelper(
    private val activity: FragmentActivity,
    mediaSessionTag: String,
    /** Der zugrundeliegende DVR-Player. */
    val dvrPlayer: DvrPlayer,
    overlayFragment: DvrPlaybackOverlayFragment,
) {
    private var nowPlayingCardWidth = 0
    private var nowPlayingCardHeight = 0
    private var speedLevel = 0
    private var programDurationMs = 0L
    private var mediaSession: MediaSession? = null
    private val dvrWatchedPositionManager: DvrWatchedPositionManager =
        TvSingletons.getSingletons(activity).getDvrWatchedPositionManager()
    private val channelDataManager: ChannelDataManager = TvSingletons.getSingletons(activity).getChannelDataManager()

    init {
        dvrPlayer.setCallback(object : DvrPlayer.DvrPlayerCallback() {
            override fun onPlaybackStateChanged(playbackState: Int, playbackSpeed: Int) {
                updateMediaSessionPlaybackState()
            }

            override fun onPlaybackPositionChanged(positionMs: Long) {
                updateMediaSessionPlaybackState()
                if (program?.isPartial == true) overlayFragment.updateProgress()
                if (dvrPlayer.isPlaybackPrepared()) {
                    dvrPlayer.program?.let { dvrWatchedPositionManager.setWatchedPosition(it.id, positionMs) }
                }
            }

            override fun onPlaybackEnded() {
                // TODO: Gesehene Aufnahmen in der DVR-Bibliothek behandeln
                val nextEpisode = dvrPlayer.program?.let { overlayFragment.getNextEpisode(it) }
                if (nextEpisode == null) {
                    dvrPlayer.reset()
                    activity.finish()
                } else {
                    val intent = Intent(activity, DvrPlaybackActivity::class.java)
                        .putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, nextEpisode.id)
                    activity.startActivity(intent)
                }
            }

            override fun onPlaybackResume() {
                overlayFragment.onPlaybackResume()
            }
        })
        initializeMediaSession(mediaSessionTag)
    }

    /** Stoppt den Player und gibt die MediaSession frei. */
    fun release() {
        dvrPlayer.reset()
        mediaSession?.release()
        mediaSession = null
    }

    /** Zustand und Geschwindigkeit an die MediaSession melden. */
    fun updateMediaSessionPlaybackState() {
        // Absicherung: nach release() keine Session mehr
        mediaSession?.setPlaybackState(
            PlaybackState.Builder()
                .setState(dvrPlayer.playbackState, dvrPlayer.playbackPosition, speedLevel.toFloat())
                .build(),
        )
    }

    /** Setzt die Aufnahme für die Wiedergabe; null setzt den Player zurück. */
    fun setupPlayback(program: RecordedProgram?, seekPositionMs: Long) {
        if (program != null) {
            dvrPlayer.setProgram(program, seekPositionMs)
            setupMediaSession(program)
        } else {
            dvrPlayer.reset()
            mediaSession?.isActive = false
        }
    }

    /** Aktuell abgespielte Aufnahme. */
    val program: RecordedProgram? get() = dvrPlayer.program

    /** Ob [program] die aktuell abgespielte Aufnahme ist. */
    fun isCurrentProgram(program: RecordedProgram?): Boolean = program != null && program == this.program

    /** Wiedergabezustand. */
    val playbackState: Int get() = dvrPlayer.playbackState

    private fun initializeMediaSession(mediaSessionTag: String) {
        val session = MediaSession(activity, mediaSessionTag)
        mediaSession = session
        @Suppress("DEPRECATION") // Flags sind ab API 26 ohnehin immer gesetzt
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        nowPlayingCardWidth = activity.resources.getDimensionPixelSize(R.dimen.notif_card_img_max_width)
        nowPlayingCardHeight = activity.resources.getDimensionPixelSize(R.dimen.notif_card_img_height)
        session.setCallback(MediaSessionCallback())
        activity.mediaController = MediaController(activity, session.sessionToken)
        updateMediaSessionPlaybackState()
    }

    private fun setupMediaSession(program: RecordedProgram) {
        programDurationMs = program.durationMillis
        var cardTitleText: String? = program.title
        if (TextUtils.isEmpty(cardTitleText)) {
            val channel = channelDataManager.getChannel(program.channelId)
            cardTitleText = if (channel != null) channel.displayName else activity.getString(R.string.no_program_information)
        }
        val currentMetadata = updateMetadataTextInfo(program.id, cardTitleText, program.description, programDurationMs)
        // Im Kotlin-Modell steht "" statt null für ein fehlendes Posterbild
        val posterArtUri = program.posterArtUri.ifEmpty { TvContract.buildChannelLogoUri(program.channelId).toString() }
        updatePosterArt(program, currentMetadata, null, posterArtUri)
        mediaSession?.isActive = true
    }

    private fun updatePosterArt(program: RecordedProgram, currentMetadata: MediaMetadata, posterArt: Bitmap?, posterArtUri: String?) {
        if (posterArt != null) {
            updateMetadataImageInfo(program, currentMetadata, posterArt, 0)
        } else if (posterArtUri != null) {
            ImageLoader.loadBitmap(
                activity, posterArtUri, nowPlayingCardWidth, nowPlayingCardHeight,
                ProgramPosterArtCallback(activity, program, currentMetadata),
            )
        } else {
            updateMetadataImageInfo(program, currentMetadata, null, R.drawable.default_now_card)
        }
    }

    private inner class ProgramPosterArtCallback(
        activity: Activity,
        private val recordedProgram: RecordedProgram,
        private val currentMetadata: MediaMetadata,
    ) : ImageLoader.ImageLoaderCallback<Activity>(activity) {
        override fun onBitmapLoaded(referent: Activity, bitmap: Bitmap?) {
            if (isCurrentProgram(recordedProgram)) {
                updatePosterArt(recordedProgram, currentMetadata, bitmap, null)
            }
        }
    }

    private fun updateMetadataTextInfo(programId: Long, title: String?, subtitle: String?, duration: Long): MediaMetadata {
        val builder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_MEDIA_ID, programId.toString())
            .putString(MediaMetadata.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, duration)
        if (subtitle != null) builder.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, subtitle)
        val metadata = builder.build()
        mediaSession?.setMetadata(metadata)
        return metadata
    }

    private fun updateMetadataImageInfo(program: RecordedProgram, currentMetadata: MediaMetadata, posterArt: Bitmap?, imageResId: Int) {
        if (mediaSession == null || (posterArt == null && imageResId == 0)) return
        val builder = MediaMetadata.Builder(currentMetadata)
        if (posterArt != null) {
            builder.putBitmap(MediaMetadata.METADATA_KEY_ART, posterArt)
            mediaSession?.setMetadata(builder.build())
        } else {
            // Coroutine statt AsyncTask
            activity.lifecycleScope.launch {
                val programPosterArt: Bitmap? = withContext(Dispatchers.IO) {
                    BitmapFactory.decodeResource(activity.resources, imageResId)
                }
                val session = mediaSession
                if (session != null && programPosterArt != null && isCurrentProgram(program)) {
                    builder.putBitmap(MediaMetadata.METADATA_KEY_ART, programPosterArt)
                    session.setMetadata(builder.build())
                }
            }
        }
    }

    /** Von MediaController.TransportControls ausgelöste Befehle an den Player weitergeben. */
    private inner class MediaSessionCallback : MediaSession.Callback() {
        override fun onPrepare() {
            if (!dvrPlayer.isPlaybackPrepared()) dvrPlayer.prepare(true)
        }

        override fun onPlay() {
            if (dvrPlayer.isPlaybackPrepared()) dvrPlayer.play()
        }

        override fun onPause() {
            if (dvrPlayer.isPlaybackPrepared()) dvrPlayer.pause()
        }

        override fun onFastForward() {
            if (!dvrPlayer.isPlaybackPrepared()) return
            if (dvrPlayer.playbackState == PlaybackState.STATE_FAST_FORWARDING) {
                if (speedLevel < TimeShiftUtils.MAX_SPEED_LEVEL) speedLevel++ else return
            } else {
                speedLevel = 0
            }
            dvrPlayer.fastForward(TimeShiftUtils.getPlaybackSpeed(speedLevel, programDurationMs))
        }

        override fun onRewind() {
            if (!dvrPlayer.isPlaybackPrepared()) return
            if (dvrPlayer.playbackState == PlaybackState.STATE_REWINDING) {
                if (speedLevel < TimeShiftUtils.MAX_SPEED_LEVEL) speedLevel++ else return
            } else {
                speedLevel = 0
            }
            dvrPlayer.rewind(TimeShiftUtils.getPlaybackSpeed(speedLevel, programDurationMs))
        }

        override fun onSeekTo(pos: Long) {
            if (dvrPlayer.isPlaybackPrepared()) dvrPlayer.seekTo(pos)
        }
    }
}
