package com.android.tv.util.images

import android.util.Log
import android.util.LruCache
import com.android.tv.common.memory.MemoryManageable
import com.android.tv.util.images.BitmapUtils.ScaledBitmapInfo
import kotlin.math.max
import kotlin.math.roundToInt

/** 1:1-Port von com.android.tv.util.images.ImageCache (Speicher-LRU in KB). */
class ImageCache private constructor(memCacheSizePercent: Float) : MemoryManageable {

    private val memoryCache: LruCache<String, ScaledBitmapInfo> =
        object : LruCache<String, ScaledBitmapInfo>(calculateMemCacheSize(memCacheSizePercent)) {
            override fun sizeOf(key: String, value: ScaledBitmapInfo): Int =
                (value.bitmap.byteCount + 1023) / 1024
        }

    init {
        if (DEBUG) Log.d(TAG, "Memory cache created (size = ${memoryCache.maxSize()} Kbytes)")
    }

    /** Legt das Bild ab, behält aber ein vorhandenes, das größer ist. */
    fun putIfNeeded(bitmapInfo: ScaledBitmapInfo) {
        val key = bitmapInfo.id
        synchronized(memoryCache) {
            val old = memoryCache.put(key, bitmapInfo)
            if (old != null && !old.needToReload(bitmapInfo)) {
                memoryCache.put(key, old)
                if (DEBUG) Log.d(TAG, "Kept original $old in memory cache because it was larger than $bitmapInfo.")
            } else if (DEBUG) {
                Log.d(TAG, "Add $bitmapInfo to memory cache. Current size is ${memoryCache.size()} / ${memoryCache.maxSize()} Kbytes")
            }
        }
    }

    fun get(key: String?): ScaledBitmapInfo? {
        if (key == null) return null
        val info = memoryCache.get(key)
        if (DEBUG) {
            val hit = memoryCache.hitCount()
            val miss = memoryCache.missCount()
            Log.d(TAG, "Memory cache ${if (info == null) "miss" else "hit"} for  $key")
            Log.d(TAG, "Memory cache ${hit}h:${miss}m ${hit.toDouble() / (hit + miss) * 100}%")
        }
        return info
    }

    fun remove(key: String): ScaledBitmapInfo? = memoryCache.remove(key)

    override fun performTrimMemory(level: Int) = memoryCache.evictAll()

    companion object {
        private const val TAG = "ImageCache"
        private const val DEBUG = false
        private const val MAX_CACHE_SIZE_PERCENT = 0.8f
        private const val MIN_CACHE_SIZE_PERCENT = 0.05f
        private const val DEFAULT_CACHE_SIZE_PERCENT = 0.1f
        private const val MIN_CACHE_SIZE_KBYTES = 1024

        @Volatile private var instance: ImageCache? = null

        @JvmStatic
        @Synchronized
        fun getInstance(memCacheSizePercent: Float = DEFAULT_CACHE_SIZE_PERCENT): ImageCache =
            instance ?: ImageCache(memCacheSizePercent).also { instance = it }

        @JvmStatic
        fun calculateMemCacheSize(percent: Float): Int {
            require(percent in MIN_CACHE_SIZE_PERCENT..MAX_CACHE_SIZE_PERCENT) {
                "setMemCacheSizePercent - percent must be between 0.05 and 0.8 (inclusive)"
            }
            return max(MIN_CACHE_SIZE_KBYTES, (percent * Runtime.getRuntime().maxMemory() / 1024).roundToInt())
        }
    }
}
