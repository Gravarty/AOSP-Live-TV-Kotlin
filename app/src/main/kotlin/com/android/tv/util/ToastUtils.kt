package com.android.tv.util

import android.content.Context
import android.widget.Toast
import androidx.annotation.MainThread
import java.lang.ref.WeakReference

/** Zeigt einen Toast und schließt den vorherigen. */
object ToastUtils {
    private var toast: WeakReference<Toast>? = null

    @JvmStatic
    @MainThread
    fun show(context: Context, text: CharSequence, duration: Int) {
        toast?.get()?.cancel()
        toast = WeakReference(Toast.makeText(context, text, duration).also { it.show() })
    }
}
