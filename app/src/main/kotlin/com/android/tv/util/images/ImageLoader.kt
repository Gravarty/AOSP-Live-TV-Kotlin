package com.android.tv.util.images

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.media.tv.TvInputInfo
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import androidx.tvprovider.media.tv.TvContractCompat.PreviewPrograms
import com.android.tv.R
import com.android.tv.common.concurrent.NamedThreadFactory
import com.android.tv.util.images.BitmapUtils.ScaledBitmapInfo
import java.lang.ref.WeakReference
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * 1:1-Port von com.android.tv.util.images.ImageLoader.
 * AsyncTask ersetzt durch Executor + Main-Handler; Verhalten (Pool, Queue, Ablehnung) unverändert.
 */
object ImageLoader {
    private const val TAG = "ImageLoader"
    private const val DEBUG = false

    private val CPU_COUNT = Runtime.getRuntime().availableProcessors()
    // 2..4 Kern-Threads, einer weniger als CPUs, um die CPU nicht zu sättigen
    private val CORE_POOL_SIZE = max(2, min(CPU_COUNT - 1, 4))
    private val MAXIMUM_POOL_SIZE = CPU_COUNT * 2 + 1
    private const val KEEP_ALIVE_SECONDS = 30L

    private val IMAGE_THREAD_POOL_EXECUTOR: Executor = ThreadPoolExecutor(
        CORE_POOL_SIZE, MAXIMUM_POOL_SIZE, KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
        LinkedBlockingQueue(128), NamedThreadFactory("ImageLoader"),
    ).apply { allowCoreThreadTimeOut(true) }

    /** Ersatz für AsyncTask.SERIAL_EXECUTOR (für Prefetch). */
    private val SERIAL_EXECUTOR: Executor =
        Executors.newSingleThreadExecutor(NamedThreadFactory("ImageLoaderSerial"))

    private val mainHandler = Handler(Looper.getMainLooper())

    /** Nur auf dem Main-Thread benutzt. */
    private val pendingListMap = HashMap<String, LoadBitmapTask<*>>()

    /** Hält das Ziel schwach; wird nur aufgerufen, solange es noch existiert. */
    abstract class ImageLoaderCallback<T>(referent: T) {
        private val weakReference = WeakReference(referent)

        internal fun onBitmapLoaded(bitmap: Bitmap?) {
            val referent = weakReference.get()
            if (referent != null) {
                onBitmapLoaded(referent, bitmap)
            } else if (DEBUG) {
                Log.d(TAG, "onBitmapLoaded not called because weak reference is gone")
            }
        }

        abstract fun onBitmapLoaded(referent: T, bitmap: Bitmap?)
    }

    /** Lädt ein Bild vorab in den Cache, ohne Callback. */
    @JvmStatic
    fun prefetchBitmap(context: Context, uriString: String, maxWidth: Int, maxHeight: Int) {
        if (DEBUG) Log.d(TAG, "prefetchBitmap() $uriString")
        if (Looper.getMainLooper() == Looper.myLooper()) {
            doLoadBitmap<Any>(context, uriString, maxWidth, maxHeight, null, SERIAL_EXECUTOR)
        } else {
            val appContext = context.applicationContext
            mainHandler.post {
                doLoadBitmap<Any>(appContext, uriString, maxWidth, maxHeight, null, SERIAL_EXECUTOR)
            }
        }
    }

    /**
     * Lädt ein Bild. Liegt es passend im Cache, wird der Callback sofort aufgerufen und true
     * zurückgegeben; sonst asynchron geladen und false zurückgegeben.
     */
    @JvmStatic
    @MainThread
    @JvmOverloads
    fun <T> loadBitmap(
        context: Context,
        uriString: String?,
        maxWidth: Int = Int.MAX_VALUE,
        maxHeight: Int = Int.MAX_VALUE,
        callback: ImageLoaderCallback<T>?,
    ): Boolean {
        if (DEBUG) Log.d(TAG, "loadBitmap() $uriString")
        return doLoadBitmap(context, uriString, maxWidth, maxHeight, callback, IMAGE_THREAD_POOL_EXECUTOR)
    }

    @JvmStatic
    @MainThread
    fun <T> loadBitmap(callback: ImageLoaderCallback<T>?, loadBitmapTask: LoadBitmapTask<T>): Boolean {
        if (DEBUG) Log.d(TAG, "loadBitmap() $loadBitmapTask")
        return doLoadBitmap(callback, IMAGE_THREAD_POOL_EXECUTOR, loadBitmapTask)
    }

    private fun <T> doLoadBitmap(
        context: Context,
        uriString: String?,
        maxWidth: Int,
        maxHeight: Int,
        callback: ImageLoaderCallback<T>?,
        executor: Executor,
    ): Boolean {
        // Cache vor dem Anlegen eines Tasks prüfen – deutlich billiger.
        val imageCache = ImageCache.getInstance()
        val info = imageCache.get(uriString)
        if (info != null && !info.needToReload(maxWidth, maxHeight)) {
            callback?.onBitmapLoaded(info.bitmap)
            return true
        }
        return doLoadBitmap(
            callback, executor,
            LoadBitmapFromUriTask(context, imageCache, uriString.orEmpty(), maxWidth, maxHeight),
        )
    }

    private fun <T> doLoadBitmap(
        callback: ImageLoaderCallback<T>?,
        executor: Executor,
        task: LoadBitmapTask<T>,
    ): Boolean {
        val info = task.getFromCache()
        if (info != null && !task.isReloadNeeded()) {
            callback?.onBitmapLoaded(info.bitmap)
            return true
        }
        @Suppress("UNCHECKED_CAST")
        val existing = pendingListMap[task.key] as LoadBitmapTask<T>?
        if (existing != null && !task.isReloadNeeded(existing)) {
            // Bereits geplant und groß genug.
            if (callback != null) existing.callbacks.add(callback)
        } else {
            if (callback != null) task.callbacks.add(callback)
            pendingListMap[task.key] = task
            try {
                task.executeOn(executor)
            } catch (e: RejectedExecutionException) {
                Log.e(TAG, "Failed to create new image loader", e)
                pendingListMap.remove(task.key)
            }
        }
        return false
    }

