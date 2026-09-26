package com.android.tv.common.util

import android.content.ContentUris
import android.net.Uri
import android.util.Log

object ContentUriUtils {
    private const val TAG = "ContentUriUtils"

    /** ID aus einer Content-URI oder -1, wenn keine gültige ID enthalten ist. */
    @JvmStatic
    fun safeParseId(uri: Uri?): Long = try {
        ContentUris.parseId(uri!!)
    } catch (e: Exception) {
        Log.d(TAG, "Error parsing $uri", e)
        -1
    }
}
