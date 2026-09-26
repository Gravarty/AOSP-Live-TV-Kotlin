package com.android.tv.dvr.ui.browse

import android.os.Bundle
import com.android.tv.TvSingletons
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.ui.DetailsActivity

/** Detailansicht für Aufnahmen ([ScheduledRecording]) im DVR. */
abstract class RecordingDetailsFragment : DvrDetailsFragment() {
    private var recording: ScheduledRecording? = null

    override fun onCreateInternal() {
        setDetailsOverviewRow(DetailsContent.createFromScheduledRecording(requireContext(), getRecording()))
    }

    override fun onLoadRecordingDetails(args: Bundle): Boolean {
        val scheduledRecordingId = args.getLong(DetailsActivity.RECORDING_ID)
        recording = TvSingletons.getSingletons(requireContext()).getDvrDataManager()
            .getScheduledRecording(scheduledRecordingId)
        return recording != null
    }

    protected fun getScheduledRecording(): ScheduledRecording = getRecording()

    /**
     * Die [ScheduledRecording] dieses Fragments. Erst nach erfolgreichem
     * [onLoadRecordingDetails] gültig (vorher im Original null).
     */
    fun getRecording(): ScheduledRecording = recording!!

    /** Null-sichere Variante für Callbacks, die vor dem Laden eintreffen können. */
    protected val recordingOrNull: ScheduledRecording?
        get() = recording
}
