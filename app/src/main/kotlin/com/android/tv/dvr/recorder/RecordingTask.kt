package com.android.tv.dvr.recorder

import android.content.Context
import android.media.tv.TvContract
import android.media.tv.TvInputManager
import android.media.tv.TvRecordingClient
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.Toast
import androidx.annotation.WorkerThread
import com.android.tv.InputSessionManager
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.Clock
import com.android.tv.data.api.Channel
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.WritableDvrDataManager
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit

/**
 * Eine einzelne Aufnahme über TvRecordingClient: Session holen, tunen, zur Startzeit aufnehmen,
 * zur Endzeit stoppen, Zustand in die Datenbank schreiben.
 * Entfällt: TvRecordingClientCompat (privates Protokoll nur des eingebauten Tuners, meldete
 * "Aufnahme gestartet" samt Aufnahme-URI vorab).
 * Bugfix: Geänderte Endzeiten wurden nie übernommen (Vergleich erst nach der Zuweisung).
 */
@WorkerThread
class RecordingTask internal constructor(
    private val context: Context,
    private var scheduledRecording: ScheduledRecording,
    private val channel: Channel?,
    private val dvrManager: DvrManager,
    private val sessionManager: InputSessionManager,
    private val dataManager: WritableDvrDataManager,
    private val clock: Clock,
) : TvRecordingClient.RecordingCallback(), Handler.Callback, DvrManager.Listener {

    internal enum class State { NOT_STARTED, SESSION_ACQUIRED, CONNECTION_PENDING, CONNECTED, RECORDING_STARTED, RECORDING_STOP_REQUESTED, FINISHED, ERROR, RELEASED }

    private val mainThreadHandler = Handler(Looper.getMainLooper())
    private var recordingSession: InputSessionManager.RecordingSession? = null
    private var handler: Handler? = null
    internal var state = State.NOT_STARTED
        private set
    private var startedWithClipping = false
    private var recordedProgramUri: Uri? = null
    private var canceled = false

    fun setHandler(handler: Handler) { this.handler = handler }

    override fun handleMessage(msg: Message): Boolean {
        SoftPreconditions.checkState(msg.what == InputTaskScheduler.MESSAGE_REMOVE || handler != null, TAG, "Null handler trying to handle $msg")
        try {
            when (msg.what) {
                MSG_INITIALIZE -> handleInit()
                MSG_START_RECORDING -> handleStartRecording()
                MSG_STOP_RECORDING -> handleStopRecording()
                MSG_UDPATE_SCHEDULE -> handleUpdateSchedule(msg.obj as ScheduledRecording)
                InputTaskScheduler.MESSAGE_REMOVE -> {
                    handler?.removeCallbacksAndMessages(null)
                    handler = null
                    release()
                    return false
                }
                else -> SoftPreconditions.checkArgument(false, TAG, "unexpected message type %s", msg)
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Error processing message $msg  for $scheduledRecording", e)
            failAndQuit()
        }
        return false
    }

    override fun onDisconnected(inputId: String) {
        if (recordingSession != null && state != State.FINISHED) failAndQuit(ScheduledRecording.FAILED_REASON_NOT_FINISHED)
    }

    override fun onConnectionFailed(inputId: String) {
        if (recordingSession != null) failAndQuit(ScheduledRecording.FAILED_REASON_CONNECTION_FAILED)
    }

    override fun onTuned(channelUri: Uri) {
        if (recordingSession == null) return
        state = State.CONNECTED
        if (handler == null || !sendEmptyMessageAtAbsoluteTime(MSG_START_RECORDING, scheduledRecording.startTimeMs - RECORDING_EARLY_START_OFFSET_MS)) {
            failAndQuit(ScheduledRecording.FAILED_REASON_MESSAGE_NOT_SENT)
        }
    }

    /** Fertig: < 5 min vor dem Ende gestoppt oder spät gestartet = abgeschnitten. */
    override fun onRecordingStopped(recordedProgramUri: Uri) {
        Log.i(TAG, "Recording Stopped: $scheduledRecording")
        Log.i(TAG, "Recording Stopped: stored as $recordedProgramUri")
        if (recordingSession == null) return
        this.recordedProgramUri = recordedProgramUri
        state = State.FINISHED
        var newState = ScheduledRecording.STATE_RECORDING_FINISHED
        if (startedWithClipping || scheduledRecording.endTimeMs - CLIPPED_THRESHOLD_MS > clock.currentTimeMillis()) {
            newState = ScheduledRecording.STATE_RECORDING_CLIPPED
        }
        updateRecordingState(newState)
        sendRemove()
        if (canceled) removeRecordedProgram()
    }

    override fun onError(reason: Int) {
        Log.i(TAG, "Recording failed with code=$reason for $scheduledRecording")
        if (recordingSession == null) return
        val error = when (reason) {
            TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE -> {
                Log.i(TAG, "Insufficient space to record $scheduledRecording")
                mainThreadHandler.post {
                    // Im Vordergrund: Hinweis; sonst beim nächsten Öffnen melden
                    if (TvSingletons.getSingletons(context).getMainActivityWrapper().isResumed) {
                        dataManager.getScheduledRecording(scheduledRecording.id)?.let {
                            Toast.makeText(context.applicationContext,
                                context.getString(R.string.dvr_error_insufficient_space_description_one_recording,
                                    it.getProgramDisplayTitle(context)), Toast.LENGTH_LONG).show()
                        }
                    } else {
                        Utils.setRecordingFailedReason(context.applicationContext, TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE)
                        Utils.addFailedScheduledRecordingInfo(context.applicationContext, scheduledRecording.getProgramDisplayTitle(context).orEmpty())
                    }
                }
                ScheduledRecording.FAILED_REASON_INSUFFICIENT_SPACE
            }
            TvInputManager.RECORDING_ERROR_RESOURCE_BUSY -> ScheduledRecording.FAILED_REASON_RESOURCE_BUSY
            else -> ScheduledRecording.FAILED_REASON_OTHER
        }
        failAndQuit(error)
    }

    private fun handleInit() {
        if (scheduledRecording.endTimeMs < clock.currentTimeMillis()) {
            Log.w(TAG, "End time already past, not recording $scheduledRecording")
            failAndQuit(ScheduledRecording.FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED)
            return
        }
        if (channel == null) {
            Log.w(TAG, "Null channel for $scheduledRecording")
            failAndQuit(ScheduledRecording.FAILED_REASON_INVALID_CHANNEL)
            return
        }
        if (channel.id != scheduledRecording.channelId) {
            Log.w(TAG, "Channel$channel does not match scheduled recording $scheduledRecording")
            failAndQuit(ScheduledRecording.FAILED_REASON_INVALID_CHANNEL)
            return
        }
        val inputId = channel.inputId
        val session = sessionManager.createRecordingSession(inputId, "recordingTask-${scheduledRecording.id}", this, handler!!,
            scheduledRecording.endTimeMs)
        recordingSession = session
        state = State.SESSION_ACQUIRED
        dvrManager.addListener(this, handler!!)
        session.tune(inputId, channel.uri)
        state = State.CONNECTION_PENDING
    }

    private fun failAndQuit(reason: Int = ScheduledRecording.FAILED_REASON_OTHER) {
        Log.w(TAG, "Recording $scheduledRecording failed with code $reason")
        updateRecordingState(ScheduledRecording.STATE_RECORDING_FAILED, reason)
        state = State.ERROR
        sendRemove()
    }

    private fun sendRemove() {
        handler?.let { it.sendMessageAtFrontOfQueue(it.obtainMessage(InputTaskScheduler.MESSAGE_REMOVE)) }
    }

    private fun handleStartRecording() {
        Log.i(TAG, "Start Recording: $scheduledRecording")
        val programId = scheduledRecording.programId
        recordingSession?.startRecording(if (programId == ScheduledRecording.ID_NOT_SET) null else TvContract.buildProgramUri(programId))
        updateRecordingState(ScheduledRecording.STATE_RECORDING_IN_PROGRESS)
        // Mehr als 5 min zu spät gestartet
        if (scheduledRecording.startTimeMs + CLIPPED_THRESHOLD_MS < clock.currentTimeMillis()) startedWithClipping = true
        state = State.RECORDING_STARTED
        if (!sendEmptyMessageAtAbsoluteTime(MSG_STOP_RECORDING, scheduledRecording.endTimeMs)) {
            failAndQuit(ScheduledRecording.FAILED_REASON_MESSAGE_NOT_SENT)
        }
    }

    private fun handleStopRecording() {
        Log.i(TAG, "Stop Recording: $scheduledRecording")
        recordingSession?.stopRecording()
        state = State.RECORDING_STOP_REQUESTED
    }

    /** Geänderte Endzeit übernehmen (vor der Zuweisung vergleichen). */
    private fun handleUpdateSchedule(schedule: ScheduledRecording) {
        val endChanged = schedule.endTimeMs != scheduledRecording.endTimeMs
        scheduledRecording = schedule
        if (!endChanged) return
        recordingSession?.setEndTimeMs(schedule.endTimeMs)
        if (state == State.RECORDING_STARTED) {
            handler?.removeMessages(MSG_STOP_RECORDING)
            if (!sendEmptyMessageAtAbsoluteTime(MSG_STOP_RECORDING, schedule.endTimeMs)) {
                failAndQuit(ScheduledRecording.FAILED_REASON_MESSAGE_NOT_SENT)
            }
        }
    }

    val priority: Long get() = scheduledRecording.priority
    val startTimeMs: Long get() = scheduledRecording.startTimeMs
    val endTimeMs: Long get() = scheduledRecording.endTimeMs
    private val scheduleId: Long get() = scheduledRecording.id

    private fun release() {
        recordingSession?.let {
            sessionManager.releaseRecordingSession(it)
            recordingSession = null
        }
        dvrManager.removeListener(this)
    }

    private fun sendEmptyMessageAtAbsoluteTime(what: Int, `when`: Long): Boolean {
        val delay = maxOf(0L, `when` - clock.currentTimeMillis())
        return handler?.sendEmptyMessageDelayed(what, delay) == true
    }

    private fun updateRecordingState(newState: Int, reason: Int? = null) {
        scheduledRecording = ScheduledRecording.buildFrom(scheduledRecording).setState(newState).build()
        val id = scheduledRecording.id
        runOnMainThread {
            val schedule = dataManager.getScheduledRecording(id)
            if (schedule == null) {
                // Plan inzwischen gelöscht: Aufnahme verwerfen
                removeRecordedProgram()
            } else {
                val builder = ScheduledRecording.buildFrom(schedule).setState(newState)
                if (newState == ScheduledRecording.STATE_RECORDING_FAILED && reason != null) builder.setFailedReason(reason)
                dataManager.updateScheduledRecording(builder.build())
            }
        }
    }

    override fun onStopRecordingRequested(scheduledRecording: ScheduledRecording) {
        if (scheduledRecording.id == this.scheduledRecording.id) stop()
    }

    fun start() {
        handler?.sendEmptyMessage(MSG_INITIALIZE)
    }

    /** Laufende Aufnahme stoppen, sonst Aufgabe entfernen. */
    fun stop() {
        when (state) {
            State.RECORDING_STARTED -> {
                handler?.removeMessages(MSG_STOP_RECORDING)
                handleStopRecording()
            }
            State.RECORDING_STOP_REQUESTED -> {}
            else -> sendRemove()
        }
    }

    fun cancel() {
        canceled = true
        stop()
        removeRecordedProgram()
    }

    fun cleanUp() {
        if (state == State.RECORDING_STARTED || state == State.RECORDING_STOP_REQUESTED) {
            updateRecordingState(ScheduledRecording.STATE_RECORDING_FAILED, ScheduledRecording.FAILED_REASON_SCHEDULER_STOPPED)
        }
        release()
        handler?.removeCallbacksAndMessages(null)
    }

    override fun toString() = "${javaClass.name}($scheduledRecording)"

    private fun removeRecordedProgram() = runOnMainThread {
        recordedProgramUri?.let { dvrManager.removeRecordedProgram(it, true) }
    }

    private fun runOnMainThread(r: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) r() else mainThreadHandler.post(r)
    }

    companion object {
        private const val TAG = "RecordingTask"
        @JvmField val END_TIME_COMPARATOR: Comparator<RecordingTask> = compareBy { it.endTimeMs }
        @JvmField val ID_COMPARATOR: Comparator<RecordingTask> = compareBy { it.scheduleId }
        @JvmField val PRIORITY_COMPARATOR: Comparator<RecordingTask> = compareBy { it.priority }
        internal const val MSG_INITIALIZE = 1
        internal const val MSG_START_RECORDING = 2
        internal const val MSG_STOP_RECORDING = 3
        const val MSG_UDPATE_SCHEDULE = 4
        @JvmField val RECORDING_EARLY_START_OFFSET_MS: Long = TimeUnit.SECONDS.toMillis(3)
        private val CLIPPED_THRESHOLD_MS = TimeUnit.MINUTES.toMillis(5)
    }
}
