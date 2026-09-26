package com.android.tv.dialog

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.util.Utils

/** Verlauf abgeschlossener/fehlgeschlagener Aufnahmen, neueste zuerst. */
class DvrHistoryDialogFragment : SafeDismissDialogFragment() {
    private val schedules = ArrayList<ScheduledRecording>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val singletons = TvSingletons.getSingletons(context)
        val channelDataManager = singletons.getChannelDataManager()
        singletons.getDvrDataManager().getAllScheduledRecordings()
            .filterTo(schedules) { !it.isInProgress && !it.isNotStarted }
        schedules.sortWith(ScheduledRecording.START_TIME_COMPARATOR.reversed())
        val inflater = LayoutInflater.from(context)
        val adapter = object : ArrayAdapter<ScheduledRecording>(context, R.layout.list_item_dvr_history, schedules) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = inflater.inflate(R.layout.list_item_dvr_history, parent, false)
                val schedule = schedules[position]
                getStateString(schedule.state).let { if (it != 0) view.text(R.id.state).setText(it) }
                view.text(R.id.schedule_time).text = Utils.getDurationString(
                    context, schedule.startTimeMs, schedule.endTimeMs, true, true, true, 0)
                view.text(R.id.program_title).text = DvrUiHelper.getStyledTitleWithEpisodeNumber(context, schedule, 0)
                view.text(R.id.channel_name).text = channelDataManager.getChannel(schedule.channelId)?.let {
                    if (it.displayName.isNullOrEmpty()) it.displayNumber else "${it.displayName!!.trim()} ${it.displayNumber}"
                }
                return view
            }

            private fun View.text(id: Int): TextView = findViewById(id)

            private fun getStateString(state: Int): Int = when (state) {
                ScheduledRecording.STATE_RECORDING_CLIPPED -> R.string.dvr_history_dialog_state_clip
                ScheduledRecording.STATE_RECORDING_FAILED -> R.string.dvr_history_dialog_state_fail
                ScheduledRecording.STATE_RECORDING_FINISHED -> R.string.dvr_history_dialog_state_success
                else -> 0
            }
        }
        val listView = ListView(requireActivity()).apply { this.adapter = adapter }
        return AlertDialog.Builder(requireActivity()).setTitle(R.string.dvr_history_dialog_title).setView(listView).create()
    }

    companion object {
        val DIALOG_TAG: String = DvrHistoryDialogFragment::class.java.simpleName
    }
}
