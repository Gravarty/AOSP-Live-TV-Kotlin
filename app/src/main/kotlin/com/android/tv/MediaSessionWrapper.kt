package com.android.tv

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.media.tv.TvContract
import androidx.annotation.DrawableRes
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program
import com.android.tv.util.Utils
import com.android.tv.util.images.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * MediaSession für die "Läuft gerade"-Karte des Launchers (Titel + Bild).
 * AsyncTask (seriell) → Coroutine auf einem seriellen IO-Dispatcher.
 */
class MediaSessionWrapper(private val context: Context, pendingIntent: PendingIntent) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    val mediaSession = MediaSession(context, MEDIA_SESSION_TAG)
    private lateinit var mediaController: MediaController
    private val nowPlayingCardWidth = context.resources.getDimensionPixelSize(R.dimen.notif_card_img_max_width)
    private val nowPlayingCardHeight = context.resources.getDimensionPixelSize(R.dimen.notif_card_img_height)

    val mediaControllerCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            // Session erst nach dem Stopp-Status deaktivieren
            if (isMediaSessionStateStop(state)) mediaSession.isActive = false
        }
    }

    init {
        mediaSession.setCallback(object : MediaSession.Callback() {
            // Medientasten hier verbrauchen, nicht an andere Apps weitergeben
            override fun onMediaButtonEvent(mediaButtonIntent: Intent): Boolean = true
        })
        @Suppress("DEPRECATION")
        mediaSession.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS or MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
        mediaSession.setSessionActivity(pendingIntent)
        initMediaController()
    }

    fun setPlaybackState(isPlaying: Boolean) {
        if (isPlaying) {
            mediaSession.isActive = true
            // Muss nach setActive(true) kommen
            mediaSession.setPlaybackState(MEDIA_SESSION_STATE_PLAYING)
        } else if (mediaSession.isActive) {
            mediaSession.setPlaybackState(MEDIA_SESSION_STATE_STOPPED)
        }
    }

    /** Aktualisiert Titel/Bild der Karte; gesperrte Kanäle zeigen das Schloss. */
    fun update(blocked: Boolean, currentChannel: Channel?, currentProgram: Program?) {
        if (currentChannel == null) {
            setPlaybackState(false)
            return
        }
        if (blocked) {
            val art = BitmapFactory.decodeResource(context.resources, R.drawable.ic_message_lock_preview)
            updateMediaMetadata(context.resources.getString(R.string.channel_banner_locked_channel_title), art)
            setPlaybackState(true)
            return
        }
        var cardTitleText = currentProgram?.title
        var posterArtUri = currentProgram?.posterArtUri
        if (cardTitleText.isNullOrEmpty()) cardTitleText = getChannelName(currentChannel)
        updateMediaMetadata(cardTitleText, null)
        if (posterArtUri == null) posterArtUri = TvContract.buildChannelLogoUri(currentChannel.id).toString()
        updatePosterArt(currentChannel, currentProgram, cardTitleText, null, posterArtUri)
        setPlaybackState(true)
    }

    fun release() {
        unregisterMediaControllerCallback()
        mediaSession.release()
        scope.cancel()
    }

    private fun getChannelName(channel: Channel): String? =
        if (channel.isPassthrough) {
            Utils.loadLabel(context, TvSingletons.getSingletons(context).getTvInputManagerHelper().getTvInputInfo(channel.inputId))
        } else {
            channel.displayName
        }

    private fun updatePosterArt(
        channel: Channel, program: Program?, cardTitleText: String?, posterArt: Bitmap?, posterArtUri: String?,
    ) {
        when {
            posterArt != null -> updateMediaMetadata(cardTitleText, posterArt)
            posterArtUri != null -> ImageLoader.loadBitmap(
                context, posterArtUri, nowPlayingCardWidth, nowPlayingCardHeight,
                ProgramPosterArtCallback(this, channel, program, cardTitleText))
            else -> updateMediaMetadata(cardTitleText, R.drawable.default_now_card)
        }
    }

    private fun updateMediaMetadata(title: String?, posterArt: Bitmap?) {
        scope.launch {
            val builder = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            posterArt?.let { builder.putBitmap(MediaMetadata.METADATA_KEY_ART, it) }
            mediaSession.setMetadata(builder.build())
        }
    }

    private fun updateMediaMetadata(title: String?, @DrawableRes imageResId: Int) {
        scope.launch {
            val builder = MediaMetadata.Builder().putString(MediaMetadata.METADATA_KEY_TITLE, title)
            BitmapFactory.decodeResource(context.resources, imageResId)?.let {
                builder.putBitmap(MediaMetadata.METADATA_KEY_ART, it)
            }
            mediaSession.setMetadata(builder.build())
        }
    }

    fun initMediaController() {
        mediaController = MediaController(context, mediaSession.sessionToken)
        (context as Activity).mediaController = mediaController
        mediaController.registerCallback(mediaControllerCallback)
    }

    fun unregisterMediaControllerCallback() = mediaController.unregisterCallback(mediaControllerCallback)

    private class ProgramPosterArtCallback(
        wrapper: MediaSessionWrapper,
        private val channel: Channel,
        private val program: Program?,
        private val cardTitleText: String?,
    ) : ImageLoader.ImageLoaderCallback<MediaSessionWrapper>(wrapper) {
        override fun onBitmapLoaded(referent: MediaSessionWrapper, bitmap: Bitmap?) {
            // Nur übernehmen, wenn die Sendung noch läuft
            if ((referent.context as MainActivity).isNowPlayingProgram(channel, program)) {
                referent.updatePosterArt(channel, program, cardTitleText, bitmap, null)
            }
        }
    }

    companion object {
        private const val MEDIA_SESSION_TAG = "com.android.tv.mediasession"

        @JvmField
        val MEDIA_SESSION_STATE_PLAYING: PlaybackState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f).build()

        @JvmField
        val MEDIA_SESSION_STATE_STOPPED: PlaybackState = PlaybackState.Builder()
            .setState(PlaybackState.STATE_STOPPED, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 0.0f).build()

        private fun isMediaSessionStateStop(state: PlaybackState?): Boolean =
            state != null &&
                state.state == MEDIA_SESSION_STATE_STOPPED.state &&
                state.position == MEDIA_SESSION_STATE_STOPPED.position &&
                state.playbackSpeed == MEDIA_SESSION_STATE_STOPPED.playbackSpeed
    }
}
