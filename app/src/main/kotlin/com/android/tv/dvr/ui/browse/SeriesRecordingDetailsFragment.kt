package com.android.tv.dvr.ui.browse

import android.graphics.drawable.Drawable
import android.media.tv.TvInputManager
import android.os.Bundle
import android.text.TextUtils
import androidx.leanback.widget.Action
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.DetailsOverviewRow
import androidx.leanback.widget.DetailsOverviewRowPresenter
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.PresenterSelector
import androidx.leanback.widget.OnActionClickedListener
import androidx.leanback.widget.SparseArrayObjectAdapter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.api.BaseProgram
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrWatchedPositionManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.dvr.ui.SortedArrayAdapter
import com.android.tv.ui.DetailsActivity

/** Detailansicht einer Serienaufnahme (Staffel-Zeilen mit den Folgen). */
class SeriesRecordingDetailsFragment : DvrDetailsFragment(),
    DvrDataManager.SeriesRecordingListener, DvrDataManager.RecordedProgramListener {

    private lateinit var dvrWatchedPositionManager: DvrWatchedPositionManager
    private lateinit var dvrDataManager: DvrDataManager

    private var series: SeriesRecording? = null
    // HINWEIS: nur beim Aufbau verwenden; danach zum Sparen von Ressourcen auf null setzen.
    private var recordedPrograms: List<RecordedProgram>? = null
    private var recommendRecordedProgram: RecordedProgram? = null
    private var seasonRowCount = 0
    private lateinit var actionsAdapter: SparseArrayObjectAdapter
    private lateinit var deleteAction: Action

    private var paused = false
    private var initialPlaybackPositionMs = 0L
    private lateinit var watchLabel: String
    private lateinit var resumeLabel: String
    private var watchDrawable: Drawable? = null
    private lateinit var recordedProgramPresenter: RecordedProgramPresenter

    override fun onCreate(savedInstanceState: Bundle?) {
        dvrDataManager = TvSingletons.getSingletons(requireActivity()).getDvrDataManager()
        watchLabel = getString(R.string.dvr_detail_watch)
        resumeLabel = getString(R.string.dvr_detail_series_resume)
        watchDrawable = resources.getDrawable(R.drawable.lb_ic_play, null)
        recordedProgramPresenter = RecordedProgramPresenter(requireContext(), true, true)
        super.onCreate(savedInstanceState)
    }

    override fun onCreateInternal() {
        dvrWatchedPositionManager = TvSingletons.getSingletons(requireActivity()).getDvrWatchedPositionManager()
        setDetailsOverviewRow(DetailsContent.createFromSeriesRecording(requireContext(), requireSeries()))
        setupRecordedProgramsRow()
        dvrDataManager.addSeriesRecordingListener(this)
        dvrDataManager.addRecordedProgramListener(this)
        recordedPrograms = null
    }

    override fun onResume() {
        super.onResume()
        if (paused) {
            updateWatchAction()
            paused = false
        }
    }

    override fun onPause() {
        super.onPause()
        paused = true
    }

    private fun requireSeries(): SeriesRecording = series!!

    private fun updateWatchAction() {
        // Original sortiert mit RecordedProgram.EPISODE_COMPARATOR (= BaseProgram.EPISODE_COMPARATOR).
        val programs = dvrDataManager.getRecordedPrograms(requireSeries().id).sortedWith(BaseProgram.EPISODE_COMPARATOR)
        val recommend = getRecommendProgram(programs)
        recommendRecordedProgram = recommend
        if (recommend == null) {
            actionsAdapter.clear(ACTION_WATCH)
        } else {
            val episodeStatus: String
            if (dvrWatchedPositionManager.getWatchedStatus(recommend) ==
                DvrWatchedPositionManager.DVR_WATCHED_STATUS_WATCHING
            ) {
                episodeStatus = resumeLabel
                initialPlaybackPositionMs = dvrWatchedPositionManager.getWatchedPosition(recommend.id)
            } else {
                episodeStatus = watchLabel
                initialPlaybackPositionMs = TvInputManager.TIME_SHIFT_INVALID_TIME
            }
            val episodeDisplayNumber = recommend.getEpisodeDisplayNumber(requireContext())
            actionsAdapter.set(ACTION_WATCH,
                Action(ACTION_WATCH.toLong(), episodeStatus, episodeDisplayNumber, watchDrawable))
        }
    }

    override fun onLoadRecordingDetails(args: Bundle): Boolean {
        val recordId = args.getLong(DetailsActivity.RECORDING_ID)
        val s = TvSingletons.getSingletons(requireActivity()).getDvrDataManager().getSeriesRecording(recordId)
            ?: return false
        series = s
        recordedPrograms = dvrDataManager.getRecordedPrograms(s.id)
            .sortedWith(BaseProgram.SEASON_REVERSED_EPISODE_COMPARATOR)
        return true
    }

    override fun onCreatePresenterSelector(rowPresenter: DetailsOverviewRowPresenter): PresenterSelector =
        ClassPresenterSelector().apply {
            addClassPresenter(DetailsOverviewRow::class.java, rowPresenter)
            addClassPresenter(ListRow::class.java, DvrListRowPresenter(requireContext()))
        }

    override fun onCreateActionsAdapter(): SparseArrayObjectAdapter {
        actionsAdapter = SparseArrayObjectAdapter(ActionPresenterSelector())
        val res = resources
        updateWatchAction()
        actionsAdapter.set(ACTION_SERIES_SCHEDULES, Action(ACTION_SERIES_SCHEDULES.toLong(),
            getString(R.string.dvr_detail_view_schedule), null, res.getDrawable(R.drawable.ic_schedule_32dp, null)))
        deleteAction = Action(ACTION_DELETE.toLong(), getString(R.string.dvr_detail_series_delete), null,
            res.getDrawable(R.drawable.ic_delete_32dp, null))
        if (recordedPrograms?.isNotEmpty() == true) {
            actionsAdapter.set(ACTION_DELETE, deleteAction)
        }
        return actionsAdapter
    }

    private fun setupRecordedProgramsRow() {
        recordedPrograms?.forEach { addProgram(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        dvrDataManager.removeSeriesRecordingListener(this)
        dvrDataManager.removeRecordedProgramListener(this)
        series?.let { dvrDataManager.checkAndRemoveEmptySeriesRecording(it.id) }
        recordedProgramPresenter.unbindAllViewHolders()
    }

    override fun onCreateOnActionClickedListener() = OnActionClickedListener { action ->
        when (action.id) {
            ACTION_WATCH.toLong() -> recommendRecordedProgram?.let { startPlayback(it, initialPlaybackPositionMs) }
            ACTION_SERIES_SCHEDULES.toLong() -> DvrUiHelper.startSchedulesActivityForSeries(requireContext(), requireSeries())
            ACTION_DELETE.toLong() -> DvrUiHelper.startSeriesDeletionActivity(requireContext(), requireSeries().id)
        }
    }

    /** Die Liste ist nach Staffel und Folge sortiert. */
    private fun getRecommendProgram(programs: List<RecordedProgram>): RecordedProgram? {
        for (i in programs.indices.reversed()) {
            val program = programs[i]
            val watchedStatus = dvrWatchedPositionManager.getWatchedStatus(program)
            if (watchedStatus == DvrWatchedPositionManager.DVR_WATCHED_STATUS_NEW) continue
            if (watchedStatus == DvrWatchedPositionManager.DVR_WATCHED_STATUS_WATCHING) return program
            return if (i == programs.size - 1) program else programs[i + 1]
        }
        return programs.firstOrNull()
    }

    override fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording) {}

    override fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording) {
        for (s in seriesRecordings) {
            if (requireSeries().id == s.id) series = s
        }
    }

    override fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording) {
        if (seriesRecordings.any { it.id == requireSeries().id }) activity?.finish()
    }

    override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) {
        for (recordedProgram in recordedPrograms) {
            if (TextUtils.equals(recordedProgram.seriesId, requireSeries().seriesId)) {
                addProgram(recordedProgram)
                if (actionsAdapter.lookup(ACTION_DELETE) == null) {
                    actionsAdapter.set(ACTION_DELETE, deleteAction)
                }
            }
        }
    }

    override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) {
        // Nichts zu tun
    }

    override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) {
        for (recordedProgram in recordedPrograms) {
            if (TextUtils.equals(recordedProgram.seriesId, requireSeries().seriesId)) {
                val row = getSeasonRow(recordedProgram.seasonNumber, false)
                if (row != null) {
                    val adapter = row.adapter as SeasonRowAdapter
                    adapter.remove(recordedProgram)
                    if (adapter.isEmpty()) {
                        getRowsAdapter().remove(row)
                        if (getRowsAdapter().size() == 1) {
                            // Keine Staffel-Zeilen mehr, nur noch die DetailsOverviewRow.
                            actionsAdapter.clear(ACTION_DELETE)
                        }
                    }
                }
                if (recordedProgram.id == recommendRecordedProgram?.id) {
                    updateWatchAction()
                }
            }
        }
    }

    private fun addProgram(program: RecordedProgram) {
        val programSeasonNumber = if (TextUtils.isEmpty(program.seasonNumber)) "" else program.seasonNumber
        getOrCreateSeasonRowAdapter(programSeasonNumber).add(program)
    }

    private fun getOrCreateSeasonRowAdapter(seasonNumber: String): SeasonRowAdapter =
        getSeasonRow(seasonNumber, true)!!.adapter as SeasonRowAdapter

    private fun getSeasonRow(seasonNumberIn: String?, createNewRow: Boolean): ListRow? {
        val seasonNumber = if (seasonNumberIn.isNullOrEmpty()) "" else seasonNumberIn
        val rowsAdapter = getRowsAdapter()
        for (i in rowsAdapter.size() - 1 downTo 0) {
            val row = rowsAdapter.get(i)
            if (row is ListRow) {
                val compareResult = BaseProgram.numberCompare(seasonNumber, (row.adapter as SeasonRowAdapter).seasonNumber)
                if (compareResult == 0) {
                    return row
                } else if (compareResult < 0) {
                    return if (createNewRow) createNewSeasonRow(seasonNumber, i + 1) else null
                }
            }
        }
        return if (createNewRow) createNewSeasonRow(seasonNumber, rowsAdapter.size()) else null
    }

    private fun createNewSeasonRow(seasonNumber: String, position: Int): ListRow {
        // Serien-Titel kann null sein → leerer Header-Titel.
        val seasonTitle = if (seasonNumber.isEmpty()) requireSeries().title.orEmpty()
        else getString(R.string.dvr_detail_series_season_title, seasonNumber)
        val header = HeaderItem((seasonRowCount++).toLong(), seasonTitle)
        val selector = ClassPresenterSelector()
        selector.addClassPresenter(RecordedProgram::class.java, recordedProgramPresenter)
        val row = ListRow(header, SeasonRowAdapter(
            selector, Comparator<RecordedProgram> { lhs, rhs -> BaseProgram.EPISODE_COMPARATOR.compare(lhs, rhs) }, seasonNumber))
        getRowsAdapter().add(position, row)
        return row
    }

    private class SeasonRowAdapter(
        selector: PresenterSelector,
        comparator: Comparator<RecordedProgram>,
        val seasonNumber: String,
    ) : SortedArrayAdapter<RecordedProgram>(selector, comparator) {
        override fun getId(item: RecordedProgram): Long = item.id
    }

    companion object {
        private const val ACTION_WATCH = 1
        private const val ACTION_SERIES_SCHEDULES = 2
        private const val ACTION_DELETE = 3
    }
}
