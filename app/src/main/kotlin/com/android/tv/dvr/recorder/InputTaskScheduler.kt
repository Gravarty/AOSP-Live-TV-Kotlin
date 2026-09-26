package com.android.tv.dvr.recorder

import android.content.Context
import android.media.tv.TvInputInfo
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.ArrayMap
import android.util.Log
import android.util.LongSparseArray
import com.android.tv.InputSessionManager
import com.android.tv.common.util.Clock
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.api.Channel
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.WritableDvrDataManager
import com.android.tv.dvr.data.ScheduledRecording

/**
 * Verteilt die anstehenden Aufnahmen eines Inputs auf dessen Tuner (auf dem Recorder-Thread):
 * startet Aufgaben, verdrängt niedriger priorisierte, markiert zu spät gestartete als fehlgeschlagen.
 */
class InputTaskScheduler internal constructor(
    private val context: Context,
    input: TvInputInfo,
    private val looper: Looper,
    private val channelDataManager: ChannelDataManager,
    private val dvrManager: DvrManager,
    dataManager: DvrDataManager,
    private val sessionManager: InputSessionManager,
    private val clock: Clock,
    recordingTaskFactory: RecordingTaskFactory? = null,
) {
    internal fun interface RecordingTaskFactory {
        fun createRecordingTask(schedule: ScheduledRecording, channel: Channel?, dvrManager: DvrManager,
            sessionManager: InputSessionManager, dataManager: WritableDvrDataManager, clock: Clock): RecordingTask
    }

    /** Handler einer Aufnahme; bei jeder Nachricht wird der Plan neu gebaut. */
    inner class HandlerWrapper internal constructor(looper: Looper, schedule: ScheduledRecording, internal val task: RecordingTask) :
        Handler(looper, task) {
        private val id = schedule.id

        init {
            task.setHandler(this)
        }

        override fun handleMessage(msg: Message) {
            if (msg.what == MESSAGE_REMOVE) pendingRecordings.remove(id)
            removeCallbacksAndMessages(null)
            handler.removeMessages(MSG_BUILD_SCHEDULE)
            handler.sendEmptyMessage(MSG_BUILD_SCHEDULE)
            super.handleMessage(msg)
        }
    }

    private var input: TvInputInfo = input
    private val dataManager = dataManager as WritableDvrDataManager
    private val pendingRecordings = LongSparseArray<HandlerWrapper>()
    private val waitingSchedules = ArrayMap<Long, ScheduledRecording>()
    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private val inputLock = Any()
    private val recordingTaskFactory = recordingTaskFactory ?: RecordingTaskFactory { schedule, channel, _, _, _, _ ->
        RecordingTask(context, schedule, channel, this.dvrManager, this.sessionManager, this.dataManager, this.clock)
    }
    private val handler = object : Handler(looper) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_ADD_SCHEDULED_RECORDING -> handleAddSchedule(msg.obj as ScheduledRecording)
                MSG_REMOVE_SCHEDULED_RECORDING -> handleRemoveSchedule(msg.obj as ScheduledRecording)
                MSG_UPDATE_SCHEDULED_RECORDING -> handleUpdateSchedule(msg.obj as ScheduledRecording)
                MSG_BUILD_SCHEDULE -> handleBuildSchedule()
                MSG_STOP_SCHEDULE -> handleStopSchedule()
            }
        }
    }

    fun addSchedule(schedule: ScheduledRecording) { handler.sendMessage(handler.obtainMessage(MSG_ADD_SCHEDULED_RECORDING, schedule)) }
    fun removeSchedule(schedule: ScheduledRecording) { handler.sendMessage(handler.obtainMessage(MSG_REMOVE_SCHEDULED_RECORDING, schedule)) }
    fun updateSchedule(schedule: ScheduledRecording) { handler.sendMessage(handler.obtainMessage(MSG_UPDATE_SCHEDULED_RECORDING, schedule)) }

    fun updateTvInputInfo(input: TvInputInfo?) {
        if (input == null) return
        synchronized(inputLock) { this.input = input }
    }

    fun stop() {
        handler.removeCallbacksAndMessages(null)
        handler.sendEmptyMessage(MSG_STOP_SCHEDULE)
    }

    internal fun handleAddSchedule(schedule: ScheduledRecording) {
        if (pendingRecordings.get(schedule.id) != null || waitingSchedules.containsKey(schedule.id)) return
        waitingSchedules[schedule.id] = schedule
        rebuildSoon()
    }

    internal fun handleRemoveSchedule(schedule: ScheduledRecording) {
        val wrapper = pendingRecordings.get(schedule.id)
        if (wrapper != null) {
            wrapper.task.cancel()
            return
        }
        if (waitingSchedules.remove(schedule.id) != null) rebuildSoon()
    }

    internal fun handleUpdateSchedule(schedule: ScheduledRecording) {
        val wrapper = pendingRecordings.get(schedule.id)
        if (wrapper != null) {
            // Später verschoben: laufende Aufgabe abbrechen und neu einplanen
            if (schedule.startTimeMs > clock.currentTimeMillis() && schedule.startTimeMs > wrapper.task.startTimeMs) {
                wrapper.task.cancel()
                waitingSchedules[schedule.id] = schedule
                return
            }
            wrapper.sendMessage(wrapper.obtainMessage(RecordingTask.MSG_UDPATE_SCHEDULE, schedule))
            return
        }
        if (waitingSchedules.containsKey(schedule.id)) {
            waitingSchedules[schedule.id] = schedule
            rebuildSoon()
        }
    }

    private fun rebuildSoon() {
        handler.removeMessages(MSG_BUILD_SCHEDULE)
        handler.sendEmptyMessage(MSG_BUILD_SCHEDULE)
    }

    private fun handleStopSchedule() {
        waitingSchedules.clear()
        for (i in 0 until pendingRecordings.size()) pendingRecordings.valueAt(i).task.cleanUp()
    }

    /** Startbare Pläne auf freie Tuner verteilen und den nächsten Prüfzeitpunkt setzen. */
    internal fun handleBuildSchedule() {
        if (waitingSchedules.isEmpty()) return
        val now = clock.currentTimeMillis()
        // Weniger als 5 % Restlaufzeit: fehlgeschlagen
        val iter = waitingSchedules.values.iterator()
        while (iter.hasNext()) {
            val s = iter.next()
            if (s.endTimeMs - now <= MIN_REMAIN_DURATION_PERCENT * s.duration) {
                Log.e(TAG, "Error! Program ended before recording started:$s")
                fail(s, ScheduledRecording.FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED)
                iter.remove()
            }
        }
        if (waitingSchedules.isEmpty()) return
        val toStart = waitingSchedules.values.filter {
            it.state != ScheduledRecording.STATE_RECORDING_CANCELED &&
                it.startTimeMs - RecordingTask.RECORDING_EARLY_START_OFFSET_MS <= now && it.endTimeMs > now
        }.sortedWith(ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR)
        val tunerCount = synchronized(inputLock) { if (input.canRecord()) input.tunerCount else 0 }
        for (s in toStart) {
            // Erst warten, bis eine früher endende Aufnahme fertig ist
            if (hasTaskWhichFinishEarlier(s)) return
            if (pendingRecordings.size() < tunerCount) {
                createRecordingTask(s).start()
                waitingSchedules.remove(s.id)
            } else {
                getReplaceableTask(s)?.let {
                    it.stop()
                    return
                }
            }
        }
        if (waitingSchedules.isEmpty()) return
        var earliest = Long.MAX_VALUE
        for (s in waitingSchedules.values) {
            earliest = if (s in toStart) minOf(earliest, s.endTimeMs)
            else minOf(earliest, s.startTimeMs - RecordingTask.RECORDING_EARLY_START_OFFSET_MS)
        }
        handler.sendEmptyMessageDelayed(MSG_BUILD_SCHEDULE, earliest - now)
    }

    private fun createRecordingTask(schedule: ScheduledRecording): RecordingTask {
        val channel = channelDataManager.getChannel(schedule.channelId)
        val task = recordingTaskFactory.createRecordingTask(schedule, channel, dvrManager, sessionManager, dataManager, clock)
        pendingRecordings.put(schedule.id, HandlerWrapper(looper, schedule, task))
        return task
    }

    private fun hasTaskWhichFinishEarlier(schedule: ScheduledRecording): Boolean =
        (0 until pendingRecordings.size()).any { pendingRecordings.valueAt(it).task.endTimeMs <= schedule.startTimeMs }

    private fun getReplaceableTask(schedule: ScheduledRecording): RecordingTask? {
        var candidate: RecordingTask? = null
        for (i in 0 until pendingRecordings.size()) {
            val task = pendingRecordings.valueAt(i).task
            if (schedule.priority > task.priority && (candidate == null || CANDIDATE_COMPARATOR.compare(candidate, task) > 0)) candidate = task
        }
        return candidate
    }

    private fun fail(schedule: ScheduledRecording, reason: Int) = runOnMainHandler {
        dataManager.getScheduledRecording(schedule.id)?.let {
            dataManager.changeState(it, ScheduledRecording.STATE_RECORDING_FAILED, reason)
        }
    }

    private fun runOnMainHandler(r: () -> Unit) {
        if (Looper.myLooper() == mainThreadHandler.looper) r() else mainThreadHandler.post(r)
    }

    companion object {
        private const val TAG = "InputTaskScheduler"
        const val MESSAGE_REMOVE = 999
        private const val MSG_ADD_SCHEDULED_RECORDING = 1
        private const val MSG_REMOVE_SCHEDULED_RECORDING = 2
        private const val MSG_UPDATE_SCHEDULED_RECORDING = 3
        private const val MSG_BUILD_SCHEDULE = 4
        private const val MSG_STOP_SCHEDULE = 5
        private const val MIN_REMAIN_DURATION_PERCENT = 0.05f
        private val CANDIDATE_COMPARATOR: Comparator<RecordingTask> =
            RecordingTask.PRIORITY_COMPARATOR.then(RecordingTask.END_TIME_COMPARATOR).then(RecordingTask.ID_COMPARATOR)

        @JvmStatic
        fun getRecordingOrderComparator(): Comparator<ScheduledRecording> = ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR
    }
}
