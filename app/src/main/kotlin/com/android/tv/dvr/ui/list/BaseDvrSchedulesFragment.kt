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
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.ScheduledRecording

/** Basis-Fragment für die Liste geplanter Aufnahmen. Leanback DetailsFragment → DetailsSupportFragment. */
abstract class BaseDvrSchedulesFragment : DetailsSupportFragment(),
    DvrDataManager.ScheduledRecordingListener,
    DvrScheduleManager.OnConflictStateChangeListener {

    private var scheduleRowsAdapter: ScheduleRowAdapter? = null
    private var emptyInfoScreenView: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val presenterSelector = ClassPresenterSelector()
        presenterSelector.addClassPresenter(SchedulesHeaderRow::class.java, onCreateHeaderRowPresenter())
        presenterSelector.addClassPresenter(ScheduleRow::class.java, onCreateRowPresenter())
        val adapter = onCreateRowsAdapter(presenterSelector)
        scheduleRowsAdapter = adapter
        setAdapter(adapter)
        adapter.start()
        val singletons = TvSingletons.getSingletons(requireContext())
        singletons.getDvrDataManager().addScheduledRecordingListener(this)
        singletons.getDvrScheduleManager()?.addOnConflictStateChangeListener(this)
        emptyInfoScreenView = requireActivity().findViewById(R.id.empty_info_screen)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, container, savedInstanceState)
        val firstItemPosition = getFirstItemPosition()
        if (firstItemPosition != -1) {
            rowsSupportFragment?.setSelectedPosition(firstItemPosition, false)
        }
        return view
    }

    /** Zeilen-Adapter (ab onCreate gesetzt). */
    protected fun getRowsAdapter(): ScheduleRowAdapter = scheduleRowsAdapter!!

    /** Leer-Hinweis anzeigen. */
    fun showEmptyMessage(messageId: Int) {
        val view = emptyInfoScreenView ?: return
        view.setText(messageId)
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

    override fun onDestroy() {
        val singletons = TvSingletons.getSingletons(requireContext())
        singletons.getDvrScheduleManager()?.removeOnConflictStateChangeListener(this)
        singletons.getDvrDataManager().removeScheduledRecordingListener(this)
        scheduleRowsAdapter?.stop()
        super.onDestroy()
    }

    /** Presenter für die Kopfzeilen erzeugen. */
    abstract fun onCreateHeaderRowPresenter(): SchedulesHeaderRowPresenter

    /** Presenter für die Zeilen erzeugen. */
    abstract fun onCreateRowPresenter(): ScheduleRowPresenter

    /** Zeilen-Adapter erzeugen. */
    abstract fun onCreateRowsAdapter(presenterSelector: ClassPresenterSelector): ScheduleRowAdapter

    /** Erste zu fokussierende Position in der Liste. */
    protected open fun getFirstItemPosition(): Int {
        val adapter = scheduleRowsAdapter ?: return -1
        for (i in 0 until adapter.size()) {
            if (adapter.get(i) is ScheduleRow) return i
        }
        return -1
    }

    override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
        scheduleRowsAdapter?.let { adapter -> scheduledRecordings.forEach { adapter.onScheduledRecordingAdded(it) } }
    }

    override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
        scheduleRowsAdapter?.let { adapter -> scheduledRecordings.forEach { adapter.onScheduledRecordingRemoved(it) } }
    }

    override fun onScheduledRecordingStatusChanged(vararg scheduledRecordings: ScheduledRecording) {
        scheduleRowsAdapter?.let { adapter -> scheduledRecordings.forEach { adapter.onScheduledRecordingUpdated(it, false) } }
    }

    override fun onConflictStateChange(conflict: Boolean, vararg schedules: ScheduledRecording) {
        scheduleRowsAdapter?.let { adapter -> schedules.forEach { adapter.onScheduledRecordingUpdated(it, true) } }
    }

    companion object {
        /** Schlüssel für die in der Liste auszuwählende Aufnahme. */
        const val SCHEDULES_KEY_SCHEDULED_RECORDING = "schedules_key_scheduled_recording"
    }
}
