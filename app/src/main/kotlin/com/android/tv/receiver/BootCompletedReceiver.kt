package com.android.tv.receiver

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import com.android.tv.Starter
import com.android.tv.TvActivity
import com.android.tv.TvSingletons
import com.android.tv.features.TvFeatures
import com.android.tv.recommendation.ChannelPreviewUpdater
import com.android.tv.util.OnboardingUtils
import com.android.tv.util.SetupUtils

/** Nach dem Booten: Vorschau-Kanäle, EPG-Rechte, App-Symbol, DVR-Scheduler. */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TvSingletons.getSingletons(context).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager")
            return
        }
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            Log.w(TAG, "invalid action ${intent.action}")
            return
        }
        Starter.start(context)
        ChannelPreviewUpdater.getInstance(context).updatePreviewDataForChannelsImmediately()
        SetupUtils.grantEpgPermissionToSetUpPackages(context)
        // App-Symbol beim ersten Boot einblenden
        if (TvFeatures.isUnhideEnabled(context) && OnboardingUtils.isFirstBoot(context)) {
            val pm = context.packageManager
            val name = ComponentName(context, TvActivity::class.java)
            if (pm.getComponentEnabledSetting(name) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(name, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0)
            }
            OnboardingUtils.setFirstBootCompleted(context)
        }
        TvSingletons.getSingletons(context).getRecordingScheduler()?.updateAndStartServiceIfNeeded()
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
