package com.android.tv.common.compat

import android.content.Context
import android.media.tv.TvView
import android.util.AttributeSet

/**
 * TvView-Basis. Das Original tunnelte über TvView-Events ein Protobuf-Protokoll (Signalstärke,
 * Dev-Toasts), das nur der eingebaute Google-Tuner sprach. Das Protokoll entfällt; die Hooks
 * bleiben, werden aber von Drittanbieter-Inputs nie ausgelöst.
 */
open class TvViewCompat @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : TvView(context, attrs, defStyleAttr) {

    open class TvInputCallbackCompat : TvInputCallback() {
        fun getTifCompatVersionForInput(inputId: String): Int = 0
        open fun onDevToast(inputId: String, message: String) {}
        open fun onSignalStrength(inputId: String, value: Int) {}
    }
}
