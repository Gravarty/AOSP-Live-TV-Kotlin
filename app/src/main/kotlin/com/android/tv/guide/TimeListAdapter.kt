package com.android.tv.guide

import android.content.res.Resources
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.R
import com.android.tv.util.Utils
import java.util.Date
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** Zeitleiste in 30-Minuten-Schritten ("h:mm a" bzw. mit Datum an anderen Tagen). */
internal class TimeListAdapter(res: Resources) : RecyclerView.Adapter<TimeListAdapter.TimeViewHolder>() {
    private var startUtcMs = 0L
    private val timePatternSameDay: String
    private val timePatternDifferentDay: String

    init {
        if (rowHeaderOverlapping == 0) {
            rowHeaderOverlapping = abs(res.getDimensionPixelOffset(R.dimen.program_guide_table_header_row_overlap))
        }
        val locale = res.configuration.locales[0]
        timePatternSameDay = DateFormat.getBestDateTimePattern(locale, TIME_PATTERN_SAME_DAY)
        timePatternDifferentDay = DateFormat.getBestDateTimePattern(locale, TIME_PATTERN_DIFFERENT_DAY)
    }

    fun update(startTimeMs: Long) {
        startUtcMs = startTimeMs
        notifyDataSetChanged()
    }

    override fun getItemCount() = Int.MAX_VALUE
    override fun getItemViewType(position: Int) = R.layout.program_guide_table_header_row_item

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val startTime = startUtcMs + position * TIME_UNIT_MS
        val endTime = startTime + TIME_UNIT_MS
        val itemView = holder.itemView
        val pattern = if (Utils.isInGivenDay(System.currentTimeMillis(), startTime)) timePatternSameDay else timePatternDifferentDay
        itemView.findViewById<TextView>(R.id.time).text = DateFormat.format(pattern, Date(startTime)).toString()
        val lp = itemView.layoutParams as RecyclerView.LayoutParams
        lp.width = GuideUtils.convertMillisToPixel(startTime, endTime)
        // Erste Zeitmarke mittig über dem Kanal-Kopf
        lp.marginStart = if (position == 0) rowHeaderOverlapping - lp.width / 2 else 0
        itemView.layoutParams = lp
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        TimeViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))

    class TimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    companion object {
        private val TIME_UNIT_MS = TimeUnit.MINUTES.toMillis(30)
        private const val TIME_PATTERN_SAME_DAY = "h:mm a"
        private const val TIME_PATTERN_DIFFERENT_DAY = "MMM d, h:mm a"
        private var rowHeaderOverlapping = 0
    }
}
