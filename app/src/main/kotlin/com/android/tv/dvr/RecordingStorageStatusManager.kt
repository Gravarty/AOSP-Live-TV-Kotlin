package com.android.tv.dvr

import android.content.BroadcastReceiver
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.Looper
import android.os.StatFs
import android.util.Log
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.core.content.ContextCompat
import com.android.tv.common.SoftPreconditions
import java.io.File
import java.io.IOException
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Status des Aufnahmespeichers (eingehängt, Kapazität ≥ 50 GB, frei ≥ 10 GB) – für den
 * eingebauten Tuner und die Speicher-Prüfung vor Aufnahmen.
 */
open class RecordingStorageStatusManager(private val context: Context) {

    fun interface OnStorageMountChangedListener {
        fun onStorageMountChanged(storageMounted: Boolean)
    }

    private data class MountedStorageStatus(val mounted: Boolean, val mountedDir: File?, val capacity: Long) {
        fun isValidForDvr() = mounted && capacity >= MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES
    }

    private val listeners = CopyOnWriteArraySet<OnStorageMountChangedListener>()
    @Volatile private var mountedStorageStatus = getStorageStatusInternal()
    private var storageValid = mountedStorageStatus.isValidForDvr()

    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme(ContentResolver.SCHEME_FILE)
        }
        ContextCompat.registerReceiver(context, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val result = getStorageStatusInternal()
                if (mountedStorageStatus == result) return
                mountedStorageStatus = result
                if (result.mounted) cleanUpDbIfNeeded()
                val valid = result.isValidForDvr()
                if (valid == storageValid) return
                storageValid = valid
                listeners.forEach { it.onStorageMountChanged(valid) }
            }
        }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    fun addListener(listener: OnStorageMountChangedListener) { listeners.add(listener) }
    fun removeListener(listener: OnStorageMountChangedListener) { listeners.remove(listener) }

    val isStorageMounted: Boolean get() = mountedStorageStatus.mounted

    @WorkerThread
    fun getRecordingRootDataDirectory(): File? {
        SoftPreconditions.checkState(Looper.myLooper() != Looper.getMainLooper())
        if (mountedStorageStatus.mountedDir == null) return null
        val rootPath = try {
            context.getExternalFilesDir(null)?.canonicalPath
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
        return rootPath?.let { File(it + RECORDING_DATA_SUB_PATH) }
    }

    @AnyThread
    fun getDvrStorageStatus(): Int {
        val status = mountedStorageStatus
        val dir = status.mountedDir ?: return STORAGE_STATUS_MISSING
        // CommonFeatures.FORCE_RECORDING_UNTIL_NO_SPACE ist im Original aus
        if (status.capacity < MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES) return STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL
        return try {
            if (StatFs(dir.toString()).availableBytes < MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES) STORAGE_STATUS_FREE_SPACE_INSUFFICIENT
            else STORAGE_STATUS_OK
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Error getting Dvr Storage Status.", e)
            SoftPreconditions.checkState(false)
            STORAGE_STATUS_FREE_SPACE_INSUFFICIENT
        }
    }

    val isStorageSufficient: Boolean get() = getDvrStorageStatus() == STORAGE_STATUS_OK

    protected open fun cleanUpDbIfNeeded() {}

    @Suppress("DEPRECATION")
    private fun getStorageStatusInternal(): MountedStorageStatus {
        var mounted = Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
        var dir: File? = if (mounted) Environment.getExternalStorageDirectory() else null
        mounted = mounted && dir != null
        var capacity = 0L
        if (mounted) {
            try {
                capacity = StatFs(dir.toString()).totalBytes
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Storage mount status was changed.", e)
                mounted = false
                dir = null
            }
        }
        return MountedStorageStatus(mounted, dir, capacity)
    }

    companion object {
        private const val TAG = "RecordingStorageStatusManager"
        const val MIN_STORAGE_SIZE_FOR_DVR_IN_BYTES = 50 * 1024 * 1024 * 1024L
        private const val MIN_FREE_STORAGE_SIZE_FOR_DVR_IN_BYTES = 10 * 1024 * 1024 * 1024L
        private const val RECORDING_DATA_SUB_PATH = "/recording"
        const val STORAGE_STATUS_OK = 0
        const val STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL = 1
        const val STORAGE_STATUS_FREE_SPACE_INSUFFICIENT = 2
        const val STORAGE_STATUS_MISSING = 3
    }
}
