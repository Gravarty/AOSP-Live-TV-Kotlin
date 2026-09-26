package com.android.tv.dvr.ui

import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.android.tv.R
import com.android.tv.Starter

/** Activity für die Aufnahme-Einstellungen (früher starten/später beenden) einer Sendung. Activity → FragmentActivity. */
class DvrRecordingSettingsActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_series_settings)

        if (savedInstanceState == null) {
            val settingFragment = DvrRecordingSettingsFragment()
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
        /** Ob das Einstellungs-Fragment durchscheinend sein soll. Typ: Boolean */
        const val IS_WINDOW_TRANSLUCENT = "windows_translucent"

        /** Die zur Aufnahme hinzugefügte Sendung. */
        const val PROGRAM = "program"
    }
}
