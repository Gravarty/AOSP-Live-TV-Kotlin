package com.android.tv.dvr

import android.content.Context
import android.media.tv.TvInputManager
import com.android.tv.common.util.SharedPreferencesUtils
import com.android.tv.dvr.data.RecordedProgram
import java.util.concurrent.CopyOnWriteArraySet

/** Merkt sich die Wiedergabeposition je Aufnahme (neu / angesehen / fertig ab 98 %). */
class DvrWatchedPositionManager(context: Context) {

    fun interface WatchedPositionChangedListener {
        fun onWatchedPositionChanged(recordedProgramId: Long, positionMs: Long)
    }

    private val watchedPositions = context.getSharedPreferences(SharedPreferencesUtils.SHARED_PREF_DVR_WATCHED_POSITION, Context.MODE_PRIVATE)
    private val listeners = HashMap<Long, MutableSet<WatchedPositionChangedListener>>()

    fun setWatchedPosition(recordedProgramId: Long, positionMs: Long) {
        watchedPositions.edit().putLong(recordedProgramId.toString(), positionMs).apply()
        listeners[recordedProgramId]?.forEach { it.onWatchedPositionChanged(recordedProgramId, positionMs) }
    }

    fun getWatchedPosition(recordedProgramId: Long): Long =
        watchedPositions.getLong(recordedProgramId.toString(), TvInputManager.TIME_SHIFT_INVALID_TIME)

    fun getWatchedStatus(recordedProgram: RecordedProgram): Int {
        val position = getWatchedPosition(recordedProgram.id)
        return when {
            position == TvInputManager.TIME_SHIFT_INVALID_TIME -> DVR_WATCHED_STATUS_NEW
            position > recordedProgram.durationMillis * DVR_WATCHED_THRESHOLD_RATE -> DVR_WATCHED_STATUS_WATCHED
            else -> DVR_WATCHED_STATUS_WATCHING
        }
    }

    fun addListener(listener: WatchedPositionChangedListener, recordedProgramId: Long) {
        if (recordedProgramId == RecordedProgram.ID_NOT_SET.toLong()) return
        listeners.getOrPut(recordedProgramId) { CopyOnWriteArraySet() }.add(listener)
    }

    fun removeListener(listener: WatchedPositionChangedListener) {
        ArrayList(listeners.keys).forEach { removeListener(listener, it) }
    }

    fun removeListener(listener: WatchedPositionChangedListener, recordedProgramId: Long) {
        val set = listeners[recordedProgramId] ?: return
        set.remove(listener)
        if (set.isEmpty()) listeners.remove(recordedProgramId)
    }

    companion object {
        const val DVR_WATCHED_THRESHOLD_RATE = 0.98f
        const val DVR_WATCHED_STATUS_NEW = 0
        const val DVR_WATCHED_STATUS_WATCHING = 1
        const val DVR_WATCHED_STATUS_WATCHED = 2
    }
}
