package com.android.tv.recommendation

import com.android.tv.data.WatchedPrograms
import android.content.Context
import android.database.ContentObserver
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvInputManager.TvInputCallback
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Message
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.android.tv.TvSingletons
import com.android.tv.common.util.PermissionUtils
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.ProgramImpl
import com.android.tv.data.WatchedHistoryManager
import com.android.tv.data.api.Channel
import java.util.concurrent.ConcurrentHashMap

/**
 * Gemeinsame Datenbasis der Empfehlungen: sichtbare Kanäle + Sehverlauf (TvProvider oder
 * eigener WatchedHistoryManager). Arbeitet auf einem eigenen HandlerThread; Listener auf Main.
 */
class RecommendationDataManager private constructor(context: Context) : WatchedHistoryManager.Listener {

    interface Listener {
        fun onChannelRecordLoaded()
        fun onNewWatchLog(channelRecord: ChannelRecord)
        fun onChannelRecordChanged()
    }

    private val context = context.applicationContext
    private val channelRecordMap: MutableMap<Long, ChannelRecord> = ConcurrentHashMap()
    private val availableChannelRecordMap: MutableMap<Long, ChannelRecord> = ConcurrentHashMap()
    @Volatile private var started = false
    @Volatile private var cancelLoadTask = false
    @Volatile private var channelRecordMapLoaded = false
    private var indexWatchChannelId = -1
    private var indexProgramTitle = -1
    private var indexProgramStartTime = -1
    private var indexProgramEndTime = -1
    private var indexWatchStartTime = -1
    private var indexWatchEndTime = -1
    private var tvInputManager: TvInputManager? = null
    private val inputs: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val handlerThread = HandlerThread("RecommendationDataManager").also { it.start() }
    private val handler = Handler(handlerThread.looper) { msg -> handleMessage(msg); true }
    private val mainHandler = Handler(Looper.getMainLooper())
    private var watchedHistoryManager: WatchedHistoryManager? = null
    private val channelDataManager: ChannelDataManager = TvSingletons.getSingletons(this.context).getChannelDataManager()
    private val listeners = ArrayList<Listener>()

    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            // Nur einzelne WatchedPrograms-Einträge (…/watched_program/<id>)
            if (uri != null && isWatchedProgramIdUri(uri) &&
                !handler.hasMessages(MSG_UPDATE_WATCH_HISTORY, WatchedPrograms.CONTENT_URI)
            ) {
                handler.obtainMessage(MSG_UPDATE_WATCH_HISTORY, uri).sendToTarget()
            }
        }
    }

    private val channelDataListener = object : ChannelDataManager.Listener {
        override fun onLoadFinished() = updateChannelData()
        override fun onChannelListUpdated() = updateChannelData()
        override fun onChannelBrowsableChanged() = updateChannelData()
    }

    private val internalCallback = object : TvInputCallback() {
        override fun onInputAdded(inputId: String) {
            if (!started) return
            inputs.add(inputId)
            if (!channelRecordMapLoaded) return
            var changed = false
            for (record in channelRecordMap.values) {
                if (record.channel.inputId == inputId) {
                    record.isInputRemoved = false
                    availableChannelRecordMap[record.channel.id] = record
                    changed = true
                }
            }
            if (changed && !handler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)
            }
        }

        override fun onInputRemoved(inputId: String) {
            if (!started) return
            inputs.remove(inputId)
            if (!channelRecordMapLoaded) return
            var changed = false
            for (record in channelRecordMap.values) {
                if (record.channel.inputId == inputId) {
                    record.isInputRemoved = true
                    availableChannelRecordMap.remove(record.channel.id)
                    changed = true
                }
            }
            if (changed && !handler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
                handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)
            }
        }
    }

    init {
        runOnMainThread(::start)
    }

    fun release(listener: Listener) = runOnMainThread {
        listeners.remove(listener)
        if (listeners.isEmpty()) stop()
    }

    fun getChannelRecord(channelId: Long): ChannelRecord? = availableChannelRecordMap[channelId]
    val channelRecordCount: Int get() = availableChannelRecordMap.size
    fun getChannelRecords(): Collection<ChannelRecord> = java.util.Collections.unmodifiableCollection(availableChannelRecordMap.values)

    @MainThread
    private fun start() {
        handler.sendEmptyMessage(MSG_START)
        channelDataManager.addListener(channelDataListener)
        if (channelDataManager.isDbLoadFinished) updateChannelData()
    }

    @MainThread
    private fun stop() {
        watchedHistoryManager?.setListener(null)
        for (what in MSG_FIRST..MSG_LAST) handler.removeMessages(what)
        channelDataManager.removeListener(channelDataListener)
        handler.sendEmptyMessage(MSG_STOP)
        handlerThread.quitSafely()
        mainHandler.removeCallbacksAndMessages(null)
        synchronized(Companion) { if (manager === this) manager = null }
    }

    @MainThread
    private fun updateChannelData() {
        handler.removeMessages(MSG_UPDATE_CHANNELS)
        handler.obtainMessage(MSG_UPDATE_CHANNELS, channelDataManager.getBrowsableChannelList()).sendToTarget()
    }

    private fun addListener(listener: Listener) = runOnMainThread { listeners.add(listener) }

    @WorkerThread
    private fun onStart() {
        if (!started) {
            started = true
            cancelLoadTask = false
            if (!PermissionUtils.hasAccessWatchedHistory(context)) {
                watchedHistoryManager = WatchedHistoryManager(context).also {
                    it.setListener(this)
                    it.start()
                }
            } else {
                context.contentResolver.registerContentObserver(WatchedPrograms.CONTENT_URI, true, contentObserver)
                handler.obtainMessage(MSG_UPDATE_WATCH_HISTORY, WatchedPrograms.CONTENT_URI).sendToTarget()
            }
            val manager = context.getSystemService(TvInputManager::class.java)
            tvInputManager = manager
            manager.registerCallback(internalCallback, handler)
            manager.tvInputList.forEach { inputs.add(it.id) }
        }
        if (channelRecordMapLoaded) handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED)
    }

    @WorkerThread
    private fun onStop() {
        context.contentResolver.unregisterContentObserver(contentObserver)
        cancelLoadTask = true
        channelRecordMap.clear()
        availableChannelRecordMap.clear()
        inputs.clear()
        tvInputManager?.unregisterCallback(internalCallback)
        started = false
    }

    @WorkerThread
    private fun onUpdateChannels(channels: List<Channel>) {
        var changed = false
        val removedChannelIds = HashSet(channelRecordMap.keys)
        for (channel in channels) {
            if (updateChannelRecordMapFromChannel(channel)) changed = true
            removedChannelIds.remove(channel.id)
        }
        for (channelId in removedChannelIds) {
            channelRecordMap.remove(channelId)
            if (availableChannelRecordMap.remove(channelId) != null) changed = true
        }
        if (changed && channelRecordMapLoaded && !handler.hasMessages(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)) {
            handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED)
        }
    }

    /** Sehverlauf aus dem TvProvider (nur mit Systemrecht ACCESS_WATCHED_PROGRAMS). */
    @WorkerThread
    private fun onLoadWatchHistory(uri: Uri) {
        val history = ArrayList<WatchedProgram>()
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToLast()) {
                    do {
                        if (cancelLoadTask) return
                        history.add(createWatchedProgramFromWatchedProgramCursor(cursor))
                    } while (cursor.moveToPrevious())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error trying to load watch history from $uri", e)
            return
        }
        for (watchedProgram in history) {
            val record = updateChannelRecordFromWatchedProgram(watchedProgram)
            if (channelRecordMapLoaded && record != null) runOnMainThread { listeners.toList().forEach { it.onNewWatchLog(record) } }
        }
        if (!channelRecordMapLoaded) handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED)
    }

    private fun convertFromWatchedHistoryManagerRecords(record: WatchedHistoryManager.WatchedRecord): WatchedProgram {
        val endTime = record.watchedStartTime + record.duration
        val program = ProgramImpl.Builder()
            .setChannelId(record.channelId)
            .setTitle("")
            .setStartTimeUtcMillis(record.watchedStartTime)
            .setEndTimeUtcMillis(endTime)
            .build()
        return WatchedProgram(program, record.watchedStartTime, endTime)
    }

    override fun onLoadFinished() {
        watchedHistoryManager?.getWatchedHistory()?.forEach {
            updateChannelRecordFromWatchedProgram(convertFromWatchedHistoryManagerRecords(it))
        }
        handler.sendEmptyMessage(MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED)
    }

    override fun onNewRecordAdded(watchedRecord: WatchedHistoryManager.WatchedRecord) {
        val record = updateChannelRecordFromWatchedProgram(convertFromWatchedHistoryManagerRecords(watchedRecord))
        if (channelRecordMapLoaded && record != null) runOnMainThread { listeners.toList().forEach { it.onNewWatchLog(record) } }
    }

    private fun createWatchedProgramFromWatchedProgramCursor(cursor: android.database.Cursor): WatchedProgram {
        if (indexWatchChannelId == -1) {
            indexWatchChannelId = cursor.getColumnIndex(WatchedPrograms.COLUMN_CHANNEL_ID)
            indexProgramTitle = cursor.getColumnIndex(WatchedPrograms.COLUMN_TITLE)
            indexProgramStartTime = cursor.getColumnIndex(WatchedPrograms.COLUMN_START_TIME_UTC_MILLIS)
            indexProgramEndTime = cursor.getColumnIndex(WatchedPrograms.COLUMN_END_TIME_UTC_MILLIS)
            indexWatchStartTime = cursor.getColumnIndex(WatchedPrograms.COLUMN_WATCH_START_TIME_UTC_MILLIS)
            indexWatchEndTime = cursor.getColumnIndex(WatchedPrograms.COLUMN_WATCH_END_TIME_UTC_MILLIS)
        }
        val program = ProgramImpl.Builder()
            .setChannelId(cursor.getLong(indexWatchChannelId))
            .setTitle(cursor.getString(indexProgramTitle))
            .setStartTimeUtcMillis(cursor.getLong(indexProgramStartTime))
            .setEndTimeUtcMillis(cursor.getLong(indexProgramEndTime))
            .build()
        return WatchedProgram(program, cursor.getLong(indexWatchStartTime), cursor.getLong(indexWatchEndTime))
    }

    private fun onNotifyChannelRecordMapLoaded() {
        channelRecordMapLoaded = true
        runOnMainThread { listeners.toList().forEach { it.onChannelRecordLoaded() } }
    }

    private fun onNotifyChannelRecordMapChanged() =
        runOnMainThread { listeners.toList().forEach { it.onChannelRecordChanged() } }

    /** true, wenn sich die verfügbaren Kanäle geändert haben. */
    private fun updateChannelRecordMapFromChannel(channel: Channel): Boolean {
        if (!channel.isBrowsable) {
            channelRecordMap.remove(channel.id)
            return availableChannelRecordMap.remove(channel.id) != null
        }
        val inputRemoved = channel.inputId !in inputs
        val record = channelRecordMap[channel.id]
        if (record == null) {
            val newRecord = ChannelRecord(context, channel, inputRemoved)
            channelRecordMap[channel.id] = newRecord
            if (!inputRemoved) {
                availableChannelRecordMap[channel.id] = newRecord
                return true
            }
            return false
        }
        val oldInputRemoved = record.isInputRemoved
        record.setChannel(channel, inputRemoved)
        return oldInputRemoved != inputRemoved
    }

    private fun updateChannelRecordFromWatchedProgram(program: WatchedProgram?): ChannelRecord? {
        if (program == null || program.watchEndTimeMs == 0L) return null
        val record = channelRecordMap[program.program.channelId]
        if (record != null && record.lastWatchEndTimeMs < program.watchEndTimeMs) record.logWatchHistory(program)
        return record
    }

    private fun runOnMainThread(r: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) r() else mainHandler.post(r)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_START -> onStart()
            MSG_STOP -> if (started) onStop()
            MSG_UPDATE_CHANNELS -> if (started) onUpdateChannels(msg.obj as List<Channel>)
            MSG_UPDATE_WATCH_HISTORY -> if (started) onLoadWatchHistory(msg.obj as Uri)
            MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED -> if (started) onNotifyChannelRecordMapLoaded()
            MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED -> if (started) onNotifyChannelRecordMapChanged()
        }
    }

    companion object {
        private const val TAG = "RecommendationDataManag"
        private const val MSG_START = 1000
        private const val MSG_STOP = 1001
        private const val MSG_UPDATE_CHANNELS = 1002
        private const val MSG_UPDATE_WATCH_HISTORY = 1003
        private const val MSG_NOTIFY_CHANNEL_RECORD_MAP_LOADED = 1004
        private const val MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED = 1005
        private const val MSG_FIRST = MSG_START
        private const val MSG_LAST = MSG_NOTIFY_CHANNEL_RECORD_MAP_CHANGED

        private var manager: RecommendationDataManager? = null

        /** Gemeinsame Instanz holen und [listener] anmelden. */
        @JvmStatic
        @Synchronized
        fun acquireManager(context: Context, listener: Listener): RecommendationDataManager =
            (manager ?: RecommendationDataManager(context).also { manager = it }).also { it.addListener(listener) }

        /** Ersatz für TvUriMatcher.MATCH_WATCHED_PROGRAM_ID. */
        private fun isWatchedProgramIdUri(uri: Uri): Boolean {
            val segments = uri.pathSegments
            return uri.authority == TvContract.AUTHORITY && segments.size == 2 &&
                segments[0] == "watched_program" && segments[1].toLongOrNull() != null
        }
    }
}
