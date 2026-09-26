package com.android.tv.dvr.ui

import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.android.tv.R

/** Dialog-Activity mit der Bestätigung nach dem Planen einer Serie. Activity → FragmentActivity. */
class DvrSeriesScheduledDialogActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.halfsized_dialog)
        if (savedInstanceState == null) {
            val dvrSeriesScheduledFragment = DvrSeriesScheduledFragment()
            dvrSeriesScheduledFragment.arguments = intent.extras
            GuidedStepSupportFragment.addAsRoot(this, dvrSeriesScheduledFragment, R.id.halfsized_dialog_host)
        }
    }

    companion object {
        /** ID der Serienaufnahme. */
        const val SERIES_RECORDING_ID = "series_recording_id"

        /** Ob der Dialog "Planungen ansehen" anbieten soll. */
        const val SHOW_VIEW_SCHEDULE_OPTION = "show_view_schedule_option"
    }
}
