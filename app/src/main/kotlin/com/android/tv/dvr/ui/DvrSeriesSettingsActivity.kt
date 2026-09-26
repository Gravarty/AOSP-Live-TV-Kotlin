package com.android.tv.dvr.ui

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.common.SoftPreconditions

/** Activity für die Einstellungen einer Serienaufnahme. Activity → FragmentActivity. */
class DvrSeriesSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_series_settings)
        val seriesRecordingId = intent.getLongExtra(SERIES_RECORDING_ID, -1)
        SoftPreconditions.checkArgument(seriesRecordingId != -1L, null, null)

        if (savedInstanceState == null) {
            val settingFragment = DvrSeriesSettingsFragment()
            settingFragment.arguments = intent.extras
            GuidedStepSupportFragment.addAsRoot(this, settingFragment, R.id.dvr_settings_view_frame)
        }
    }

    override fun onAttachedToWindow() {
        // Abweichung: ohne Extras gilt der Standardwert true (Original: NPE)
        if (intent.extras?.getBoolean(IS_WINDOW_TRANSLUCENT, true) == false) {
            window.setBackgroundDrawable(ColorDrawable(getColor(R.color.common_tv_background)))
        }
    }

    companion object {
        /** ID der Serienaufnahme. Typ: Long */
        const val SERIES_RECORDING_ID = "series_recording_id"

        /** Ob eine Serienaufnahme ohne Planungen und Aufnahmen entfernt wird. Typ: Boolean */
        const val REMOVE_EMPTY_SERIES_RECORDING = "remove_empty_series_recording"

        /** Ob das Einstellungs-Fragment durchscheinend sein soll. Typ: Boolean */
        const val IS_WINDOW_TRANSLUCENT = "windows_translucent"

        /** Name der Sendungsliste der Serie (über [BigArguments]). Typ: List<Program> */
        const val PROGRAM_LIST = "program_list"

        /** Ob der Bestätigungsdialog "Planungen ansehen" anbieten soll. Typ: Boolean */
        const val SHOW_VIEW_SCHEDULE_OPTION_IN_DIALOG = "show_view_schedule_option_in_dialog"

        /**
         * Die zur Serie hinzugefügte laufende Sendung. Sie wird nur aufgenommen, wenn die Serie
         * über den Media-Controller angelegt wurde.
         */
        const val CURRENT_PROGRAM = "current_program"
    }
}
