package com.android.tv.common.util

import android.content.Context
import android.content.pm.PackageManager

/** Berechtigungsprüfungen (Ergebnis wird wie im Original gecacht, wo es dort gecacht war). */
object PermissionUtils {
    const val PERMISSION_READ_TV_LISTINGS = "android.permission.READ_TV_LISTINGS"

    private var hasAccessAllEpg: Boolean? = null
    private var hasAccessWatchedHistory: Boolean? = null

    private fun Context.granted(permission: String) =
        checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    @JvmStatic
    fun hasAccessAllEpg(context: Context): Boolean =
        hasAccessAllEpg ?: context.granted("com.android.providers.tv.permission.ACCESS_ALL_EPG_DATA")
            .also { hasAccessAllEpg = it }

    @JvmStatic
    fun hasAccessWatchedHistory(context: Context): Boolean =
        hasAccessWatchedHistory ?: context.granted("com.android.providers.tv.permission.ACCESS_WATCHED_PROGRAMS")
            .also { hasAccessWatchedHistory = it }

    @JvmStatic
    fun hasReadTvListings(context: Context): Boolean = context.granted(PERMISSION_READ_TV_LISTINGS)

    @JvmStatic
    fun hasInternet(context: Context): Boolean = context.granted("android.permission.INTERNET")

    @JvmStatic
    fun hasWriteExternalStorage(context: Context): Boolean =
        context.granted("android.permission.WRITE_EXTERNAL_STORAGE")
}
