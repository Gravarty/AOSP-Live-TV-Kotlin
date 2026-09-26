package com.android.tv.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.android.tv.Starter
import com.android.tv.TvApplication
import com.android.tv.TvSingletons
import com.android.tv.util.Partner

/** Paket installiert/entfernt: App-Symbol neu prüfen, Partner-Cache zurücksetzen. */
class PackageIntentsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (!TvSingletons.getSingletons(context).getTvInputManagerHelper().hasTvInputManager()) {
            Log.wtf(TAG, "Stopping because device does not have a TvInputManager")
            return
        }
        Starter.start(context)
        (context.applicationContext as TvApplication).handleInputCountChanged()
        Partner.reset(context, intent.data?.schemeSpecificPart)
    }

    companion object {
        private const val TAG = "PackageIntentsReceiver"
    }
}
