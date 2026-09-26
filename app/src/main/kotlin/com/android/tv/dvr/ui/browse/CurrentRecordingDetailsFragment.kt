package com.android.tv.dvr.ui.browse

import android.content.Context
import android.media.tv.TvInputManager
import androidx.leanback.widget.Action
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dialog.HalfSizedDialogFragment
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrStopRecordingFragment
import com.android.tv.dvr.ui.DvrUiHelper

/**
 * Detailansicht einer laufenden Aufnahme.
 * DvrWatchedPositionManager kommt aus TvSingletons statt per Injection.
 */
class CurrentRecordingDetailsFragment : RecordingDetailsFragment() {
    private var dvrDataManager: DvrDataManager? = null
    private var recordedProgram: RecordedProgram? = null
    private lateinit var dvrWatchedPositionManager: DvrWatchedPositionManager
    private var paused = false

    private val scheduledRecordingListener = object : DvrDataManager.ScheduledRecordingListener {
        override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {}

        override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
            // Bugfix: Callback kann vor dem Laden der Aufnahme eintreffen.
            val id = recordingOrNull?.id ?: return
            if (scheduledRecordings.any { it.id == id }) activity?.finish()
        }

        override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
            val id = recordingOrNull?.id ?: return
            if (scheduledRecordings.any { it.id == id && it.state != ScheduledRecording.STATE_RECORDING_IN_PROGRESS }) {
                activity?.finish()
            }
        }
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val singletons = TvSingletons.getSingletons(context)
        dvrWatchedPositionManager = singletons.getDvrWatchedPositionManager()
        dvrDataManager = singletons.getDvrDataManager().also {
            it.addScheduledRecordingListener(scheduledRecordingListener)
        }
    }

    override fun onResume() {
        super.onResume()
        if (paused) {
            updateActions()
            paused = false
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    override fun onCreateActionsAdapter(): SparseArrayObjectAdapter {
        getRecording().recordedProgramId?.let { recordedProgram = dvrDataManager?.getRecordedProgram(it) }
        val adapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        val res = resources
        adapter.set(ACTION_STOP_RECORDING, Action(ACTION_STOP_RECORDING.toLong(),
            res.getString(R.string.dvr_detail_stop_recording), null, res.getDrawable(R.drawable.lb_ic_stop, null)))
        val program = recordedProgram
        if (program != null && program.isPartial) {
            if (dvrWatchedPositionManager.getWatchedStatus(program) ==
                DvrWatchedPositionManager.DVR_WATCHED_STATUS_WATCHING
            ) {
                adapter.set(ACTION_RESUME_PLAYING, Action(ACTION_RESUME_PLAYING.toLong(),
                    res.getString(R.string.dvr_detail_resume_play), null, res.getDrawable(R.drawable.lb_ic_play, null)))
                adapter.set(ACTION_PLAY_FROM_BEGINNING, Action(ACTION_PLAY_FROM_BEGINNING.toLong(),
                    res.getString(R.string.dvr_detail_play_from_beginning), null,
                    res.getDrawable(R.drawable.lb_ic_replay, null)))
            } else {
                adapter.set(ACTION_PLAY_FROM_BEGINNING, Action(ACTION_PLAY_FROM_BEGINNING.toLong(),
                    res.getString(R.string.dvr_detail_watch), null, res.getDrawable(R.drawable.lb_ic_play, null)))
            }
        }
        return adapter
    }

    override fun onCreateOnActionClickedListener() = OnActionClickedListener { action ->
        when (action.id) {
            ACTION_STOP_RECORDING.toLong() -> DvrUiHelper.showStopRecordingDialog(
                requireActivity(), getRecording().channelId, DvrStopRecordingFragment.REASON_USER_STOP,
                HalfSizedDialogFragment.OnActionClickListener { actionId ->
                    if (actionId == DvrStopRecordingFragment.ACTION_STOP.toLong()) {
                        TvSingletons.getSingletons(requireContext()).getDvrManager()?.stopRecording(getRecording())
                        requireActivity().finish()
                    }
                })
            ACTION_RESUME_PLAYING.toLong() -> recordedProgram?.let {
                startPlayback(it, dvrWatchedPositionManager.getWatchedPosition(it.id))
            }
            ACTION_PLAY_FROM_BEGINNING.toLong() -> recordedProgram?.let {
                startPlayback(it, TvInputManager.TIME_SHIFT_INVALID_TIME)
            }
        }
    }

    override fun onDetach() {
        dvrDataManager?.removeScheduledRecordingListener(scheduledRecordingListener)
        super.onDetach()
    }

    companion object {
        private const val ACTION_STOP_RECORDING = 1
        private const val ACTION_RESUME_PLAYING = 2
        private const val ACTION_PLAY_FROM_BEGINNING = 3
    }
}
