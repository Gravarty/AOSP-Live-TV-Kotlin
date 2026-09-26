package com.android.tv

import android.content.Context

/** Initialisiert die App beim Start einer Activity/eines Service/Receivers. */
interface Starter {
    fun start()

    companion object {
        @JvmStatic
        fun start(context: Context) {
            (context.applicationContext as? Starter)?.start()
        }
    }
}
