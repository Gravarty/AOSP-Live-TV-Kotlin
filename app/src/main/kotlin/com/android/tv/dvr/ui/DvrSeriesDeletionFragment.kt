package com.android.tv.dvr.ui

import com.android.tv.common.util.PermissionUtils
import android.content.Context
import android.media.tv.TvInputManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Toast
import androidx.leanback.app.GuidedStepSupportFragment
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.common.SoftPreconditions
import com.android.tv.data.api.BaseProgram
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.ui.GuidedActionsStylistWithDivider
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit

/** Auswahl und Löschen aufgenommener Folgen einer Serie. GuidedStepFragment → SupportFragment. */
class DvrSeriesDeletionFragment : GuidedStepSupportFragment() {
    private var dvrManager: DvrManager? = null
    private lateinit var dvrDataManager: DvrDataManager
    private lateinit var dvrWatchedPositionManager: DvrWatchedPositionManager
    private var recordings: List<RecordedProgram> = emptyList()
    private val watchedRecordings = HashSet<Long>()
    private val idsToDelete = ArrayList<Long>()
    private var allSelected = false
    private var seriesRecordingId = 0L
    private var oneLineActionHeight = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        seriesRecordingId = arguments?.getLong(DvrSeriesDeletionActivity.SERIES_RECORDING_ID, -1) ?: -1
        SoftPreconditions.checkArgument(seriesRecordingId != -1L, null, null)
        val singletons = TvSingletons.getSingletons(context)
        dvrManager = singletons.getDvrManager()
        dvrDataManager = singletons.getDvrDataManager()
        dvrWatchedPositionManager = singletons.getDvrWatchedPositionManager()
        recordings = dvrDataManager.getRecordedPrograms(seriesRecordingId)
        oneLineActionHeight = resources.getDimensionPixelSize(R.dimen.dvr_settings_one_line_action_container_height)
        if (recordings.isEmpty()) {
            Toast.makeText(activity, getString(R.string.dvr_series_deletion_no_recordings), Toast.LENGTH_LONG).show()
            finishGuidedStepSupportFragments()
            return
        }
        recordings = recordings.sortedWith(BaseProgram.EPISODE_COMPARATOR)
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val breadcrumb = dvrDataManager.getSeriesRecording(seriesRecordingId)?.title
        return Guidance(
            getString(R.string.dvr_series_deletion_title),
            getString(R.string.dvr_series_deletion_description),
            breadcrumb,
            null,
        )
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(activity).id(ACTION_ID_SELECT_WATCHED).title(getString(R.string.dvr_series_select_watched)).build())
        actions.add(GuidedAction.Builder(activity).id(ACTION_ID_SELECT_ALL).title(getString(R.string.dvr_series_select_all)).build())
        actions.add(GuidedActionsStylistWithDivider.createDividerAction(requireContext()))
        for (recording in recordings) {
            val watchedPositionMs = dvrWatchedPositionManager.getWatchedPosition(recording.id)
            var title = recording.getEpisodeDisplayTitle(requireContext())
            if (title.isNullOrEmpty()) {
                title = if (recording.title.isEmpty()) getString(R.string.channel_banner_no_title) else recording.title
            }
            val description = if (watchedPositionMs != TvInputManager.TIME_SHIFT_INVALID_TIME) {
                watchedRecordings.add(recording.id)
                getWatchedString(watchedPositionMs, recording.durationMillis)
            } else {
                getString(R.string.dvr_series_never_watched)
            }
            actions.add(
                GuidedAction.Builder(activity)
                    .id(recording.id)
                    .title(title)
                    .description(description)
                    .checkSetId(GuidedAction.CHECKBOX_CHECK_SET_ID)
                    .build(),
            )
        }
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(activity).id(ACTION_ID_DELETE).title(getString(R.string.dvr_detail_delete)).build())
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onGuidedActionClicked(action: GuidedAction) {
        val actionId = action.id
        if (actionId == ACTION_ID_DELETE) {
            delete()
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            finishGuidedStepSupportFragments()
        } else if (actionId == ACTION_ID_SELECT_WATCHED) {
            for (guidedAction in actions) {
                if (guidedAction.checkSetId == GuidedAction.CHECKBOX_CHECK_SET_ID) {
                    val recordingId = guidedAction.id
                    guidedAction.isChecked = watchedRecordings.contains(recordingId)
                    notifyActionChanged(findActionPositionById(recordingId))
                }
            }
            allSelected = updateSelectAllState()
        } else if (actionId == ACTION_ID_SELECT_ALL) {
            allSelected = !allSelected
            for (guidedAction in actions) {
                if (guidedAction.checkSetId == GuidedAction.CHECKBOX_CHECK_SET_ID) {
                    guidedAction.isChecked = allSelected
                    notifyActionChanged(findActionPositionById(guidedAction.id))
                }
            }
            updateSelectAllState(action, allSelected)
        } else {
            allSelected = updateSelectAllState()
        }
    }

    override fun onCreateButtonActionsStylist(): GuidedActionsStylist = DvrGuidedActionsStylist(true)

    override fun onCreateActionsStylist(): GuidedActionsStylist = object : GuidedActionsStylistWithDivider() {
        override fun onBindViewHolder(vh: GuidedActionsStylist.ViewHolder, action: GuidedAction) {
            super.onBindViewHolder(vh, action)
            if (action.id == GuidedActionsStylistWithDivider.ACTION_DIVIDER.toLong()) {
                return
            }
            val lp = vh.itemView.layoutParams
            if (action.checkSetId != GuidedAction.CHECKBOX_CHECK_SET_ID) {
                lp.height = oneLineActionHeight
            } else {
                vh.itemView.layoutParams = ViewGroup.LayoutParams(lp.width, ViewGroup.LayoutParams.WRAP_CONTENT)
            }
        }
    }

    private fun delete() {
        idsToDelete.clear()
        for (guidedAction in actions) {
            if (guidedAction.checkSetId == GuidedAction.CHECKBOX_CHECK_SET_ID && guidedAction.isChecked) {
                idsToDelete.add(guidedAction.id)
            }
        }
        (activity as DvrSeriesDeletionActivity).setIdsToDelete(idsToDelete)
        if (!PermissionUtils.hasWriteExternalStorage(requireContext()) && doesAnySelectedRecordedProgramNeedWritePermission()) {
            DvrUiHelper.showWriteStoragePermissionRationaleDialog(requireActivity())
        } else {
            deleteSelectedIds()
        }
    }

    private fun doesAnySelectedRecordedProgramNeedWritePermission(): Boolean =
        recordings.any { r ->
            idsToDelete.contains(r.id) && DvrManager.isFile(r.dataUri) && !DvrManager.isFromBundledInput(r)
        }

    private fun deleteSelectedIds() {
        if (idsToDelete.isNotEmpty()) {
            dvrManager?.removeRecordedPrograms(idsToDelete, true)
        }
        Toast.makeText(
            context,
            resources.getQuantityString(R.plurals.dvr_msg_episodes_deleted, idsToDelete.size, idsToDelete.size, recordings.size),
            Toast.LENGTH_LONG,
        ).show()
        finishGuidedStepSupportFragments()
    }

    private fun getWatchedString(watchedPositionMs: Long, durationMs: Long): String =
        if (durationMs > WATCHED_TIME_UNIT_THRESHOLD) {
            resources.getString(
                R.string.dvr_series_watched_info_minutes,
                maxOf(1, Utils.getRoundOffMinsFromMs(watchedPositionMs)),
                Utils.getRoundOffMinsFromMs(durationMs),
            )
        } else {
            resources.getString(
                R.string.dvr_series_watched_info_seconds,
                maxOf(1L, TimeUnit.MILLISECONDS.toSeconds(watchedPositionMs)),
                TimeUnit.MILLISECONDS.toSeconds(durationMs),
            )
        }

    private fun updateSelectAllState(): Boolean {
        for (guidedAction in actions) {
            if (guidedAction.checkSetId == GuidedAction.CHECKBOX_CHECK_SET_ID && !guidedAction.isChecked) {
                if (allSelected) {
                    updateSelectAllState(findActionById(ACTION_ID_SELECT_ALL), false)
                }
                return false
            }
        }
        if (!allSelected) {
            updateSelectAllState(findActionById(ACTION_ID_SELECT_ALL), true)
        }
        return true
    }

    private fun updateSelectAllState(selectAll: GuidedAction?, select: Boolean) {
        selectAll ?: return
        selectAll.title = if (select) getString(R.string.dvr_series_deselect_all) else getString(R.string.dvr_series_select_all)
        notifyActionChanged(findActionPositionById(ACTION_ID_SELECT_ALL))
    }

    companion object {
        private val WATCHED_TIME_UNIT_THRESHOLD = TimeUnit.MINUTES.toMillis(2)

        // Aufnahme-IDs (zufällige positive Zahlen) dienen als IDs der Häkchen-Aktionen,
        // daher sind die übrigen Aktions-IDs negativ
        private const val ACTION_ID_SELECT_WATCHED = -110L
        private const val ACTION_ID_SELECT_ALL = -111L
        private const val ACTION_ID_DELETE = -112L
    }
}
