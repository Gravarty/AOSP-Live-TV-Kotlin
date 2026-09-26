package com.android.tv.interactive

import android.graphics.Rect
import android.media.tv.AitInfo
import android.media.tv.TvTrackInfo
import android.media.tv.interactive.TvInteractiveAppManager
import android.media.tv.interactive.TvInteractiveAppService
import android.media.tv.interactive.TvInteractiveAppServiceInfo
import android.media.tv.interactive.TvInteractiveAppView
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.annotation.RequiresApi
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.common.util.ContentUriUtils
import com.android.tv.data.api.Channel
import com.android.tv.dialog.InteractiveAppDialogFragment
import com.android.tv.ui.TunableTvView
import com.android.tv.util.CaptionSettings
import com.android.tv.util.TvSettings

/**
 * Port von IAppManager: interaktive TV-Apps (HbbTV, ATSC, Ginga) über TIAF, ab Android 13.
 * Bugfixes: Callbacks laufen auf dem Main-Thread (Original: Hintergrund-Executor, der Views
 * änderte), stop() ohne View stürzt nicht mehr ab, Videogrenzen setzen korrekte Ränder.
 */
@RequiresApi(33)
class IAppManager(
    private val mainActivity: MainActivity,
    private val tvView: TunableTvView,
    private val handler: Handler,
) {
    private val tvIAppManager: TvInteractiveAppManager? = mainActivity.getSystemService(TvInteractiveAppManager::class.java)
    private val tvIAppView: TvInteractiveAppView? = mainActivity.findViewById(R.id.tv_app_view)
    private var currentAitInfo: AitInfo? = null
    // Bis zur Bestätigung im Dialog zurückgehaltene AIT-Info
    private var heldAitInfo: AitInfo? = null
    private var tvAppDialogShown = false

    init {
        if (tvIAppManager == null || tvIAppView == null) {
            Log.e(TAG, "Could not find interactive app view or manager")
        } else {
            val executor = mainActivity.mainExecutor
            tvIAppManager.registerCallback(executor, MyInteractiveAppManagerCallback())
            tvIAppView.setCallback(executor, MyInteractiveAppViewCallback())
            tvIAppView.setOnUnhandledInputEventListener(executor) { inputEvent ->
                if (mainActivity.isKeyEventBlocked()) return@setOnUnhandledInputEventListener true
                val keyEvent = inputEvent as? KeyEvent ?: return@setOnUnhandledInputEventListener false
                if (keyEvent.action == KeyEvent.ACTION_DOWN && keyEvent.isLongPress &&
                    mainActivity.onKeyLongPress(keyEvent.keyCode, keyEvent)
                ) return@setOnUnhandledInputEventListener true
                when (keyEvent.action) {
                    KeyEvent.ACTION_UP -> mainActivity.onKeyUp(keyEvent.keyCode, keyEvent)
                    KeyEvent.ACTION_DOWN -> mainActivity.onKeyDown(keyEvent.keyCode, keyEvent)
                    else -> false
                }
            }
        }
    }

    fun stop() {
        tvIAppView?.stopInteractiveApp()
        tvIAppView?.reset()
        currentAitInfo = null
    }

    /** Nach Bestätigung im Dialog die zurückgehaltene AIT-Info verarbeiten. */
    fun processHeldAitInfo() {
        heldAitInfo?.let { onAitInfoUpdated(it) }
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean =
        tvIAppView != null && tvIAppView.visibility == View.VISIBLE && tvIAppView.dispatchKeyEvent(event)

    /** Startet die passende interaktive App oder fragt beim ersten Mal per Dialog nach. */
    fun onAitInfoUpdated(aitInfo: AitInfo?) {
        val manager = tvIAppManager ?: return
        if (aitInfo == null) return
        if (currentAitInfo != null && currentAitInfo!!.type == aitInfo.type) return // gleicher Typ läuft schon
        val tvIAppInfoList = manager.tvInteractiveAppServiceList
        if (tvIAppInfoList.isEmpty()) return
        val type = when (aitInfo.type) {
            0x0010 -> TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV
            0x0006, 0x0007 -> TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_ATSC // DCAP-J/-X
            0x0001, 0x0009, 0x000b -> TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_GINGA
            else -> {
                Log.e(TAG, "AIT info contained unknown type: ${aitInfo.type}")
                return
            }
        }
        val info = tvIAppInfoList.firstOrNull { it.supportedTypes and type > 0 }
        if (TvSettings.isTvIAppOn(mainActivity.applicationContext)) {
            tvAppDialogShown = false
            if (info != null) {
                currentAitInfo = aitInfo
                tvIAppView?.let {
                    it.visibility = View.VISIBLE
                    it.prepareInteractiveApp(info.id, type)
                }
            }
        } else if (!tvAppDialogShown && info != null) {
            mainActivity.overlayManager.showDialogFragment(
                InteractiveAppDialogFragment.DIALOG_TAG,
                InteractiveAppDialogFragment.create(info.serviceInfo!!.packageName), false)
            heldAitInfo = aitInfo
            tvAppDialogShown = true
        }
    }

    private inner class MyInteractiveAppManagerCallback : TvInteractiveAppManager.TvInteractiveAppCallback() {
        override fun onInteractiveAppServiceAdded(iAppServiceId: String) {}
        override fun onInteractiveAppServiceRemoved(iAppServiceId: String) {}
        override fun onInteractiveAppServiceUpdated(iAppServiceId: String) {}

        override fun onTvInteractiveAppServiceStateChanged(iAppServiceId: String, type: Int, state: Int, err: Int) {
            val view = tvIAppView ?: return
            if (state == TvInteractiveAppManager.SERVICE_STATE_READY) {
                view.startInteractiveApp()
                view.setTvView(tvView.tvView)
                tvView.tvView.setInteractiveAppNotificationEnabled(true)
            }
        }
    }

    private inner class MyInteractiveAppViewCallback : TvInteractiveAppView.TvInteractiveAppCallback() {
        override fun onPlaybackCommandRequest(iAppServiceId: String, cmdType: String, parameters: Bundle) {
            when (cmdType) {
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE -> {
                    val uriString = parameters.getString(TvInteractiveAppService.COMMAND_PARAMETER_KEY_CHANNEL_URI) ?: return
                    val channel = mainActivity.channelDataManager.getChannel(ContentUriUtils.safeParseId(Uri.parse(uriString)))
                    if (channel != null) handler.post { mainActivity.tuneToChannel(channel) }
                }
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_SELECT_TRACK -> {
                    val trackType = parameters.getInt(TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_TYPE, -1)
                    val trackId = parameters.getString(TvInteractiveAppService.COMMAND_PARAMETER_KEY_TRACK_ID, null)
                    when (trackType) {
                        TvTrackInfo.TYPE_AUDIO -> handler.post { mainActivity.selectAudioTrack(trackId) }
                        TvTrackInfo.TYPE_SUBTITLE -> handler.post {
                            mainActivity.selectSubtitleTrack(
                                if (trackId == null) CaptionSettings.OPTION_OFF else CaptionSettings.OPTION_ON, trackId)
                        }
                    }
                }
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_SET_STREAM_VOLUME -> {
                    val volume = parameters.getFloat(TvInteractiveAppService.COMMAND_PARAMETER_KEY_VOLUME, -1f)
                    if (volume in 0.0f..1.0f) handler.post { tvView.setStreamVolume(volume) }
                }
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE_NEXT -> handler.post { mainActivity.channelUp() }
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_TUNE_PREV -> handler.post { mainActivity.channelDown() }
                // Stop-Modus (blank/freeze) wertet das Original nicht aus
                TvInteractiveAppService.PLAYBACK_COMMAND_TYPE_STOP -> handler.post { mainActivity.stopTv() }
                else -> Log.e(TAG, "PlaybackCommandRequest had unknown cmdType:$cmdType")
            }
        }

        override fun onStateChanged(iAppServiceId: String, state: Int, err: Int) {}
        override fun onBiInteractiveAppCreated(iAppServiceId: String, biIAppUri: Uri, biIAppId: String?) {}
        override fun onTeletextAppStateChanged(iAppServiceId: String, state: Int) {}

        /** Videofläche setzen. Bugfix: rect.right/bottom sind Koordinaten, keine Ränder. */
        override fun onSetVideoBounds(iAppServiceId: String, rect: Rect) {
            val layoutParams = tvView.tvViewLayoutParams
            layoutParams.setMargins(rect.left, rect.top, tvView.width - rect.right, tvView.height - rect.bottom)
            tvView.tvViewLayoutParams = layoutParams
        }

        @RequiresApi(34)
        override fun onRequestCurrentVideoBounds(iAppServiceId: String) {
            handler.post {
                tvIAppView?.sendCurrentVideoBounds(Rect(tvView.left, tvView.top, tvView.right, tvView.bottom))
            }
        }

        override fun onRequestCurrentChannelUri(iAppServiceId: String) {
            tvIAppView?.sendCurrentChannelUri(mainActivity.currentChannel?.uri)
        }

        /** LCN nur bei Nummern im Format "123-4" (wie im Original). */
        override fun onRequestCurrentChannelLcn(iAppServiceId: String) {
            val view = tvIAppView ?: return
            val displayNumber = mainActivity.currentChannel?.displayNumber ?: return
            if (!displayNumber.matches(Regex("[0-9]+${Channel.CHANNEL_NUMBER_DELIMITER}[0-9]+"))) return
            view.sendCurrentChannelLcn(displayNumber.split(Channel.CHANNEL_NUMBER_DELIMITER)[0].toInt())
        }

        override fun onRequestStreamVolume(iAppServiceId: String) {
            tvIAppView?.sendStreamVolume(tvView.streamVolume)
        }

        override fun onRequestTrackInfoList(iAppServiceId: String) {
            val view = tvIAppView ?: return
            val allTracks = intArrayOf(TvTrackInfo.TYPE_AUDIO, TvTrackInfo.TYPE_VIDEO, TvTrackInfo.TYPE_SUBTITLE)
                .flatMap { tvView.getTracks(it).orEmpty() }
            view.sendTrackInfoList(allTracks)
        }

        override fun onRequestCurrentTvInputId(iAppServiceId: String) {
            tvIAppView?.sendCurrentTvInputId(mainActivity.currentChannel?.inputId)
        }

        override fun onRequestSigning(iAppServiceId: String, signingId: String, algorithm: String, alias: String, data: ByteArray) {}
    }

    companion object {
        private const val TAG = "IAppManager"
    }
}