    /** Lädt ein Bild im Hintergrund, legt es in den Cache und benachrichtigt die Callbacks. */
    abstract class LoadBitmapTask<T>(
        context: Context,
        private val imageCache: ImageCache,
        val key: String,
        protected val maxHeight: Int,
        protected val maxWidth: Int,
    ) {
        protected val appContext: Context = context.applicationContext
        internal val callbacks = LinkedHashSet<ImageLoaderCallback<T>>()

        init {
            require(maxWidth != 0 && maxHeight != 0) {
                "Image size should not be 0. {width=$maxWidth, height=$maxHeight}"
            }
        }

        internal fun isReloadNeeded(): Boolean {
            val info = getFromCache()
            val needToReload = info != null && info.needToReload(maxWidth, maxHeight)
            if (DEBUG && needToReload) {
                Log.d(TAG, "Bitmap needs to be reloaded. {originalWidth=${info!!.bitmap.width}, " +
                    "originalHeight=${info.bitmap.height}, reqWidth=$maxWidth, reqHeight=$maxHeight}")
            }
            return needToReload
        }

        internal fun isReloadNeeded(other: LoadBitmapTask<*>): Boolean =
            (other.maxHeight != Int.MAX_VALUE && maxHeight >= other.maxHeight * 2) ||
                (other.maxWidth != Int.MAX_VALUE && maxWidth >= other.maxWidth * 2)

        fun getFromCache(): ScaledBitmapInfo? = imageCache.get(key)

        @WorkerThread
        abstract fun doGetBitmapInBackground(): ScaledBitmapInfo?

        internal fun executeOn(executor: Executor) {
            executor.execute {
                val result = doInBackground()
                mainHandler.post { onPostExecute(result) }
            }
        }

        @WorkerThread
        private fun doInBackground(): ScaledBitmapInfo? {
            var info = getFromCache()
            if (info != null && !isReloadNeeded()) return info
            info = doGetBitmapInBackground()
            if (info != null) imageCache.putIfNeeded(info)
            return info
        }

        @MainThread
        private fun onPostExecute(info: ScaledBitmapInfo?) {
            if (DEBUG) Log.d(TAG, "Bitmap is loaded $key")
            for (callback in callbacks) callback.onBitmapLoaded(info?.bitmap)
            pendingListMap.remove(key)
        }

        override fun toString() = "${javaClass.simpleName}($key ${maxWidth}x$maxHeight)"
    }

    private class LoadBitmapFromUriTask<T>(
        context: Context, imageCache: ImageCache, uriString: String, maxWidth: Int, maxHeight: Int,
    ) : LoadBitmapTask<T>(context, imageCache, uriString, maxHeight, maxWidth) {
        override fun doGetBitmapInBackground(): ScaledBitmapInfo? =
            BitmapUtils.decodeSampledBitmapFromUriString(appContext, key, maxWidth, maxHeight)
    }

    /** Lädt das Icon eines TV-Inputs (Größe: channel_banner_input_logo_size). */
    class LoadTvInputLogoTask<T>(context: Context, cache: ImageCache, private val info: TvInputInfo) :
        LoadBitmapTask<T>(
            context, cache, getTvInputLogoKey(info.id),
            context.resources.getDimensionPixelSize(R.dimen.channel_banner_input_logo_size),
            context.resources.getDimensionPixelSize(R.dimen.channel_banner_input_logo_size),
        ) {
        override fun doGetBitmapInBackground(): ScaledBitmapInfo? {
            val drawable = info.loadIcon(appContext)
            val bm = (drawable as? BitmapDrawable)?.bitmap ?: BitmapUtils.drawableToBitmap(drawable)
            return bm?.let { BitmapUtils.createScaledBitmapInfo(key, it, maxWidth, maxHeight) }
        }

        companion object {
            @JvmStatic
            fun getTvInputLogoKey(inputId: String) = "$inputId-logo"
        }
    }

    /** Nächstliegendes Seitenverhältnis aus den PreviewPrograms-Konstanten. */
    @JvmStatic
    @WorkerThread
    fun getAspectRatioFromPosterArtUri(context: Context, uriString: String): Int {
        // Bugfix: nicht ladbares Bild führte zu NPE → Standard 16:9
        val info = ImageCache.getInstance().get(uriString)
            ?: BitmapUtils.decodeSampledBitmapFromUriString(context, uriString, Int.MAX_VALUE, Int.MAX_VALUE)
            ?: return PreviewPrograms.ASPECT_RATIO_16_9
        val ratio = info.bitmap.width.toFloat() / info.bitmap.height
        return when {
            ratio > 0 && ratio <= 0.6803 -> PreviewPrograms.ASPECT_RATIO_2_3
            ratio > 0.6803 && ratio <= 0.8469 -> PreviewPrograms.ASPECT_RATIO_MOVIE_POSTER
            ratio > 0.8469 && ratio <= 1.1667 -> PreviewPrograms.ASPECT_RATIO_1_1
            ratio > 1.1667 && ratio <= 1.4167 -> PreviewPrograms.ASPECT_RATIO_4_3
            ratio > 1.4167 && ratio <= 1.6389 -> PreviewPrograms.ASPECT_RATIO_3_2
            else -> PreviewPrograms.ASPECT_RATIO_16_9
        }
    }
}
