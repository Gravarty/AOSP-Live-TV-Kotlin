package com.android.tv.util.images

import android.content.ContentResolver
import android.content.Context
import android.database.sqlite.SQLiteException
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.TrafficStats
import android.net.Uri
import android.util.Log
import com.android.tv.common.util.NetworkTrafficTags
import java.io.BufferedInputStream
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLConnection
import kotlin.math.max
import kotlin.math.roundToInt

/** 1:1-Port von com.android.tv.util.images.BitmapUtils. */
object BitmapUtils {
    private const val TAG = "BitmapUtils"
    private const val DEBUG = false

    // 64K = 8x Standardpuffer von BufferedInputStream (8K)
    private const val MARK_READ_LIMIT = 64 * 1024
    private const val CONNECTION_TIMEOUT_MS = 3000
    private const val READ_TIMEOUT_MS = 10000

    @JvmStatic
    fun scaleBitmap(bm: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val rect = calculateNewSize(bm, maxWidth, maxHeight)
        return Bitmap.createScaledBitmap(bm, rect.right, rect.bottom, false)
    }

    @JvmStatic
    fun getScaledMutableBitmap(bm: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val scaled = scaleBitmap(bm, maxWidth, maxHeight)
        return if (scaled.isMutable) scaled else scaled.copy(Bitmap.Config.ARGB_8888, true)
    }

    private fun calculateNewSize(bm: Bitmap, maxWidth: Int, maxHeight: Int): Rect {
        val ratio = maxHeight / maxWidth.toDouble()
        val bmRatio = bm.height / bm.width.toDouble()
        return Rect().apply {
            if (ratio > bmRatio) {
                right = maxWidth
                bottom = (bm.height.toFloat() * maxWidth / bm.width).roundToInt()
            } else {
                right = (bm.width.toFloat() * maxHeight / bm.height).roundToInt()
                bottom = maxHeight
            }
        }
    }

    @JvmStatic
    fun createScaledBitmapInfo(id: String, bm: Bitmap, maxWidth: Int, maxHeight: Int) =
        ScaledBitmapInfo(
            id,
            scaleBitmap(bm, maxWidth, maxHeight),
            calculateInSampleSize(bm.width, bm.height, maxWidth, maxHeight),
        )

    @JvmStatic
    fun drawableToBitmap(drawable: Drawable?): Bitmap? {
        if (drawable == null) return null
        val bm = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bm)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        return bm
    }

    /** Lädt ein Bitmap aus einer content://-, android.resource://-, file://- oder HTTP-URI. */
    @JvmStatic
    fun decodeSampledBitmapFromUriString(
        context: Context, uriString: String?, reqWidth: Int, reqHeight: Int,
    ): ScaledBitmapInfo? {
        if (uriString.isNullOrEmpty()) return null
        val uri = Uri.parse(uriString).normalizeScheme()
        val isResourceUri = isContentResolverUri(uri)
        var urlConnection: URLConnection? = null
        var inputStream: InputStream? = null
        val oldTag = TrafficStats.getThreadStatsTag()
        TrafficStats.setThreadStatsTag(NetworkTrafficTags.LOGO_FETCHER)
        try {
            if (isResourceUri) {
                inputStream = context.contentResolver.openInputStream(uri)
            } else {
                // HttpURLConnection muss explizit getrennt werden (siehe close()).
                urlConnection = getUrlConnection(uriString)
                inputStream = urlConnection.getInputStream()
            }
            inputStream = BufferedInputStream(inputStream)
            inputStream.mark(MARK_READ_LIMIT)

            // Abmessungen prüfen
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(inputStream, null, options)

            // Stream zurückspulen, sonst neu öffnen
            try {
                inputStream.reset()
            } catch (e: IOException) {
                if (DEBUG) Log.i(TAG, "Failed to rewind stream: $uriString", e)
                close(inputStream, urlConnection)
                if (isResourceUri) {
                    inputStream = context.contentResolver.openInputStream(uri)
                } else {
                    urlConnection = getUrlConnection(uriString)
                    inputStream = urlConnection.getInputStream()
                }
            }

            options.inJustDecodeBounds = false
            options.inPreferredConfig = Bitmap.Config.RGB_565
            options.inSampleSize =
                calculateInSampleSize(options.outWidth, options.outHeight, reqWidth, reqHeight)
            val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
            return ScaledBitmapInfo(uriString, bitmap, options.inSampleSize)
        } catch (e: IOException) {
            // Normal, z.B. wenn ein Kanal kein Logo hat.
            if (DEBUG) Log.w(TAG, "Failed to open stream: $uriString", e)
            return null
        } catch (e: SQLiteException) {
            Log.e(TAG, "Failed to open stream: $uriString", e)
            return null
        } finally {
            close(inputStream, urlConnection)
            TrafficStats.setThreadStatsTag(oldTag)
        }
    }

    private fun getUrlConnection(uriString: String): URLConnection =
        URL(uriString).openConnection().apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
        }

    /** Größte Zweierpotenz, bei der Breite oder Höhe noch >= der angeforderten Größe bleibt. */
    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        val ratio = max(width / reqWidth, height / reqHeight)
        return max(1, Integer.highestOneBit(ratio))
    }

    private fun isContentResolverUri(uri: Uri): Boolean = uri.scheme in setOf(
        ContentResolver.SCHEME_CONTENT,
        ContentResolver.SCHEME_ANDROID_RESOURCE,
        ContentResolver.SCHEME_FILE,
    )

    private fun close(closeable: Closeable?, urlConnection: URLConnection?) {
        try {
            closeable?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error closing $closeable", e)
        }
        (urlConnection as? HttpURLConnection)?.disconnect()
    }

    @JvmStatic
    fun setColorFilterToDrawable(color: Int, drawable: Drawable?) {
        drawable?.mutate()?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_ATOP)
    }

    class ScaledBitmapInfo(
        @JvmField val id: String,
        @JvmField val bitmap: Bitmap,
        @JvmField val inSampleSize: Int,
    ) {
        /** True, wenn ein neu geladenes Bild für die gewünschte Größe deutlich schärfer wäre. */
        fun needToReload(reqWidth: Int, reqHeight: Int): Boolean {
            if (inSampleSize <= 1) {
                if (DEBUG) Log.d(TAG, "Reload not required $this already full size.")
                return false
            }
            val size = calculateNewSize(bitmap, reqWidth, reqHeight)
            val reload = size.right >= bitmap.width * 2 || size.bottom >= bitmap.height * 2
            if (DEBUG) Log.d(TAG, "needToReload($reqWidth, $reqHeight)=$reload because the new size would be $size for $this")
            return reload
        }

        fun needToReload(other: ScaledBitmapInfo): Boolean =
            needToReload(other.bitmap.width, other.bitmap.height)

        override fun toString() =
            "ScaledBitmapInfo[$id](in=$inSampleSize, w=${bitmap.width}, h=${bitmap.height})"
    }
}
