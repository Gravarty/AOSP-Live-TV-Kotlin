package com.android.tv.util

import android.content.Context
import android.content.Intent
import android.util.Log

/** Hilfen für Google TV (Launcher über den gewählten Eingang informieren). */
object GtvUtils {
    private const val TAG = "GtvUtils"
    private const val AMATI_FEATURE = "com.google.android.feature.AMATI_EXPERIENCE"
    private const val PERMISSION_WRITE_EPG_DATA = "com.android.providers.tv.permission.WRITE_EPG_DATA"
    private const val ACTION_INPUT_SELECTED = "android.apps.tv.launcherx.INPUT_SELECTED"
    private const val EXTRA_INPUT_ID = "extra_input_id"
    private const val LAUNCHERX_PACKAGE_NAME = "com.google.android.apps.tv.launcherx"
    private var enabled: Boolean? = null

    private fun isEnabled(context: Context): Boolean =
        enabled ?: context.packageManager.hasSystemFeature(AMATI_FEATURE).also { enabled = it }

    /** Sendet die Eingangs-ID per Broadcast an den Launcher. */
    @JvmStatic
    fun broadcastInputId(context: Context, inputId: String?) {
        if (!isEnabled(context)) return
        if (inputId == null) {
            Log.e(TAG, "Will not broadcast inputId because it is null")
        } else {
            val intent = Intent(ACTION_INPUT_SELECTED)
                .putExtra(EXTRA_INPUT_ID, inputId)
                .setPackage(LAUNCHERX_PACKAGE_NAME)
            context.sendBroadcast(intent, PERMISSION_WRITE_EPG_DATA)
        }
    }
}
