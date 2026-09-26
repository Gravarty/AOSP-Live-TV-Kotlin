package com.android.tv.common

import android.util.Log

/** Weiche Vorbedingungen: loggen statt abstürzen (Verhalten des Release-Builds im Original). */
object SoftPreconditions {
    @JvmStatic
    fun checkState(expression: Boolean, tag: String? = null, message: String? = null): Boolean {
        if (!expression) {
            Log.w(tag ?: "SoftPreconditions", "Illegal State: $message", IllegalStateException(message))
        }
        return expression
    }

    @JvmStatic
    fun checkArgument(expression: Boolean, tag: String? = null, message: String? = null, vararg args: Any?): Boolean {
        if (!expression) {
            val msg = "$message ${args.joinToString()}"
            Log.w(tag ?: "SoftPreconditions", "Illegal argument: $msg", IllegalArgumentException(msg))
        }
        return expression
    }

    /** Loggt bei null eine Warnung und gibt [reference] unverändert zurück. */
    @JvmStatic
    fun <T> checkNotNull(reference: T?): T? {
        if (reference == null) Log.w("SoftPreconditions", "Null pointer", NullPointerException())
        return reference
    }
}
