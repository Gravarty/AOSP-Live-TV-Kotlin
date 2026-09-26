package com.android.tv.dvr.ui.list

import android.app.ProgressDialog
import android.os.Bundle
import androidx.annotation.IntDef
import androidx.core.os.BundleCompat
import androidx.fragment.app.FragmentActivity
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.data.api.Program
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.EpisodicProgramLoadTask
import com.android.tv.dvr.recorder.SeriesRecordingScheduler
import com.android.tv.dvr.ui.BigArguments

/** Activity mit der Liste geplanter Aufnahmen (alle oder die einer Serie). Activity → FragmentActivity. */
class DvrSchedulesActivity : FragmentActivity() {

    @Retention(AnnotationRetention.SOURCE)
    @IntDef(TYPE_FULL_SCHEDULE, TYPE_SERIES_SCHEDULE)
    annotation class ScheduleListType

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        // null übergeben, damit Fragments nicht automatisch neu erzeugt werden.
        super.onCreate(null)
        setContentView(R.layout.activity_dvr_schedules)
        when (intent.getIntExtra(KEY_SCHEDULES_TYPE, TYPE_FULL_SCHEDULE)) {
            TYPE_FULL_SCHEDULE -> {
                val schedulesFragment = DvrSchedulesFragment()
                schedulesFragment.arguments = intent.extras
                supportFragmentManager.beginTransaction().add(R.id.fragment_container, schedulesFragment).commit()
            }
            TYPE_SERIES_SCHEDULE -> {
                if (BigArguments.getArgument(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_PROGRAMS) != null) {
                    // Die Sendungen gehen an DvrSeriesSchedulesFragment, BigArguments also nicht zurücksetzen.
                    showDvrSeriesSchedulesFragment(intent.extras)
                } else {
                    val seriesRecording = intent.extras?.let {
                        BundleCompat.getParcelable(it, DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_RECORDING,
                            SeriesRecording::class.java)
                    }
                    // Bugfix: ohne Serienaufnahme beenden statt später abzustürzen.
                    if (seriesRecording == null) {
                        finish()
                        return
                    }
                    val dialog = ProgressDialog.show(this, null, getString(R.string.dvr_series_progress_message_reading_programs))
                    // Für schnelleres Laden die Aktualisierung der Serien-Aufnahmen anhalten.
                    SeriesRecordingScheduler.getInstance(this).pauseUpdate()
                    object : EpisodicProgramLoadTask(this@DvrSchedulesActivity, listOf(seriesRecording)) {
                        override fun onPostExecute(programs: List<Program>) {
                            SeriesRecordingScheduler.getInstance(this@DvrSchedulesActivity).resumeUpdate()
                            dialog.dismiss()
                            val args = intent.extras
                            BigArguments.reset()
                            BigArguments.setArgument(DvrSeriesSchedulesFragment.SERIES_SCHEDULES_KEY_SERIES_PROGRAMS, programs)
                            showDvrSeriesSchedulesFragment(args)
                        }
                    }.setLoadCurrentProgram(true)
                        .setLoadDisallowedProgram(true)
                        .setLoadScheduledEpisode(true)
                        .setIgnoreChannelOption(true)
                        .execute()
                }
            }
            else -> finish()
        }
    }

    private fun showDvrSeriesSchedulesFragment(args: Bundle?) {
        val schedulesFragment = DvrSeriesSchedulesFragment()
        schedulesFragment.arguments = args
        supportFragmentManager.beginTransaction().add(R.id.fragment_container, schedulesFragment).commit()
    }

    companion object {
        /** Schlüssel für die Art der Liste ([ScheduleListType]). */
        const val KEY_SCHEDULES_TYPE = "schedules_type"
        /** Alle geplanten Aufnahmen anzeigen. */
        const val TYPE_FULL_SCHEDULE = 0
        /** Geplante Aufnahmen einer Serienaufnahme anzeigen. */
        const val TYPE_SERIES_SCHEDULE = 1
    }
}
