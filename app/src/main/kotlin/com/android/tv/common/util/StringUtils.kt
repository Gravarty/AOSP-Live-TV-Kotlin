package com.android.tv.common.util

object StringUtils {
    /** Null-sichere Variante von String.compareTo (null zuerst). */
    @JvmStatic
    fun compare(a: String?, b: String?): Int = when {
        a == null -> if (b == null) 0 else -1
        b == null -> 1
        else -> a.compareTo(b)
    }

    @JvmStatic
    fun nullToEmpty(s: String?): String = s ?: ""
}
