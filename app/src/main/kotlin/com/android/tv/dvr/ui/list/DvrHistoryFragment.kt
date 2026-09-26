package com.android.tv.dvr.ui.list

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.app.DetailsSupportFragment
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.list.SchedulesHeaderRowPresenter.DateHeaderRowPresenter

/** Fragment mit dem Aufnahmeverlauf. Leanback DetailsFragment → DetailsSupportFragment. */
class DvrHistoryFragment : DetailsSupportFragment(),
    DvrDataManager.ScheduledRecordingListener,
    DvrDataManager.RecordedProgramListener {

    private var historyRowsAdapter: DvrHistoryRowAdapter? = null
    private var emptyInfoScreenView: TextView? = null
    private lateinit var dvrDataManager: DvrDataManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val context = requireContext()
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(SchedulesHeaderRow::class.java, DateHeaderRowPresenter(context))
        presenterSelector.addClassPresenter(ScheduleRow::class.java, ScheduleRowPresenter(context))
        val singletons = TvSingletons.getSingletons(context)
        dvrDataManager = singletons.getDvrDataManager()
        // Abweichung: UiFlags entfernt (maxHistoryDays fest im Adapter).
        val adapter = DvrHistoryRowAdapter(context, presenterSelector, singletons.getClock(), dvrDataManager)
        historyRowsAdapter = adapter
        setAdapter(adapter)
        adapter.start()
        dvrDataManager.addScheduledRecordingListener(this)
        dvrDataManager.addRecordedProgramListener(this)
        emptyInfoScreenView = requireActivity().findViewById(R.id.empty_info_screen)
    }

    override fun onDestroy() {
        dvrDataManager.removeScheduledRecordingListener(this)
        dvrDataManager.removeRecordedProgramListener(this)
        super.onDestroy()
    }

    /** Leer-Hinweis anzeigen. */
    fun showEmptyMessage() {
        val view = emptyInfoScreenView ?: return
        view.setText(R.string.dvr_history_empty_state)
        if (view.visibility != View.VISIBLE) view.visibility = View.VISIBLE
    }

    /** Leer-Hinweis ausblenden. */
    fun hideEmptyMessage() {
        val view = emptyInfoScreenView ?: return
        if (view.visibility == View.VISIBLE) view.visibility = View.GONE
    }

    // Workaround für b/31046014
    // Abweichung: onInflateTitleView ist in Leanback 1.2 @NonNull; statt null dort liefern wird
    // installTitleView überschrieben – gleiches Ergebnis wie im Original (keine Titelleiste).
    override fun installTitleView(inflater: LayoutInflater, parent: ViewGroup, savedInstanceState: Bundle?) {
        setTitleView(null)
    }

    override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
        val adapter = historyRowsAdapter ?: return
        scheduledRecordings.forEach { adapter.onScheduledRecordingAdded(it) }
        if (adapter.size() > 0) hideEmptyMessage()
    }

    override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
        val adapter = historyRowsAdapter ?: return
        scheduledRecordings.forEach { adapter.onScheduledRecordingRemoved(it) }
        if (adapter.size() == 0) showEmptyMessage()
    }

    override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
        val adapter = historyRowsAdapter ?: return
        scheduledRecordings.forEach { adapter.onScheduledRecordingUpdated(it) }
        if (adapter.size() == 0) showEmptyMessage() else hideEmptyMessage()
    }

    override fun onRecordedProgramsAdded(vararg recordedPrograms: RecordedProgram) {
        val adapter = historyRowsAdapter ?: return
        recordedPrograms.forEach { adapter.onScheduledRecordingAdded(it) }
        if (adapter.size() > 0) hideEmptyMessage()
    }

    override fun onRecordedProgramsChanged(vararg recordedPrograms: RecordedProgram) {
        val adapter = historyRowsAdapter ?: return
        recordedPrograms.forEach { adapter.onScheduledRecordingUpdated(it) }
        if (adapter.size() == 0) showEmptyMessage() else hideEmptyMessage()
    }

    override fun onRecordedProgramsRemoved(vararg recordedPrograms: RecordedProgram) {
        val adapter = historyRowsAdapter ?: return
        recordedPrograms.forEach { adapter.onScheduledRecordingRemoved(it) }
        if (adapter.size() == 0) showEmptyMessage()
    }
}
