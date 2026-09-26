package com.android.tv.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.content.res.Resources
import android.media.tv.TvInputInfo
import android.util.Log

/** 1:1-Port: liest OEM-Anpassungen (Eingangs-Reihenfolge) aus einer Partner-System-App. */
class Partner private constructor(
    private val packageName: String?,
    private val receiverName: String?,
    private val resources: Resources?,
) {
    private fun sendInitBroadcast(context: Context) {
        if (packageName.isNullOrEmpty() || receiverName.isNullOrEmpty()) return
        context.sendBroadcast(Intent(ACTION_PARTNER_CUSTOMIZATION).apply {
            component = ComponentName(packageName, receiverName)
            flags = Intent.FLAG_RECEIVER_FOREGROUND
        })
    }

    /** Eingangstyp -> Priorität laut Partner-Array "home_screen_inputs_ordering". */
    fun getInputsOrderMap(): MutableMap<Int, Int> {
        val map = HashMap<Int, Int>()
        if (resources == null || packageName.isNullOrEmpty()) return map
        val resId = resources.getIdentifier(INPUTS_ORDER, TYPE_ARRAY, packageName)
        if (resId == 0) return map
        var priority = 0
        for (input in resources.getStringArray(resId)) {
            INPUT_TYPE_MAP[input]?.let { map[it] = priority++ }
        }
        return map
    }

    companion object {
        private const val TAG = "Partner"
        private const val ACTION_PARTNER_CUSTOMIZATION =
            "com.google.android.leanbacklauncher.action.PARTNER_CUSTOMIZATION"
        private const val INPUTS_ORDER = "home_screen_inputs_ordering"
        private const val TYPE_ARRAY = "array"

        const val INPUT_TYPE_BUNDLED_TUNER = "input_type_combined_tuners"
        const val INPUT_TYPE_TUNER = "input_type_tuner"
        const val INPUT_TYPE_CEC_LOGICAL = "input_type_cec_logical"
        const val INPUT_TYPE_CEC_RECORDER = "input_type_cec_recorder"
        const val INPUT_TYPE_CEC_PLAYBACK = "input_type_cec_playback"
        const val INPUT_TYPE_MHL_MOBILE = "input_type_mhl_mobile"
        const val INPUT_TYPE_HDMI = "input_type_hdmi"
        const val INPUT_TYPE_DVI = "input_type_dvi"
        const val INPUT_TYPE_COMPONENT = "input_type_component"
        const val INPUT_TYPE_SVIDEO = "input_type_svideo"
        const val INPUT_TYPE_COMPOSITE = "input_type_composite"
        const val INPUT_TYPE_DISPLAY_PORT = "input_type_displayport"
        const val INPUT_TYPE_VGA = "input_type_vga"
        const val INPUT_TYPE_SCART = "input_type_scart"
        const val INPUT_TYPE_OTHER = "input_type_other"

        private val INPUT_TYPE_MAP = mapOf(
            INPUT_TYPE_BUNDLED_TUNER to TvInputManagerHelper.TYPE_BUNDLED_TUNER,
            INPUT_TYPE_TUNER to TvInputInfo.TYPE_TUNER,
            INPUT_TYPE_CEC_LOGICAL to TvInputManagerHelper.TYPE_CEC_DEVICE,
            INPUT_TYPE_CEC_RECORDER to TvInputManagerHelper.TYPE_CEC_DEVICE_RECORDER,
            INPUT_TYPE_CEC_PLAYBACK to TvInputManagerHelper.TYPE_CEC_DEVICE_PLAYBACK,
            INPUT_TYPE_MHL_MOBILE to TvInputManagerHelper.TYPE_MHL_MOBILE,
            INPUT_TYPE_HDMI to TvInputInfo.TYPE_HDMI,
            INPUT_TYPE_DVI to TvInputInfo.TYPE_DVI,
            INPUT_TYPE_COMPONENT to TvInputInfo.TYPE_COMPONENT,
            INPUT_TYPE_SVIDEO to TvInputInfo.TYPE_SVIDEO,
            INPUT_TYPE_COMPOSITE to TvInputInfo.TYPE_COMPOSITE,
            INPUT_TYPE_DISPLAY_PORT to TvInputInfo.TYPE_DISPLAY_PORT,
            INPUT_TYPE_VGA to TvInputInfo.TYPE_VGA,
            INPUT_TYPE_SCART to TvInputInfo.TYPE_SCART,
            INPUT_TYPE_OTHER to TvInputInfo.TYPE_OTHER,
        )

        private val lock = Any()
        private var partner: Partner? = null

        @JvmStatic
        fun getInstance(context: Context): Partner {
            val pm = context.packageManager
            synchronized(lock) {
                getPartnerResolveInfo(pm)?.let { info ->
                    val pkg = info.activityInfo.packageName
                    try {
                        partner = Partner(pkg, info.activityInfo.name, pm.getResourcesForApplication(pkg))
                            .also { it.sendInitBroadcast(context) }
                    } catch (e: PackageManager.NameNotFoundException) {
                        Log.w(TAG, "Failed to find resources for $pkg")
                    }
                }
                return partner ?: Partner(null, null, null).also { partner = it }
            }
        }

        @JvmStatic
        fun reset(context: Context, packageName: String?) {
            synchronized(lock) {
                if (partner != null && !packageName.isNullOrEmpty() && packageName == partner?.packageName) {
                    // Neu laden, damit das aktualisierte Paket ein Init bekommt
                    partner = null
                    getInstance(context)
                }
            }
        }

        private fun getPartnerResolveInfo(pm: PackageManager): ResolveInfo? =
            pm.queryBroadcastReceivers(Intent(ACTION_PARTNER_CUSTOMIZATION), 0).firstOrNull(::isSystemApp)

        private fun isSystemApp(info: ResolveInfo): Boolean =
            (info.activityInfo?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM != 0
    }
}
