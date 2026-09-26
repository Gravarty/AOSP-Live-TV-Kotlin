package com.android.tv.dvr.ui.list

import android.os.Bundle
import androidx.core.os.BundleCompat
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.list.SchedulesHeaderRowPresenter.DateHeaderRowPresenter

/** Fragment mit der Liste aller geplanten Aufnahmen. */
class DvrSchedulesFragment : BaseDvrSchedulesFragment() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (getRowsAdapter().size() == 0) showEmptyMessage(R.string.dvr_schedules_empty_state)
    }

    override fun onCreateHeaderRowPresenter(): SchedulesHeaderRowPresenter = DateHeaderRowPresenter(requireContext())

    override fun onCreateRowPresenter(): ScheduleRowPresenter = ScheduleRowPresenter(requireContext())

    override fun onCreateRowsAdapter(presenterSelector: ClassPresenterSelector): ScheduleRowAdapter =
        ScheduleRowAdapter(requireContext(), presenterSelector)

    override fun onScheduledRecordingAdded(vararg scheduledRecordings: ScheduledRecording) {
        super.onScheduledRecordingAdded(*scheduledRecordings)
        if (getRowsAdapter().size() > 0) hideEmptyMessage()
    }

    override fun onScheduledRecordingRemoved(vararg scheduledRecordings: ScheduledRecording) {
        super.onScheduledRecordingRemoved(*scheduledRecordings)
        if (getRowsAdapter().size() == 0) showEmptyMessage(R.string.dvr_schedules_empty_state)
    }

    override fun getFirstItemPosition(): Int {
        val recording = arguments?.let {
            BundleCompat.getParcelable(it, SCHEDULES_KEY_SCHEDULED_RECORDING, ScheduledRecording::class.java)
        }
        val selectedPosition = getRowsAdapter().let { a -> a.findRowByScheduledRecording(recording)?.let { a.indexOf(it) } ?: -1 }
        if (selectedPosition != -1) return selectedPosition
        return super.getFirstItemPosition()
    }

    companion object {
        /** Wie im Original über die Basisklasse erreichbar (Kotlin vererbt Companion-Konstanten nicht nach außen). */
        const val SCHEDULES_KEY_SCHEDULED_RECORDING = BaseDvrSchedulesFragment.SCHEDULES_KEY_SCHEDULED_RECORDING
    }
}
