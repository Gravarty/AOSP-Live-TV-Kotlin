package com.android.tv.dvr.ui.browse

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver.OnGlobalFocusChangeListener
import androidx.leanback.app.BrowseSupportFragment
import androidx.leanback.widget.ArrayObjectAdapter
import androidx.leanback.widget.ClassPresenterSelector
import androidx.leanback.widget.HeaderItem
import androidx.leanback.widget.ListRow
import androidx.leanback.widget.TitleViewAdapter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.GenreItems
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.SortedArrayAdapter

/** DVR-Bibliothek. Leanback BrowseFragment → BrowseSupportFragment (AndroidX). */
class DvrBrowseFragment : BrowseSupportFragment(),
    DvrDataManager.RecordedProgramListener,
    DvrDataManager.ScheduledRecordingListener,
    DvrDataManager.SeriesRecordingListener,
    DvrDataManager.OnDvrScheduleLoadFinishedListener,
    DvrDataManager.OnRecordedProgramLoadFinishedListener {

    private var shouldShowScheduleRow = false
    private var entranceTransitionEnded = false

    private lateinit var recentAdapter: RecentRowAdapter
    private var scheduleAdapter: ScheduleAdapter? = null
    private lateinit var seriesAdapter: SeriesAdapter
    private val genreAdapters = arrayOfNulls<RecordedProgramAdapter>(GenreItems.getGenreCount() + 1)
    private lateinit var recentRow: ListRow
    private lateinit var scheduledRow: ListRow
    private lateinit var seriesRow: ListRow
    private val genreRows = arrayOfNulls<ListRow>(GenreItems.getGenreCount() + 1)
    private lateinit var genreLabels: MutableList<String>
    private lateinit var dvrDataManager: DvrDataManager
    // Ohne DVR null; die Bibliothek wird nur mit DVR geöffnet.
    private var dvrScheduleManager: DvrScheduleManager? = null
    private lateinit var rowsAdapter: ArrayObjectAdapter
    private lateinit var presenterSelector: ClassPresenterSelector
    private val seriesId2LatestProgram = HashMap<String?, RecordedProgram?>()
    // Handler() ist veraltet → expliziter Main-Looper.
    private val handler = Handler(Looper.getMainLooper())

    private val onGlobalFocusChangeListener = OnGlobalFocusChangeListener { oldFocus, newFocus ->
        if (oldFocus is RecordingCardView) oldFocus.expandTitle(false, true)
        if (newFocus is RecordingCardView) {
            // Während des Header-Übergangs sofort (ohne Animation) aufklappen.
            newFocus.expandTitle(true, !isInHeadersTransition)
        }
    }

    private val recordedProgramComparator = Comparator<Any> { l, r ->
        var lhs: Any? = l
        var rhs: Any? = r
        if (lhs is SeriesRecording) lhs = seriesId2LatestProgram[lhs.seriesId]
        if (rhs is SeriesRecording) rhs = seriesId2LatestProgram[rhs.seriesId]
        if (lhs is RecordedProgram) {
            if (rhs is RecordedProgram) {
                RecordedProgram.START_TIME_THEN_ID_COMPARATOR.reversed().compare(lhs, rhs)
            } else {
                -1
            }
        } else if (rhs is RecordedProgram) {
            1
        } else {
            0
        }
    }

    private val onConflictStateChangeListener = DvrScheduleManager.OnConflictStateChangeListener { _, schedules ->
        if (scheduleAdapter != null) {
            for (schedule in schedules) onScheduledRecordingConflictStatusChanged(schedule)
        }
    }

    private val updateRowsRunnable = Runnable { updateRows() }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (DEBUG) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        val context = requireContext()
        val singletons = TvSingletons.getSingletons(context)
        dvrDataManager = singletons.getDvrDataManager()
        dvrScheduleManager = singletons.getDvrScheduleManager()
        presenterSelector = ClassPresenterSelector()
            .addClassPresenter(ScheduledRecording::class.java, ScheduledRecordingPresenter(context))
            .addClassPresenter(RecordedProgram::class.java, RecordedProgramPresenter(context))
            .addClassPresenter(SeriesRecording::class.java, SeriesRecordingPresenter(context))
            .addClassPresenter(FullScheduleCardHolder::class.java, FullSchedulesCardPresenter(context))
            .addClassPresenter(DvrHistoryCardHolder::class.java, DvrHistoryCardPresenter(context))

        genreLabels = GenreItems.getLabels(context).toMutableList()
        genreLabels.add(getString(R.string.dvr_main_others))
        prepareUiElements()
        if (!startBrowseIfDvrInitialized()) {
            if (!dvrDataManager.isDvrScheduleLoadFinished) {
                dvrDataManager.addDvrScheduleLoadFinishedListener(this)
            }
            if (!dvrDataManager.isRecordedProgramLoadFinished) {
                dvrDataManager.addRecordedProgramLoadFinishedListener(this)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalFocusChangeListener(onGlobalFocusChangeListener)
    }

    override fun onDestroyView() {
        requireView().viewTreeObserver.removeOnGlobalFocusChangeListener(onGlobalFocusChangeListener)
        super.onDestroyView()
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        handler.removeCallbacks(updateRowsRunnable)
        dvrScheduleManager?.removeOnConflictStateChangeListener(onConflictStateChangeListener)
        dvrDataManager.removeRecordedProgramListener(this)
        dvrDataManager.removeScheduledRecordingListener(this)
        dvrDataManager.removeSeriesRecordingListener(this)
        dvrDataManager.removeDvrScheduleLoadFinishedListener(this)
        dvrDataManager.removeRecordedProgramLoadFinishedListener(this)
        rowsAdapter.clear()
        seriesId2LatestProgram.clear()
        for (presenter in presenterSelector.presenters.orEmpty()) {
            if (presenter is DvrItemPresenter<*>) presenter.unbindAllViewHolders()
        }
        super.onDestroy()
    }

    override fun onDvrScheduleLoadFinished() {
        startBrowseIfDvrInitialized()
        dvrDataManager.removeDvrScheduleLoadFinishedListener(this)
    }

    override fun onRecordedProgramLoadFinished() {
        startBrowseIfDvrInitialized()
        dvrDataManager.removeRecordedProgramLoadFinishedListener(this)
    }

    override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) {
        for (recordedProgram in recordedPrograms) handleRecordedProgramAdded(recordedProgram, true)
        postUpdateRows()
    }

    override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) {
        for (recordedProgram in recordedPrograms) {
            if (recordedProgram.isVisible) handleRecordedProgramChanged(recordedProgram)
        }
        postUpdateRows()
    }

    override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) {
        for (recordedProgram in recordedPrograms) handleRecordedProgramRemoved(recordedProgram)
        postUpdateRows()
    }

    // updateRows() ist hier unnötig, da die Zeile der geplanten Aufnahmen immer angezeigt wird.
    override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
        for (scheduleRecording in scheduledRecordings) {
            if (needToShowScheduledRecording(scheduleRecording)) {
                scheduleAdapter?.add(scheduleRecording)
            } else if (scheduleRecording.state == ScheduledRecording.STATE_RECORDING_FAILED) {
                recentAdapter.add(scheduleRecording)
            }
        }
    }

    override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
        for (scheduleRecording in scheduledRecordings) {
            scheduleAdapter?.remove(scheduleRecording)
            if (scheduleRecording.state == ScheduledRecording.STATE_RECORDING_FAILED) {
                recentAdapter.remove(scheduleRecording)
            }
        }
    }

    override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
        for (scheduleRecording in scheduledRecordings) {
            if (needToShowScheduledRecording(scheduleRecording)) {
                scheduleAdapter?.change(scheduleRecording)
            } else {
                scheduleAdapter?.removeWithId(scheduleRecording)
            }
            if (scheduleRecording.state == ScheduledRecording.STATE_RECORDING_FAILED) {
                recentAdapter.change(scheduleRecording)
            }
        }
    }

    private fun onScheduledRecordingConflictStatusChanged(vararg schedules: ScheduledRecording) {
        val adapter = scheduleAdapter ?: return
        for (schedule in schedules) {
            if (needToShowScheduledRecording(schedule)) {
                if (adapter.contains(schedule)) adapter.change(schedule)
            } else {
                adapter.removeWithId(schedule)
            }
        }
    }

    override fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording) {
        handleSeriesRecordingsAdded(seriesRecordings.asList())
        postUpdateRows()
    }

    override fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording) {
        handleSeriesRecordingsRemoved(seriesRecordings.asList())
        postUpdateRows()
    }

    override fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording) {
        handleSeriesRecordingsChanged(seriesRecordings.asList())
        postUpdateRows()
    }

    // Workaround für b/29108300
    override fun showTitle(flags: Int) {
        super.showTitle(flags and TitleViewAdapter.SEARCH_VIEW_VISIBLE.inv())
    }

    override fun onEntranceTransitionEnd() {
        super.onEntranceTransitionEnd()
        if (shouldShowScheduleRow) showScheduledRowInternal()
        entranceTransitionEnded = true
    }

    internal fun showScheduledRow() {
        if (!entranceTransitionEnded) {
            headersState = HEADERS_HIDDEN
            shouldShowScheduleRow = true
        } else {
            showScheduledRowInternal()
        }
    }

    private fun showScheduledRowInternal() {
        setSelectedPosition(rowsAdapter.indexOf(scheduledRow), true, null)
        if (headersState == HEADERS_ENABLED) startHeadersTransition(false)
        shouldShowScheduleRow = false
    }

    private fun prepareUiElements() {
        badgeDrawable = requireActivity().getDrawable(R.drawable.ic_dvr_badge)
        headersState = HEADERS_ENABLED
        isHeadersTransitionOnBackEnabled = false
        brandColor = resources.getColor(R.color.program_guide_side_panel_background, null)
        rowsAdapter = ArrayObjectAdapter(DvrListRowPresenter(requireContext()))
        adapter = rowsAdapter
        prepareEntranceTransition()
    }

    private fun startBrowseIfDvrInitialized(): Boolean {
        if (!dvrDataManager.isInitialized) return false
        // Zeilen aufbauen
        recentAdapter = RecentRowAdapter(MAX_RECENT_ITEM_COUNT)
        val schedules = ScheduleAdapter(MAX_SCHEDULED_ITEM_COUNT)
        scheduleAdapter = schedules
        seriesAdapter = SeriesAdapter()
        for (i in genreAdapters.indices) genreAdapters[i] = RecordedProgramAdapter()
        // Geplante Aufnahmen: nur nicht gestartete oder laufende
        onScheduledRecordingAdded(*dvrDataManager.getAvailableScheduledRecordings().toTypedArray())
        schedules.addExtraItem(FullScheduleCardHolder.FULL_SCHEDULE_CARD_HOLDER)
        // Aufnahmen
        for (recordedProgram in dvrDataManager.getRecordedPrograms()) {
            if (recordedProgram.isVisible) handleRecordedProgramAdded(recordedProgram, false)
        }
        // Nur fehlgeschlagene Aufnahmen
        for (scheduledRecording in dvrDataManager.getFailedScheduledRecordings()) {
            onScheduledRecordingAdded(scheduledRecording)
        }
        recentAdapter.addExtraItem(DvrHistoryCardHolder.DVR_HISTORY_CARD_HOLDER)

        // Serienaufnahmen nach den Aufnahmen hinzufügen, da dabei die jeweils neueste Aufnahme
        // je Serie ermittelt wird.
        handleSeriesRecordingsAdded(dvrDataManager.getSeriesRecordings())
        recentRow = ListRow(HeaderItem(getString(R.string.dvr_main_recent)), recentAdapter)
        scheduledRow = ListRow(HeaderItem(getString(R.string.dvr_main_scheduled)), schedules)
        seriesRow = ListRow(HeaderItem(getString(R.string.dvr_main_series)), seriesAdapter)
        rowsAdapter.add(scheduledRow)
        updateRows()
        // Listener registrieren
        dvrDataManager.addRecordedProgramListener(this)
        dvrDataManager.addScheduledRecordingListener(this)
        dvrDataManager.addSeriesRecordingListener(this)
        dvrScheduleManager?.addOnConflictStateChangeListener(onConflictStateChangeListener)
        startEntranceTransition()
        return true
    }

    private fun handleRecordedProgramAdded(recordedProgram: RecordedProgram, updateSeriesRecording: Boolean) {
        recentAdapter.add(recordedProgram)
        val seriesId = recordedProgram.seriesId
        var seriesRecording: SeriesRecording? = null
        if (!seriesId.isNullOrEmpty()) {
            seriesRecording = dvrDataManager.getSeriesRecording(seriesId)
            val latestProgram = seriesId2LatestProgram[seriesId]
            if (latestProgram == null ||
                RecordedProgram.START_TIME_THEN_ID_COMPARATOR.compare(latestProgram, recordedProgram) < 0
            ) {
                seriesId2LatestProgram[seriesId] = recordedProgram
                if (updateSeriesRecording && seriesRecording != null) onSeriesRecordingChanged(seriesRecording)
            }
        }
        if (seriesRecording == null) {
            for (adapter in getGenreAdapters(recordedProgram.canonicalGenres)) adapter.add(recordedProgram)
        }
    }

    private fun handleRecordedProgramRemoved(recordedProgram: RecordedProgram) {
        recentAdapter.remove(recordedProgram)
        val seriesId = recordedProgram.seriesId
        if (!seriesId.isNullOrEmpty()) {
            val seriesRecording = dvrDataManager.getSeriesRecording(seriesId)
            val latestProgram = seriesId2LatestProgram[seriesId]
            if (latestProgram != null && latestProgram.id == recordedProgram.id && seriesRecording != null) {
                updateLatestRecordedProgram(seriesRecording)
                onSeriesRecordingChanged(seriesRecording)
            }
        }
        for (adapter in getGenreAdapters(recordedProgram.canonicalGenres)) adapter.remove(recordedProgram)
    }

    private fun handleRecordedProgramChanged(recordedProgram: RecordedProgram) {
        recentAdapter.change(recordedProgram)
        val seriesId = recordedProgram.seriesId
        var seriesRecording: SeriesRecording? = null
        if (!seriesId.isNullOrEmpty()) {
            seriesRecording = dvrDataManager.getSeriesRecording(seriesId)
            val latestProgram = seriesId2LatestProgram[seriesId]
            if (latestProgram == null ||
                RecordedProgram.START_TIME_THEN_ID_COMPARATOR.compare(latestProgram, recordedProgram) <= 0
            ) {
                seriesId2LatestProgram[seriesId] = recordedProgram
                if (seriesRecording != null) onSeriesRecordingChanged(seriesRecording)
            } else if (latestProgram.id == recordedProgram.id) {
                if (seriesRecording != null) {
                    updateLatestRecordedProgram(seriesRecording)
                    onSeriesRecordingChanged(seriesRecording)
                }
            }
        }
        if (seriesRecording == null) {
            updateGenreAdapters(getGenreAdapters(recordedProgram.canonicalGenres), recordedProgram)
        } else {
            updateGenreAdapters(ArrayList(), recordedProgram)
        }
    }

    private fun handleSeriesRecordingsAdded(seriesRecordings: List<SeriesRecording>) {
        for (seriesRecording in seriesRecordings) {
            seriesAdapter.add(seriesRecording)
            if (seriesId2LatestProgram[seriesRecording.seriesId] != null) {
                for (adapter in getGenreAdapters(seriesRecording.canonicalGenreIds)) adapter.add(seriesRecording)
            }
        }
    }

    private fun handleSeriesRecordingsRemoved(seriesRecordings: List<SeriesRecording>) {
        for (seriesRecording in seriesRecordings) {
            seriesAdapter.remove(seriesRecording)
            for (adapter in getGenreAdapters(seriesRecording.canonicalGenreIds)) adapter.remove(seriesRecording)
        }
    }

    private fun handleSeriesRecordingsChanged(seriesRecordings: List<SeriesRecording>) {
        for (seriesRecording in seriesRecordings) {
            seriesAdapter.change(seriesRecording)
            if (seriesId2LatestProgram[seriesRecording.seriesId] != null) {
                updateGenreAdapters(getGenreAdapters(seriesRecording.canonicalGenreIds), seriesRecording)
            } else {
                // Serie ohne Aufnahme aus allen Genre-Zeilen entfernen
                updateGenreAdapters(ArrayList(), seriesRecording)
            }
        }
    }

    private fun getGenreAdapters(genres: List<String>?): List<RecordedProgramAdapter> {
        val result = ArrayList<RecordedProgramAdapter>()
        if (genres.isNullOrEmpty()) {
            genreAdapters[genreAdapters.size - 1]?.let { result.add(it) }
        } else {
            for (genre in genres) {
                val genreId = GenreItems.getId(genre)
                if (genreId >= genreAdapters.size) {
                    Log.d(TAG, "Wrong Genre ID: $genreId")
                } else {
                    genreAdapters[genreId]?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun getGenreAdapters(genreIds: IntArray?): List<RecordedProgramAdapter> {
        val result = ArrayList<RecordedProgramAdapter>()
        if (genreIds == null || genreIds.isEmpty()) {
            genreAdapters[genreAdapters.size - 1]?.let { result.add(it) }
        } else {
            for (genreId in genreIds) {
                if (genreId >= genreAdapters.size) {
                    Log.d(TAG, "Wrong Genre ID: $genreId")
                } else {
                    genreAdapters[genreId]?.let { result.add(it) }
                }
            }
        }
        return result
    }

    private fun updateGenreAdapters(adapters: List<RecordedProgramAdapter>, r: Any) {
        for (adapter in genreAdapters) {
            if (adapter == null) continue
            if (adapters.contains(adapter)) adapter.change(r) else adapter.remove(r)
        }
    }

    private fun postUpdateRows() {
        handler.removeCallbacks(updateRowsRunnable)
        handler.post(updateRowsRunnable)
    }

    private fun updateRows() {
        var visibleRowsCount = 1 // Die Zeile der geplanten Aufnahmen ist nie leer
        if (recentAdapter.size() <= 1) {
            // Zeile entfernen, wenn nur die Verlaufs-Karte enthalten ist
            rowsAdapter.remove(recentRow)
        } else {
            if (rowsAdapter.indexOf(recentRow) < 0) rowsAdapter.add(0, recentRow)
            visibleRowsCount++
        }
        if (seriesAdapter.isEmpty()) {
            rowsAdapter.remove(seriesRow)
        } else {
            if (rowsAdapter.indexOf(seriesRow) < 0) rowsAdapter.add(visibleRowsCount, seriesRow)
            visibleRowsCount++
        }
        for (i in genreAdapters.indices) {
            val adapter = genreAdapters[i] ?: continue
            if (adapter.isEmpty()) {
                genreRows[i]?.let { rowsAdapter.remove(it) }
            } else {
                val row = genreRows[i]
                if (row == null || rowsAdapter.indexOf(row) < 0) {
                    val newRow = ListRow(HeaderItem(genreLabels[i]), adapter)
                    genreRows[i] = newRow
                    rowsAdapter.add(visibleRowsCount, newRow)
                }
                visibleRowsCount++
            }
        }
        if (selectedPosition >= rowsAdapter.size()) {
            selectedPosition = rowsAdapter.size() - 1
        }
    }

    private fun needToShowScheduledRecording(recording: ScheduledRecording): Boolean {
        val state = recording.state
        return state == ScheduledRecording.STATE_RECORDING_IN_PROGRESS ||
            state == ScheduledRecording.STATE_RECORDING_NOT_STARTED
    }

    private fun updateLatestRecordedProgram(seriesRecording: SeriesRecording) {
        var latestProgram: RecordedProgram? = null
        for (program in dvrDataManager.getRecordedPrograms(seriesRecording.id)) {
            if (latestProgram == null ||
                RecordedProgram.START_TIME_THEN_ID_COMPARATOR.compare(latestProgram, program) < 0
            ) {
                latestProgram = program
            }
        }
        seriesId2LatestProgram[seriesRecording.seriesId] = latestProgram
    }

    private inner class ScheduleAdapter(maxItemCount: Int) :
        SortedArrayAdapter<Any>(presenterSelector, SCHEDULE_COMPARATOR, maxItemCount) {
        override fun getId(item: Any): Long = if (item is ScheduledRecording) item.id else -1
    }

    private inner class SeriesAdapter : SortedArrayAdapter<SeriesRecording>(
        presenterSelector,
        Comparator<SeriesRecording> { lhs, rhs ->
            if (lhs.isStopped && !rhs.isStopped) {
                1
            } else if (!lhs.isStopped && rhs.isStopped) {
                -1
            } else {
                SeriesRecording.PRIORITY_COMPARATOR.compare(lhs, rhs)
            }
        },
    ) {
        override fun getId(item: SeriesRecording): Long = item.id
    }

    private inner class RecordedProgramAdapter(maxItemCount: Int = Int.MAX_VALUE) :
        SortedArrayAdapter<Any>(presenterSelector, recordedProgramComparator, maxItemCount) {
        // Negierte IDs für Aufnahmen, damit die IDs stabil (eindeutig) bleiben.
        override fun getId(item: Any): Long = when (item) {
            is SeriesRecording -> item.id
            is RecordedProgram -> -item.id - 1
            else -> -1
        }
    }

    private inner class RecentRowAdapter(maxItemCount: Int) :
        SortedArrayAdapter<Any>(presenterSelector, RECENT_ROW_COMPARATOR, maxItemCount) {
        // Negierte IDs für geplante Aufnahmen, damit die IDs stabil (eindeutig) bleiben.
        override fun getId(item: Any): Long = when (item) {
            is ScheduledRecording -> -item.id - 1
            is RecordedProgram -> item.id
            else -> -1
        }
    }

    companion object {
        private const val TAG = "DvrBrowseFragment"
        private const val DEBUG = false

        private const val MAX_RECENT_ITEM_COUNT = 4
        private const val MAX_SCHEDULED_ITEM_COUNT = 4

        private val SCHEDULE_COMPARATOR = Comparator<Any> { lhs, rhs ->
            if (lhs is ScheduledRecording) {
                if (rhs is ScheduledRecording) {
                    ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.compare(lhs, rhs)
                } else {
                    -1
                }
            } else if (rhs is ScheduledRecording) {
                1
            } else {
                0
            }
        }

        @JvmField
        internal val RECENT_ROW_COMPARATOR = Comparator<Any> { lhs, rhs ->
            if (lhs is ScheduledRecording) {
                when (rhs) {
                    is ScheduledRecording ->
                        ScheduledRecording.START_TIME_THEN_PRIORITY_THEN_ID_COMPARATOR.reversed().compare(lhs, rhs)
                    is RecordedProgram -> {
                        val compare = rhs.startTimeUtcMillis.compareTo(lhs.startTimeMs)
                        // Bei gleicher Startzeit die Aufnahme zuerst
                        if (compare == 0) 1 else compare
                    }
                    else -> -1
                }
            } else if (lhs is RecordedProgram) {
                when (rhs) {
                    is RecordedProgram -> RecordedProgram.START_TIME_THEN_ID_COMPARATOR.reversed().compare(lhs, rhs)
                    is ScheduledRecording -> {
                        val compare = rhs.startTimeMs.compareTo(lhs.startTimeUtcMillis)
                        // Bei gleicher Startzeit die Aufnahme zuerst
                        if (compare == 0) -1 else compare
                    }
                    else -> -1
                }
            } else {
                if (rhs !is RecordedProgram && rhs !is ScheduledRecording) 0 else 1
            }
        }
    }
}
