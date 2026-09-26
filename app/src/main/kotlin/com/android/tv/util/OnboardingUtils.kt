package com.android.tv.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.preference.PreferenceManager

/** Status der Ersteinrichtung (Onboarding). */
object OnboardingUtils {
    // Schreibfehler "onbaording" aus dem Original – Schlüssel müssen gleich bleiben
    private const val PREF_KEY_IS_FIRST_BOOT = "pref_onbaording_is_first_boot"
    private const val PREF_KEY_ONBOARDING_VERSION_CODE = "pref_onbaording_versionCode"
    private const val ONBOARDING_VERSION = 1

    /** UiFlags.moreChannelsUrl() ist im AOSP-Build leer. */
    private const val MORE_CHANNELS_URL = ""

    /** Intent für "Weitere Kanäle" (Store) oder null ohne URL. */
    @JvmStatic
    fun createOnlineStoreIntent(): Intent? =
        if (MORE_CHANNELS_URL.isEmpty()) null else Intent(Intent.ACTION_VIEW, Uri.parse(MORE_CHANNELS_URL))

    private fun prefs(context: Context) = PreferenceManager.getDefaultSharedPreferences(context)

    @JvmStatic
    fun isFirstBoot(context: Context): Boolean = prefs(context).getBoolean(PREF_KEY_IS_FIRST_BOOT, true)

    @JvmStatic
    fun setFirstBootCompleted(context: Context) = prefs(context).edit().putBoolean(PREF_KEY_IS_FIRST_BOOT, false).apply()

    /** true, wenn das Onboarding dieser Version noch nicht gezeigt wurde. */
    @JvmStatic
    fun isFirstRunWithCurrentVersion(context: Context): Boolean =
        prefs(context).getInt(PREF_KEY_ONBOARDING_VERSION_CODE, 0) != ONBOARDING_VERSION

    @JvmStatic
    fun setFirstRunWithCurrentVersionCompleted(context: Context) =
        prefs(context).edit().putInt(PREF_KEY_ONBOARDING_VERSION_CODE, ONBOARDING_VERSION).apply()
}
