package com.android.tv.dvr.ui

import android.content.Context
import android.graphics.Typeface
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import androidx.leanback.widget.GuidedActionsStylist
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.data.SeriesRecording

/** Reihenfolge (Priorität) der Serienaufnahmen per Verschieben festlegen. */
class DvrPrioritySettingsFragment : TrackedGuidedStepFragment() {
    private val seriesRecordings = ArrayList<SeriesRecording>()

    private var selectedRecording: SeriesRecording? = null
    private var comeFromSeriesRecording: SeriesRecording? = null
    private var selectedActionElevation = 0f
    private var actionColor = 0
    private var selectedActionColor = 0

    override fun onAttach(context: Context) {
        super.onAttach(context)
        seriesRecordings.clear()
        seriesRecordings.add(
            SeriesRecording.Builder()
                .setTitle(getString(R.string.dvr_priority_action_one_time_recording))
                .setPriority(Long.MAX_VALUE)
                .setId(ONE_TIME_RECORDING_ID.toLong())
                .build(),
        )
        val dvrDataManager = TvSingletons.getSingletons(context).getDvrDataManager()
        val comeFromSeriesRecordingId = arguments?.getLong(COME_FROM_SERIES_RECORDING_ID, -1) ?: -1
        for (series in dvrDataManager.getSeriesRecordings()) {
            if (series.state == SeriesRecording.STATE_SERIES_NORMAL || series.id == comeFromSeriesRecordingId) {
                seriesRecordings.add(series)
            }
        }
        seriesRecordings.sortWith(SeriesRecording.PRIORITY_COMPARATOR)
        comeFromSeriesRecording = dvrDataManager.getSeriesRecording(comeFromSeriesRecordingId)
        selectedActionElevation = resources.getDimension(R.dimen.card_elevation_normal)
        actionColor = resources.getColor(R.color.dvr_guided_step_action_text_color, null)
        selectedActionColor = resources.getColor(R.color.dvr_guided_step_action_text_color_selected, null)
    }

    override fun onResume() {
        super.onResume()
        selectedActionPosition = comeFromSeriesRecording?.let { seriesRecordings.indexOf(it) } ?: 1
    }

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val breadcrumb = comeFromSeriesRecording?.title
        return Guidance(getString(R.string.dvr_priority_title), getString(R.string.dvr_priority_description), breadcrumb, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        var position = 0L
        for (seriesRecording in seriesRecordings) {
            actions.add(GuidedAction.Builder(activity).id(position++).title(seriesRecording.title).build())
        }
    }

    override fun onCreateButtonActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(
            GuidedAction.Builder(activity)
                .id(ACTION_ID_SAVE)
                .title(getString(R.string.dvr_priority_button_action_save))
                .build(),
        )
        actions.add(GuidedAction.Builder(activity).clickAction(GuidedAction.ACTION_ID_CANCEL).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        val actionId = action.id
        if (actionId == ACTION_ID_SAVE) {
            val dvrManager = TvSingletons.getSingletons(requireContext()).getDvrManager()
            val size = seriesRecordings.size
            for (i in 1 until size) {
                val priority = DvrScheduleManager.suggestSeriesPriority(size - i)
                val seriesRecording = seriesRecordings[i]
                if (seriesRecording.priority != priority) {
                    dvrManager?.updateSeriesRecording(SeriesRecording.buildFrom(seriesRecording).setPriority(priority).build())
                }
            }
            parentFragmentManager.popBackStack()
        } else if (actionId == GuidedAction.ACTION_ID_CANCEL) {
            parentFragmentManager.popBackStack()
        } else if (selectedRecording == null) {
            selectedRecording = seriesRecordings[actionId.toInt()]
            for (i in seriesRecordings.indices) {
                updateItem(i)
            }
        } else {
            selectedRecording = null
            for (i in seriesRecordings.indices) {
                updateItem(i)
            }
        }
    }

