package com.android.tv.dvr.recorder

import android.content.ContentUris
import android.media.tv.TvContract
import android.os.Handler
import android.os.Looper
import android.util.ArraySet
import androidx.annotation.MainThread
import com.android.tv.InputSessionManager
import com.android.tv.MainActivity
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrDataManager.ScheduledRecordingListener
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper
import java.util.concurrent.TimeUnit

/**
 * Prüft beim Schauen, ob in 30 s–5 min Aufnahmen starten, die mit dem aktuellen Kanal
 * kollidieren, und zeigt dann einen Hinweis-Dialog.
 * Bugfix: Kanal kann inzwischen gelöscht sein (Original: NPE).
 */
@MainThread
class ConflictChecker(private val mainActivity: MainActivity) {

    fun interface OnUpcomingConflictChangeListener {
        fun onUpcomingConflictChange()
    }

    private val singletons = TvSingletons.getSingletons(mainActivity)
    private val channelDataManager = singletons.getChannelDataManager()
    private val scheduleManager: DvrScheduleManager = singletons.getDvrScheduleManager()!!
    private val sessionManager: InputSessionManager = singletons.getInputSessionManager()
    private val handler = Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == MSG_CHECK_CONFLICT) onCheckConflict()
        true
    }
    private val upcomingConflicts = ArrayList<ScheduledRecording>()
    private val listeners = ArraySet<OnUpcomingConflictChangeListener>()
    private val checkedConflictsMap = HashMap<Long, List<ScheduledRecording>>()
    private var started = false

    private val scheduledRecordingListener = object : ScheduledRecordingListener {
        override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) { handler.sendEmptyMessage(MSG_CHECK_CONFLICT) }
        override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) { handler.sendEmptyMessage(MSG_CHECK_CONFLICT) }
        override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) { handler.sendEmptyMessage(MSG_CHECK_CONFLICT) }
    }

    private val channelChangeListener = InputSessionManager.OnTvViewChannelChangeListener { handler.sendEmptyMessage(MSG_CHECK_CONFLICT) }

    fun start() {
        if (started) return
        started = true
        handler.sendEmptyMessage(MSG_CHECK_CONFLICT)
        scheduleManager.addScheduledRecordingListener(scheduledRecordingListener)
        sessionManager.addOnTvViewChannelChangeListener(channelChangeListener)
    }

    fun stop() {
        if (!started) return
        started = false
        sessionManager.removeOnTvViewChannelChangeListener(channelChangeListener)
        scheduleManager.removeScheduledRecordingListener(scheduledRecordingListener)
        handler.removeCallbacksAndMessages(null)
    }

    fun getUpcomingConflicts(): List<ScheduledRecording> = ArrayList(upcomingConflicts)

    fun addOnUpcomingConflictChangeListener(l: OnUpcomingConflictChangeListener) { listeners.add(l) }
    fun removeOnUpcomingConflictChangeListener(l: OnUpcomingConflictChangeListener) { listeners.remove(l) }
    private fun notifyUpcomingConflictChanged() = listeners.toList().forEach { it.onUpcomingConflictChange() }

    /** Vom Nutzer bereits bestätigte Konflikte (kein erneuter Dialog). */
    fun setCheckedConflictsForChannel(channelId: Long, conflicts: List<ScheduledRecording>) {
        checkedConflictsMap[channelId] = ArrayList(conflicts)
    }

    internal fun onCheckConflict() {
        handler.removeMessages(MSG_CHECK_CONFLICT)
        upcomingConflicts.clear()
        if (!scheduleManager.isInitialized || !channelDataManager.isDbLoadFinished) {
            handler.sendEmptyMessageDelayed(MSG_CHECK_CONFLICT, CHECK_RETRY_PERIOD_MS)
            notifyUpcomingConflictChanged()
            return
        }
        val channelUri = sessionManager.getCurrentTvViewChannelUri()
        if (channelUri == null || TvContract.isChannelUriForPassthroughInput(channelUri)) {
            notifyUpcomingConflictChanged()
            return
        }
        val channel = channelDataManager.getChannel(ContentUris.parseId(channelUri))
        if (channel == null) {
            notifyUpcomingConflictChanged()
            return
        }
        val conflicts = scheduleManager.getConflictingSchedulesForWatching(channel.id)
        var earliestToCheck = Long.MAX_VALUE
        val now = System.currentTimeMillis()
        for (schedule in conflicts) {
            val start = schedule.startTimeMs
            if (start < now + MIN_WATCH_CONFLICT_CHECK_TIME_MS) continue // zu knapp
            if (start > now + MAX_WATCH_CONFLICT_CHECK_TIME_MS) {
                earliestToCheck = minOf(earliestToCheck, start - MAX_WATCH_CONFLICT_CHECK_TIME_MS)
            } else {
                upcomingConflicts.add(schedule)
                earliestToCheck = minOf(earliestToCheck, start - MIN_WATCH_CONFLICT_CHECK_TIME_MS)
            }
        }
        if (earliestToCheck != Long.MAX_VALUE) handler.sendEmptyMessageDelayed(MSG_CHECK_CONFLICT, earliestToCheck - now)
        notifyUpcomingConflictChanged()
        if (upcomingConflicts.isNotEmpty() && !DvrUiHelper.isChannelWatchConflictDialogShown(mainActivity)) {
            val checked = checkedConflictsMap[channel.id]
            if (checked == null || !checked.containsAll(upcomingConflicts)) DvrUiHelper.showChannelWatchConflictDialog(mainActivity, channel)
        }
    }

    companion object {
        private const val MSG_CHECK_CONFLICT = 1
        private val CHECK_RETRY_PERIOD_MS = TimeUnit.SECONDS.toMillis(30)
        private val MAX_WATCH_CONFLICT_CHECK_TIME_MS = TimeUnit.MINUTES.toMillis(5)
        private val MIN_WATCH_CONFLICT_CHECK_TIME_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
