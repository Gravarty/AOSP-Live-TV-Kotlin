package com.android.tv.recommendation

import android.content.Context
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program

/** Sehverlauf eines Kanals (max. 100 Einträge) für die Empfehlungen. */
class ChannelRecord(private val context: Context, channel: Channel, inputRemoved: Boolean) {
    private val watchHistory = ArrayDeque<WatchedProgram>()
    private var currentProgram: Program? = null
    var channel: Channel = channel
        private set
    var isInputRemoved = inputRemoved
    var totalWatchDurationMs = 0L
        private set

    fun setChannel(channel: Channel, inputRemoved: Boolean) {
        this.channel = channel
        isInputRemoved = inputRemoved
    }

    @get:Synchronized
    val lastWatchEndTimeMs: Long get() = watchHistory.lastOrNull()?.watchEndTimeMs ?: 0

    /** Aktuelle Sendung (neu geholt, wenn die gemerkte vorbei ist). */
    fun getCurrentProgram(): Program? {
        val time = System.currentTimeMillis()
        val current = currentProgram
        if (current == null || current.endTimeUtcMillis < time) {
            currentProgram = TvSingletons.getSingletons(context).getProgramDataManager().getCurrentProgram(channel.id)
        }
        return currentProgram
    }

    @Synchronized
    fun getWatchHistory(): Array<WatchedProgram> = watchHistory.toTypedArray()

    @Synchronized
    fun logWatchHistory(p: WatchedProgram) {
        watchHistory.addLast(p)
        totalWatchDurationMs += p.watchedDurationMs
        if (watchHistory.size > MAX_HISTORY_SIZE) {
            totalWatchDurationMs -= watchHistory.removeFirst().watchedDurationMs
        }
    }

    companion object {
        internal const val MAX_HISTORY_SIZE = 100
    }
}