    override fun getTrackerPrefix(): String = "DvrPrioritySettingsFragment"

    override fun getTrackerLabelForGuidedAction(action: GuidedAction): String =
        if (action.id == ACTION_ID_SAVE) "save" else super.getTrackerLabelForGuidedAction(action)

    override fun onGuidedActionFocused(action: GuidedAction) {
        super.onGuidedActionFocused(action)
        val selected = selectedRecording ?: return
        if (action.id < 0) {
            selectedRecording = null
            for (i in seriesRecordings.indices) {
                updateItem(i)
            }
            return
        }
        val position = action.id.toInt()
        val previousPosition = seriesRecordings.indexOf(selected)
        seriesRecordings.remove(selected)
        seriesRecordings.add(position, selected)
        updateItem(previousPosition)
        updateItem(position)
        notifyActionChanged(previousPosition)
        notifyActionChanged(position)
    }

    override fun onCreateButtonActionsStylist(): GuidedActionsStylist = DvrGuidedActionsStylist(true)

    override fun onCreateActionsStylist(): GuidedActionsStylist = object : DvrGuidedActionsStylist(false) {
        override fun onBindViewHolder(vh: GuidedActionsStylist.ViewHolder, action: GuidedAction) {
            super.onBindViewHolder(vh, action)
            updateItem(vh.itemView, action.id.toInt())
        }

        override fun onProvideItemLayoutId(): Int = R.layout.priority_settings_action_item
    }

    private fun updateItem(position: Int) {
        val itemView = getActionItemView(position) ?: return
        updateItem(itemView, position)
    }

    private fun updateItem(itemView: View, position: Int) {
        val action = actions[position]
        action.title = seriesRecordings[position].title
        val current = selectedRecording
        val selected = current != null && seriesRecordings.indexOf(current) == position
        val titleView = itemView.findViewById<TextView>(R.id.guidedactions_item_title)
        val imageView = itemView.findViewById<ImageView>(R.id.guidedactions_item_tail_image)
        if (position == 0) {
            // Einzelaufnahme
            itemView.setBackgroundResource(R.drawable.setup_selector_background)
            imageView.visibility = View.GONE
            itemView.isFocusable = false
            itemView.elevation = 0f
            // <i>-Tag in strings.xml funktioniert nicht
            titleView.setTypeface(titleView.typeface, Typeface.ITALIC)
        } else if (current == null) {
            titleView.setTextColor(actionColor)
            itemView.setBackgroundResource(R.drawable.setup_selector_background)
            imageView.setImageResource(R.drawable.ic_draggable_white)
            imageView.visibility = View.VISIBLE
            itemView.isFocusable = true
            itemView.elevation = 0f
            titleView.setTypeface(titleView.typeface, Typeface.NORMAL)
        } else if (selected) {
            titleView.setTextColor(selectedActionColor)
            itemView.setBackgroundResource(R.drawable.priority_settings_action_item_selected)
            imageView.setImageResource(R.drawable.ic_dragging_grey)
            imageView.visibility = View.VISIBLE
            itemView.isFocusable = true
            itemView.elevation = selectedActionElevation
            titleView.setTypeface(titleView.typeface, Typeface.NORMAL)
        } else {
            titleView.setTextColor(actionColor)
            itemView.setBackgroundResource(R.drawable.setup_selector_background)
            imageView.visibility = View.INVISIBLE
            itemView.isFocusable = true
            itemView.elevation = 0f
            titleView.setTypeface(titleView.typeface, Typeface.NORMAL)
        }
    }

    companion object {
        /** ID der Serienaufnahme, von der aus das Fragment gestartet wurde. Typ: Long */
        const val COME_FROM_SERIES_RECORDING_ID = "series_recording_id"

        private const val ONE_TIME_RECORDING_ID = 0
        // IDs der Button-Aktionen sind negativ
        private const val ACTION_ID_SAVE = -100L
    }
}
