package com.android.tv.common.util

import android.content.Context
import androidx.preference.PreferenceManager
import com.android.tv.common.CommonConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SharedPreferencesUtils {
    // Achtung: Namen ändern setzt die gespeicherten Werte zurück.
    const val SHARED_PREF_FEATURES = "sharePreferencesFeatures"
    const val SHARED_PREF_BROWSABLE = "browsable_shared_preference"
    const val SHARED_PREF_WATCHED_HISTORY = "watched_history_shared_preference"
    const val SHARED_PREF_DVR_WATCHED_POSITION = "dvr_watched_position_shared_preference"
    const val SHARED_PREF_AUDIO_CAPABILITIES = CommonConstants.BASE_PACKAGE + ".audio_capabilities"
    const val SHARED_PREF_RECURRING_RUNNER = "sharedPreferencesRecurringRunner"
    const val SHARED_PREF_EPG = "epg_preferences"
    const val SHARED_PREF_SERIES_RECORDINGS = "seriesRecordings"
    const val SHARED_PREF_CHANNEL_LOGO_URIS = "channelLogoUris"
    const val SHARED_PREF_UI_SETTINGS = "ui_settings"
    const val SHARED_PREF_PREVIEW_PROGRAMS = "previewPrograms"

    private var initializeCalled = false

    /** Lädt alle SharedPreferences im Hintergrund vor, danach läuft [postTask] auf dem Main-Thread. */
    @JvmStatic
    @Synchronized
    fun initialize(context: Context, scope: CoroutineScope, postTask: Runnable) {
        if (initializeCalled) return
        initializeCalled = true
        scope.launch(Dispatchers.Main) {
            withContext(Dispatchers.IO) {
                PreferenceManager.getDefaultSharedPreferences(context)
                listOf(
                    SHARED_PREF_FEATURES, SHARED_PREF_BROWSABLE, SHARED_PREF_WATCHED_HISTORY,
                    SHARED_PREF_DVR_WATCHED_POSITION, SHARED_PREF_AUDIO_CAPABILITIES,
                    SHARED_PREF_RECURRING_RUNNER, SHARED_PREF_EPG, SHARED_PREF_SERIES_RECORDINGS,
                    SHARED_PREF_UI_SETTINGS,
                ).forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE) }
            }
            postTask.run()
        }
    }
}
