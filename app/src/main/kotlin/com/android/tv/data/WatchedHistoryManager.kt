package com.android.tv.data

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.android.tv.common.util.SharedPreferencesUtils
import com.android.tv.data.api.Channel
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port von WatchedHistoryManager: Sehverlauf als Ringpuffer (max. 10000) in SharedPreferences.
 * AsyncTask ersetzt durch Coroutine auf Dispatchers.IO.
 */
@MainThread
class WatchedHistoryManager internal constructor(
    context: Context,
    private val maxHistorySize: Int,
) {
    constructor(context: Context) : this(context, MAX_HISTORY_SIZE)

    interface Listener {
        fun onLoadFinished()
        fun onNewRecordAdded(watchedRecord: WatchedRecord)
    }

    data class WatchedRecord(val channelId: Long, val watchedStartTime: Long, val duration: Long) {
        override fun toString() = "WatchedRecord: id=$channelId,watchedStartTime=$watchedStartTime,duration=$duration"
    }

    private val context = context.applicationContext
    private val handler = Handler(Looper.myLooper() ?: Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val watchedHistory = ArrayList<WatchedRecord>()
    private val pendingRecords = ArrayList<WatchedRecord>()
    private var lastIndex = 0L
    private var started = false
    var isLoaded = false
        private set
    private lateinit var sharedPreferences: SharedPreferences
    private var listener: Listener? = null

    // Neue Einträge aus anderen Prozessen/Instanzen übernehmen
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key != PREF_KEY_LAST_INDEX) return@OnSharedPreferenceChangeListener
        val newLastIndex = prefs.getLong(PREF_KEY_LAST_INDEX, -1)
        if (newLastIndex <= lastIndex) return@OnSharedPreferenceChangeListener
        handler.post {
            for (i in lastIndex + 1..newLastIndex) {
                val record = decode(sharedPreferences.getString(getSharedPreferencesKey(i), null)) ?: continue
                watchedHistory.add(record)
                listener?.onNewRecordAdded(record)
            }
            lastIndex = newLastIndex
        }
    }

    fun start() {
        if (started) return
        started = true
        if (Looper.myLooper() == Looper.getMainLooper()) {
            scope.launch {
                withContext(Dispatchers.IO) { loadWatchedHistory() }
                onLoadFinished()
            }
        } else {
            loadWatchedHistory()
            onLoadFinished()
        }
    }

    @WorkerThread
    private fun loadWatchedHistory() {
        sharedPreferences = context.getSharedPreferences(
            SharedPreferencesUtils.SHARED_PREF_WATCHED_HISTORY, Context.MODE_PRIVATE)
        lastIndex = sharedPreferences.getLong(PREF_KEY_LAST_INDEX, -1)
        val range = when {
            lastIndex < 0 -> LongRange.EMPTY
            lastIndex < maxHistorySize -> 0..lastIndex
            else -> (lastIndex - maxHistorySize + 1)..lastIndex
        }
        for (i in range) {
            decode(sharedPreferences.getString(getSharedPreferencesKey(i), null))?.let { watchedHistory.add(it) }
        }
    }

    private fun onLoadFinished() {
        isLoaded = true
        if (DEBUG) Log.d(TAG, "Loaded: size=${watchedHistory.size} index=$lastIndex")
        if (pendingRecords.isNotEmpty()) {
            val editor = sharedPreferences.edit()
            for (record in pendingRecords) {
                watchedHistory.add(record)
                ++lastIndex
                editor.putString(getSharedPreferencesKey(lastIndex), encode(record))
            }
            editor.putLong(PREF_KEY_LAST_INDEX, lastIndex).apply()
            pendingRecords.clear()
        }
        listener?.onLoadFinished()
        sharedPreferences.registerOnSharedPreferenceChangeListener(prefListener)
    }

    /** Speichert eine Sehdauer (unter 10 s wird ignoriert). */
    fun logChannelViewStop(channel: Channel, endTime: Long, duration: Long) {
        if (duration < MIN_DURATION_MS) return
        val record = WatchedRecord(channel.id, endTime - duration, duration)
        if (!isLoaded) {
            pendingRecords.add(record)
            return
        }
        watchedHistory.add(record)
        ++lastIndex
        sharedPreferences.edit()
            .putString(getSharedPreferencesKey(lastIndex), encode(record))
            .putLong(PREF_KEY_LAST_INDEX, lastIndex)
            .apply()
        listener?.onNewRecordAdded(record)
    }

    fun setListener(listener: Listener?) { this.listener = listener }

    fun getWatchedHistory(): List<WatchedRecord> = java.util.Collections.unmodifiableList(watchedHistory)

    internal fun getRecord(reverseIndex: Int): WatchedRecord = watchedHistory[watchedHistory.size - 1 - reverseIndex]

    internal fun getRecordFromSharedPreferences(reverseIndex: Int): WatchedRecord? {
        val index = sharedPreferences.getLong(PREF_KEY_LAST_INDEX, -1) - reverseIndex
        return decode(sharedPreferences.getString(getSharedPreferencesKey(index), null))
    }

    private fun getSharedPreferencesKey(index: Long) = (index % maxHistorySize).toString()

    internal fun encode(record: WatchedRecord) = "${record.channelId} ${record.watchedStartTime} ${record.duration}"

    internal fun decode(encoded: String?): WatchedRecord? {
        val parts = encoded?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 3) return null
        return WatchedRecord(
            parts[0].toLongOrNull() ?: return null,
            parts[1].toLongOrNull() ?: return null,
            parts[2].toLongOrNull() ?: return null,
        )
    }

    companion object {
        private const val TAG = "WatchedHistoryManager"
        private const val DEBUG = false
        private const val MAX_HISTORY_SIZE = 10000
        private const val PREF_KEY_LAST_INDEX = "last_index"
        private val MIN_DURATION_MS = TimeUnit.SECONDS.toMillis(10)
    }
}
