package com.android.tv.common.actions

import android.content.Intent
import android.media.tv.TvInputInfo
import android.os.Bundle
import androidx.core.os.BundleCompat

/** Intent-Aktionen/Extras zum Starten der Input-Einrichtung (inkl. alter Google-Varianten). */
object InputSetupActionUtils {
    const val INTENT_ACTION_INPUT_SETUP = "com.android.tv.action.LAUNCH_INPUT_SETUP"
    const val EXTRA_INPUT_ID = TvInputInfo.EXTRA_INPUT_ID
    const val EXTRA_SETUP_INTENT = "com.android.tv.extra.SETUP_INTENT"
    const val EXTRA_ACTIVITY_AFTER_COMPLETION = "com.android.tv.intent.extra.ACTIVITY_AFTER_COMPLETION"

    private const val INTENT_GOOGLE_ACTION_INPUT_SETUP = "com.google.android.tv.action.LAUNCH_INPUT_SETUP"
    private const val EXTRA_GOOGLE_SETUP_INTENT = "com.google.android.tv.extra.SETUP_INTENT"
    private const val EXTRA_GOOGLE_ACTIVITY_AFTER_COMPLETION = "com.google.android.tv.intent.extra.ACTIVITY_AFTER_COMPLETION"

    @JvmStatic
    fun removeSetupIntent(extras: Bundle) {
        extras.remove(EXTRA_SETUP_INTENT)
        extras.remove(EXTRA_GOOGLE_SETUP_INTENT)
    }

    @JvmStatic
    fun getExtraSetupIntent(intent: Intent): Intent? {
        val extras = intent.extras ?: return null
        return BundleCompat.getParcelable(extras, EXTRA_SETUP_INTENT, Intent::class.java)
            ?: BundleCompat.getParcelable(extras, EXTRA_GOOGLE_SETUP_INTENT, Intent::class.java)
    }

    @JvmStatic
    fun getExtraActivityAfter(intent: Intent): Intent? {
        val extras = intent.extras ?: return null
        return BundleCompat.getParcelable(extras, EXTRA_ACTIVITY_AFTER_COMPLETION, Intent::class.java)
            ?: BundleCompat.getParcelable(extras, EXTRA_GOOGLE_ACTIVITY_AFTER_COMPLETION, Intent::class.java)
    }

    @JvmStatic
    fun hasInputSetupAction(intent: Intent): Boolean =
        intent.action == INTENT_ACTION_INPUT_SETUP || intent.action == INTENT_GOOGLE_ACTION_INPUT_SETUP
}
