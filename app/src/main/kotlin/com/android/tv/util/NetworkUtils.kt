package com.android.tv.util

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.WorkerThread
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

object NetworkUtils {
    // Original nutzte http://; Klartext-HTTP ist ab Android 9 gesperrt, daher https.
    private const val GENERATE_204 = "https://clients3.google.com/generate_204"

    /** Echte Internetverbindung (Captive-Portal-Check über generate_204). */
    @JvmStatic
    @WorkerThread
    fun isNetworkAvailable(connectivityManager: ConnectivityManager?): Boolean {
        if (connectivityManager == null) return false
        val network = connectivityManager.activeNetwork ?: return false
        val caps = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(GENERATE_204).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                defaultUseCaches = false
                useCaches = false
            }
            connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT
        } catch (e: IOException) {
            false
        } finally {
            connection?.disconnect()
        }
    }
}
