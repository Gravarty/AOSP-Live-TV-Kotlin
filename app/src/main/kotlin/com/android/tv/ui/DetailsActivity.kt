package com.android.tv.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Parcelable
import android.transition.Transition
import android.util.Log
import android.view.View
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.DetailsSupportFragment
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.TvSingletons
import com.android.tv.dialog.PinDialogFragment
import com.android.tv.dvr.ui.browse.CurrentRecordingDetailsFragment
import com.android.tv.dvr.ui.browse.RecordedProgramDetailsFragment
import com.android.tv.dvr.ui.browse.ScheduledRecordingDetailsFragment
import com.android.tv.dvr.ui.browse.SeriesRecordingDetailsFragment
import dagger.hilt.android.AndroidEntryPoint

/**
 * Detailansicht für Sendungen und Aufnahmen (laufend, geplant, aufgenommen, Serie).
 * Leanback DetailsFragment → DetailsSupportFragment (AndroidX).
 */
@AndroidEntryPoint
class DetailsActivity : FragmentActivity(), PinDialogFragment.OnPinCheckedListener {

    private var onPinCheckedListener: PinDialogFragment.OnPinCheckedListener? = null
    private var recordId = INVALID_RECORD_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_details)
        val recordId = intent.getLongExtra(RECORDING_ID, INVALID_RECORD_ID)
        val detailsViewType = intent.getIntExtra(DETAILS_VIEW_TYPE, -1)
        val hideViewSchedule = intent.getBooleanExtra(HIDE_VIEW_SCHEDULE, false)
        val channelId = intent.getLongExtra(CHANNEL_ID, -1)
        var detailsFragment: DetailsSupportFragment? = null
        val args = Bundle()
        if (detailsViewType != -1 && savedInstanceState == null) {
            if (recordId != INVALID_RECORD_ID) {
                this.recordId = recordId
                args.putLong(RECORDING_ID, recordId)
                detailsFragment = when (detailsViewType) {
                    CURRENT_RECORDING_VIEW -> CurrentRecordingDetailsFragment()
                    SCHEDULED_RECORDING_VIEW -> {
                        args.putBoolean(HIDE_VIEW_SCHEDULE, hideViewSchedule)
                        ScheduledRecordingDetailsFragment()
                    }
                    RECORDED_PROGRAM_VIEW -> RecordedProgramDetailsFragment()
                    SERIES_RECORDING_VIEW -> SeriesRecordingDetailsFragment()
                    else -> null
                }
            } else if (detailsViewType == PROGRAM_VIEW && channelId != -1L) {
                val program = IntentCompat.getParcelableExtra(intent, PROGRAM, Parcelable::class.java)
                if (program != null) {
                    args.putLong(CHANNEL_ID, channelId)
                    args.putParcelable(PROGRAM, program)
                    args.putString(INPUT_ID, intent.getStringExtra(INPUT_ID))
                    detailsFragment = ProgramDetailsFragment()
                }
            }
            detailsFragment?.let {
                it.arguments = args
                supportFragmentManager.beginTransaction().replace(R.id.dvr_details_view_frame, it).commit()
            }
        }
        addTransitionListener()
    }

    override fun onPinChecked(checked: Boolean, type: Int, rating: String?) {
        onPinCheckedListener?.onPinChecked(checked, type, rating)
    }

    fun setOnPinCheckListener(listener: PinDialogFragment.OnPinCheckedListener?) { onPinCheckedListener = listener }

    /** Nach dem Übergang den Fokus auf die Aktionsleiste setzen. Bugfix: Übergang kann fehlen. */
    private fun addTransitionListener() {
        window.sharedElementEnterTransition?.addListener(object : Transition.TransitionListener {
            override fun onTransitionStart(transition: Transition) {}
            override fun onTransitionEnd(transition: Transition) {
                findViewById<View>(R.id.details_overview_actions)?.requestFocus()
            }
            override fun onTransitionCancel(transition: Transition) {}
            override fun onTransitionPause(transition: Transition) {}
            override fun onTransitionResume(transition: Transition) {}
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_DELETE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                delete(true)
            } else {
                Log.i(TAG, "Write permission denied, Not trying to delete the file for $recordId")
                delete(false)
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    private fun delete(deleteFile: Boolean) {
        if (recordId != INVALID_RECORD_ID) {
            TvSingletons.getSingletons(this).getDvrManager()?.removeRecordedProgram(recordId, deleteFile)
        }
        finish()
    }

    companion object {
        private const val TAG = "DetailsActivity"
        private const val INVALID_RECORD_ID = -1L
        const val RECORDING_ID = "record_id"
        const val PROGRAM = "program"
        const val CHANNEL_ID = "channel_id"
        const val INPUT_ID = "input_id"
        const val HIDE_VIEW_SCHEDULE = "hide_view_schedule"
        const val DETAILS_VIEW_TYPE = "details_view_type"
        const val SHARED_ELEMENT_NAME = "shared_element"
        const val CURRENT_RECORDING_VIEW = 1
        const val SCHEDULED_RECORDING_VIEW = 2
        const val RECORDED_PROGRAM_VIEW = 3
        const val SERIES_RECORDING_VIEW = 4
        const val PROGRAM_VIEW = 5
        const val REQUEST_DELETE = 1
    }
}
