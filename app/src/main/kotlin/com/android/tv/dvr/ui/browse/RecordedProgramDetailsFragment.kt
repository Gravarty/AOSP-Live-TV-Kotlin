package com.android.tv.dvr.ui.browse

import com.android.tv.common.util.PermissionUtils
import android.media.tv.TvInputManager
import android.os.Bundle
import androidx.leanback.widget.Action
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.ui.DetailsActivity

/** Detailansicht einer fertigen Aufnahme ([RecordedProgram]). */
class RecordedProgramDetailsFragment : DvrDetailsFragment(), DvrDataManager.RecordedProgramListener {
    private lateinit var dvrWatchedPositionManager: DvrWatchedPositionManager
    private lateinit var recordedProgram: RecordedProgram
    private var paused = false
    private lateinit var dvrDataManager: DvrDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        dvrDataManager = TvSingletons.getSingletons(requireContext()).getDvrDataManager()
        dvrDataManager.addRecordedProgramListener(this)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateInternal() {
        dvrWatchedPositionManager = TvSingletons.getSingletons(requireActivity()).getDvrWatchedPositionManager()
        setDetailsOverviewRow(DetailsContent.createFromRecordedProgram(requireContext(), recordedProgram))
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

    override fun onDestroy() {
        dvrDataManager.removeRecordedProgramListener(this)
        super.onDestroy()
    }

    override fun onLoadRecordingDetails(args: Bundle): Boolean {
        val recordedProgramId = args.getLong(DetailsActivity.RECORDING_ID)
        val program = dvrDataManager.getRecordedProgram(recordedProgramId) ?: return false
        recordedProgram = program
        return true
    }

    override fun onCreateActionsAdapter(): SparseArrayObjectAdapter {
        val adapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        val res = resources
        if (dvrWatchedPositionManager.getWatchedStatus(recordedProgram) ==
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
        adapter.set(ACTION_DELETE_RECORDING, Action(ACTION_DELETE_RECORDING.toLong(),
            res.getString(R.string.dvr_detail_delete), null, res.getDrawable(R.drawable.ic_delete_32dp, null)))
        return adapter
    }

    override fun onCreateOnActionClickedListener() = OnActionClickedListener { action ->
        when (action.id) {
            ACTION_PLAY_FROM_BEGINNING.toLong() -> startPlayback(recordedProgram, TvInputManager.TIME_SHIFT_INVALID_TIME)
            ACTION_RESUME_PLAYING.toLong() ->
                startPlayback(recordedProgram, dvrWatchedPositionManager.getWatchedPosition(recordedProgram.id))
            ACTION_DELETE_RECORDING.toLong() -> delete()
        }
    }

    private fun delete() {
        val hasWriteExternalStorage = PermissionUtils.hasWriteExternalStorage(requireContext())
        if (!hasWriteExternalStorage && DvrManager.isFile(recordedProgram.dataUri) &&
            !DvrManager.isFromBundledInput(recordedProgram)
        ) {
            DvrUiHelper.showWriteStoragePermissionRationaleDialog(requireActivity())
        } else {
            TvSingletons.getSingletons(requireActivity()).getDvrManager()?.removeRecordedProgram(recordedProgram, true)
            requireActivity().finish()
        }
    }

    override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) {}

    override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) {}

    override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) {
        // Bugfix: Callback vor dem Laden ignorieren.
        if (!::recordedProgram.isInitialized) return
        if (recordedPrograms.any { it.id == recordedProgram.id }) activity?.finish()
    }

    companion object {
        private const val ACTION_RESUME_PLAYING = 1
        private const val ACTION_PLAY_FROM_BEGINNING = 2
        private const val ACTION_DELETE_RECORDING = 3
    }
}
