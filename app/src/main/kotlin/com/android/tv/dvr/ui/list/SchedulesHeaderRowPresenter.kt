package com.android.tv.dvr.ui.list

import android.content.Context
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.TextView
import androidx.leanback.widget.RowPresenter
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.ui.DvrUiHelper
import com.android.tv.dvr.ui.list.SchedulesHeaderRow.SeriesRecordingHeaderRow
import kotlin.math.roundToInt

/** Basis-RowPresenter für [SchedulesHeaderRow]. */
abstract class SchedulesHeaderRowPresenter(
    /** Kontext. */
    val context: Context,
) : RowPresenter() {

    init {
        setHeaderPresenter(null)
        setSelectEffectEnabled(false)
    }

    /** ViewHolder für [SchedulesHeaderRow]. */
    open class SchedulesHeaderRowViewHolder(context: Context, parent: ViewGroup) :
        RowPresenter.ViewHolder(LayoutInflater.from(context).inflate(R.layout.dvr_schedules_header, parent, false)) {
        internal val titleView: TextView = view.findViewById(R.id.header_title)
        internal val descriptionView: TextView = view.findViewById(R.id.header_description)
    }

    override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
        super.onBindRowViewHolder(viewHolder, item)
        val headerViewHolder = viewHolder as SchedulesHeaderRowViewHolder
        val header = item as SchedulesHeaderRow
        headerViewHolder.titleView.text = header.title
        headerViewHolder.descriptionView.text = header.description
    }

    /** Presenter für [SchedulesHeaderRow.DateHeaderRow]. */
    class DateHeaderRowPresenter(context: Context) : SchedulesHeaderRowPresenter(context) {
        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder = DateHeaderRowViewHolder(context, parent)

        /** ViewHolder für [SchedulesHeaderRow.DateHeaderRow]. */
        class DateHeaderRowViewHolder(context: Context, parent: ViewGroup) : SchedulesHeaderRowViewHolder(context, parent)
    }

    /** Presenter für [SeriesRecordingHeaderRow] mit Buttons „Einstellungen“ und „Stoppen/Fortsetzen“. */
    class SeriesRecordingHeaderRowPresenter(context: Context) : SchedulesHeaderRowPresenter(context) {
        private val settingsDrawable: Drawable? = context.getDrawable(R.drawable.ic_settings)
        private val cancelDrawable: Drawable? = context.getDrawable(R.drawable.ic_dvr_cancel_large)
        private val resumeDrawable: Drawable? = context.getDrawable(R.drawable.ic_record_start)
        private val settingsInfo = context.getString(R.string.dvr_series_schedules_settings)
        private val cancelAllInfo = context.getString(R.string.dvr_series_schedules_stop)
        private val resumeInfo = context.getString(R.string.dvr_series_schedules_start)

        override fun createRowViewHolder(parent: ViewGroup): RowPresenter.ViewHolder = SeriesHeaderRowViewHolder(context, parent)

        override fun onBindRowViewHolder(viewHolder: RowPresenter.ViewHolder, item: Any) {
            super.onBindRowViewHolder(viewHolder, item)
            val headerViewHolder = viewHolder as SeriesHeaderRowViewHolder
            val header = item as SeriesRecordingHeaderRow
            headerViewHolder.seriesSettingsButton.visibility =
                if (header.seriesRecording.isStopped) View.INVISIBLE else View.VISIBLE
            headerViewHolder.seriesSettingsButton.text = settingsInfo
            setTextDrawable(headerViewHolder.seriesSettingsButton, settingsDrawable)
            if (header.seriesRecording.isStopped) {
                headerViewHolder.toggleStartStopButton.text = resumeInfo
                setTextDrawable(headerViewHolder.toggleStartStopButton, resumeDrawable)
            } else {
                headerViewHolder.toggleStartStopButton.text = cancelAllInfo
                setTextDrawable(headerViewHolder.toggleStartStopButton, cancelDrawable)
            }
            headerViewHolder.seriesSettingsButton.setOnClickListener {
                DvrUiHelper.startSeriesSettingsActivity(context, header.seriesRecording.id, header.programs,
                    false, false, false, null)
            }
            headerViewHolder.toggleStartStopButton.setOnClickListener { view ->
                if (header.seriesRecording.isStopped) {
                    val singletons = TvSingletons.getSingletons(context)
                    // Priorität auf die höchste zurücksetzen.
                    // Abweichung: DvrScheduleManager/DvrManager sind nullable (ohne DVR null).
                    val scheduleManager = singletons.getDvrScheduleManager() ?: return@setOnClickListener
                    val seriesRecording = SeriesRecording.buildFrom(header.seriesRecording)
                        .setPriority(scheduleManager.suggestNewSeriesPriority())
                        .build()
                    singletons.getDvrManager()?.updateSeriesRecording(seriesRecording)
                    DvrUiHelper.startSeriesSettingsActivity(context, header.seriesRecording.id, header.programs,
                        false, false, false, null)
                } else {
                    DvrUiHelper.showCancelAllSeriesRecordingDialog(view.context as DvrSchedulesActivity, header.seriesRecording)
                }
            }
        }

        private fun setTextDrawable(textView: TextView, drawableStart: Drawable?) {
            textView.setCompoundDrawablesRelativeWithIntrinsicBounds(drawableStart, null, null, null)
        }

        /** ViewHolder für [SeriesRecordingHeaderRow]; animierter Fokus-Selektor unter den Buttons. */
        class SeriesHeaderRowViewHolder(context: Context, parent: ViewGroup) : SchedulesHeaderRowViewHolder(context, parent) {
            internal val seriesSettingsButton: TextView
            internal val toggleStartStopButton: TextView
            private val ltr = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_LTR
            private val selector: View
            private var lastFocusedView: View? = null

            init {
                view.findViewById<View>(R.id.button_container).visibility = View.VISIBLE
                seriesSettingsButton = view.findViewById(R.id.series_settings)
                toggleStartStopButton = view.findViewById(R.id.series_toggle_start_stop)
                selector = view.findViewById(R.id.selector)
                val onFocusChangeListener = View.OnFocusChangeListener { v, _ -> v.post { updateSelector(v) } }
                seriesSettingsButton.onFocusChangeListener = onFocusChangeListener
                toggleStartStopButton.onFocusChangeListener = onFocusChangeListener
            }

            private fun updateSelector(focusedView: View) {
                val animationDuration = selector.context.resources.getInteger(android.R.integer.config_shortAnimTime)
                val interpolator = DecelerateInterpolator()
                if (focusedView.hasFocus()) {
                    val lp = selector.layoutParams
                    val targetWidth = focusedView.width
                    val targetTranslationX = if (ltr) {
                        (focusedView.left - selector.left).toFloat()
                    } else {
                        (focusedView.right - selector.right).toFloat()
                    }
                    // Ist der Selektor unsichtbar, Breite und Position direkt setzen (ohne Animation).
                    if (selector.alpha == 0f) {
                        selector.translationX = targetTranslationX
                        lp.width = targetWidth
                        selector.requestLayout()
                    }
                    // Selektor einblenden und auf Zielbreite/-position animieren.
                    val deltaWidth = (lp.width - targetWidth).toFloat()
                    selector.animate().cancel()
                    selector.animate()
                        .translationX(targetTranslationX)
                        .alpha(1f)
                        .setUpdateListener { animation ->
                            // Breite für diesen Animationsschritt.
                            lp.width = targetWidth + (deltaWidth * (1f - animation.animatedFraction)).roundToInt()
                            selector.requestLayout()
                        }
                        .setDuration(animationDuration.toLong())
                        .setInterpolator(interpolator)
                        .start()
                    lastFocusedView = focusedView
                } else if (lastFocusedView === focusedView) {
                    selector.animate().setUpdateListener(null).cancel()
                    selector.animate()
                        .alpha(0f)
                        .setDuration(animationDuration.toLong())
                        .setInterpolator(interpolator)
                        .start()
                    lastFocusedView = null
                }
            }
        }
    }
}
