package com.android.tv.dvr.ui.list

import android.content.Context
import android.database.ContentObserver
import android.media.tv.TvContract.Programs
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.transition.Fade
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.BundleCompat
import androidx.leanback.widget.ClassPresenterSelector
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrDataManager
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.EpisodicProgramLoadTask
import com.android.tv.dvr.ui.BigArguments

/** Fragment mit den Folgen einer Serienaufnahme; lädt bei EPG-/Kanaländerungen neu. */
class DvrSeriesSchedulesFragment : BaseDvrSchedulesFragment() {
    private lateinit var channelDataManager: ChannelDataManager
    private lateinit var dvrDataManager: DvrDataManager
    private var seriesRecording: SeriesRecording? = null
    private var programs: List<Program>? = null
    private var programLoadTask: EpisodicProgramLoadTask? = null

    private val seriesRecordingListener = object : DvrDataManager.SeriesRecordingListener {
        override fun onSeriesRecordingAdded(vararg seriesRecordings: SeriesRecording) {}

        override fun onSeriesRecordingRemoved(vararg seriesRecordings: SeriesRecording) {
            for (r in seriesRecordings) {
                if (r.id == seriesRecording?.id) {
                    activity?.finish()
                    return
                }
            }
        }

        override fun onSeriesRecordingChanged(vararg seriesRecordings: SeriesRecording) {
            for (r in seriesRecordings) {
                val adapter = getRowsAdapter()
                if (r.id == seriesRecording?.id && adapter is SeriesScheduleRowAdapter) {
                    adapter.onSeriesRecordingUpdated(r)
                    seriesRecording = r
                    updateEmptyMessage()
                    return
                }
            }
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private val contentObserver = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            super.onChange(selfChange, uri)
            executeProgramLoadingTask()
        }
    }

    private val channelListener = object : ChannelDataManager.Listener {
        override fun onLoadFinished() {}
        override fun onChannelListUpdated() = executeProgramLoadingTask()
        override fun onChannelBrowsableChanged() {}
    }

    init {
        enterTransition = Fade(Fade.IN)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        val args = arguments
        if (args != null) {
            seriesRecording = BundleCompat.getParcelable(args, SERIES_SCHEDULES_KEY_SERIES_RECORDING, SeriesRecording::class.java)
            @Suppress("UNCHECKED_CAST")
            programs = BigArguments.getArgument(SERIES_SCHEDULES_KEY_SERIES_PROGRAMS) as List<Program>?
            BigArguments.reset()
        }
        // Bugfix: Ohne Serienaufnahme wäre das Original später in onCreateRowsAdapter mit NPE abgestürzt.
        if (args == null || programs == null || seriesRecording == null) {
            requireActivity().finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Bugfix: Serienaufnahme fehlt (Activity wird bereits beendet) – leere Serie verhindert NPE.
        if (seriesRecording == null) seriesRecording = SeriesRecording.Builder().build()
        super.onCreate(savedInstanceState)
        val singletons = TvSingletons.getSingletons(requireContext())
        channelDataManager = singletons.getChannelDataManager()
        channelDataManager.addListener(channelListener)
        dvrDataManager = singletons.getDvrDataManager()
        dvrDataManager.addSeriesRecordingListener(seriesRecordingListener)
        requireContext().contentResolver.registerContentObserver(Programs.CONTENT_URI, true, contentObserver)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        onProgramsUpdated()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private fun onProgramsUpdated() {
        (getRowsAdapter() as SeriesScheduleRowAdapter).setPrograms(programs)
        updateEmptyMessage()
    }

    private fun updateEmptyMessage() {
        if (programs.isNullOrEmpty()) {
            if (seriesRecording?.state == SeriesRecording.STATE_SERIES_STOPPED) {
                showEmptyMessage(R.string.dvr_series_schedules_stopped_empty_state)
            } else {
                showEmptyMessage(R.string.dvr_series_schedules_empty_state)
            }
        } else {
            hideEmptyMessage()
        }
    }

    override fun onDestroy() {
        programLoadTask?.cancel(true)
        programLoadTask = null
        requireContext().contentResolver.unregisterContentObserver(contentObserver)
        handler.removeCallbacksAndMessages(null)
        channelDataManager.removeListener(channelListener)
        dvrDataManager.removeSeriesRecordingListener(seriesRecordingListener)
        super.onDestroy()
    }

    override fun onCreateHeaderRowPresenter(): SchedulesHeaderRowPresenter =
        SchedulesHeaderRowPresenter.SeriesRecordingHeaderRowPresenter(requireContext())

    override fun onCreateRowPresenter(): ScheduleRowPresenter = SeriesScheduleRowPresenter(requireContext())

    override fun onCreateRowsAdapter(presenterSelector: ClassPresenterSelector): ScheduleRowAdapter =
        SeriesScheduleRowAdapter(requireContext(), presenterSelector, seriesRecording!!)

    override fun getFirstItemPosition(): Int {
        if (seriesRecording?.state == SeriesRecording.STATE_SERIES_STOPPED) return 0
        return super.getFirstItemPosition()
    }

    private fun executeProgramLoadingTask() {
        programLoadTask?.cancel(true)
        val series = seriesRecording ?: return
        programLoadTask = object : EpisodicProgramLoadTask(requireContext(), series) {
            override fun onPostExecute(programs: List<Program>) {
                this@DvrSeriesSchedulesFragment.programs = programs
                onProgramsUpdated()
            }
        }.apply {
            setLoadCurrentProgram(true)
            setLoadDisallowedProgram(true)
            setLoadScheduledEpisode(true)
            setIgnoreChannelOption(true)
            execute()
        }
    }

    companion object {
        /** Schlüssel für die Serienaufnahme, deren Folgen angezeigt werden. Typ: [SeriesRecording]. */
        const val SERIES_SCHEDULES_KEY_SERIES_RECORDING = "series_schedules_key_series_recording"
        /** Schlüssel (in BigArguments) für die Sendungen der Serie. Typ: List<[Program]>. */
        const val SERIES_SCHEDULES_KEY_SERIES_PROGRAMS = "series_schedules_key_series_programs"
    }
}
