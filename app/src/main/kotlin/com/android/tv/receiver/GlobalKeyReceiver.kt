package com.android.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import androidx.core.content.IntentCompat
import com.android.tv.Starter
import com.android.tv.TvApplication
import com.android.tv.TvSingletons
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Globale Tasten (DVR/Guide/TV/Input) vom System, erst nach abgeschlossener Geräte-Einrichtung.
 * Hinweis: GLOBAL_BUTTON kommt nur an, wenn das Gerät die App dafür konfiguriert hat.
 * AsyncTask → goAsync() + Coroutine.
 */
class GlobalKeyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (!TvSingletons.getSingletons(context).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager")
            return
        }
        Starter.start(context)
        val appContext = context.applicationContext
        if (userSetupComplete) {
            handleIntent(appContext, intent)
            return
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.Main).launch {
            try {
                userSetupComplete = withContext(Dispatchers.IO) {
                    Settings.Secure.getInt(appContext.contentResolver, SETTINGS_USER_SETUP_COMPLETE, 0) != 0
                }
                if (userSetupComplete) handleIntent(appContext, intent)
            } finally {
                pending.finish()
            }
        }
    }

    private fun handleIntent(appContext: Context, intent: Intent) {
        if (intent.action != ACTION_GLOBAL_BUTTON) return
        val event = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_KEY_EVENT, KeyEvent::class.java) ?: return
        // Doppelte Zustellung desselben Events ignorieren
        if (event.action != KeyEvent.ACTION_UP || lastEventTime == event.eventTime) return
        lastEventTime = event.eventTime
        val app = appContext as TvApplication
        when (event.keyCode) {
            KeyEvent.KEYCODE_DVR -> app.handleDvrKey()
            KeyEvent.KEYCODE_GUIDE -> app.handleGuideKey()
            KeyEvent.KEYCODE_TV -> app.handleTvKey()
            KeyEvent.KEYCODE_TV_INPUT -> app.handleTvInputKey()
        }
    }

    companion object {
        private const val TAG = "GlobalKeyReceiver"
        private const val ACTION_GLOBAL_BUTTON = "android.intent.action.GLOBAL_BUTTON"
        private const val SETTINGS_USER_SETUP_COMPLETE = "user_setup_complete"
        private var lastEventTime = 0L
        private var userSetupComplete = false
    }
}
