package com.android.tv.dvr.ui.browse

import android.content.Intent
import android.media.tv.TvInputManager
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import com.android.tv.R
import com.android.tv.Starter

/**
 * Activity der DVR-Bibliothek. Activity → FragmentActivity (hostet [DvrBrowseFragment]).
 * StartupMeasure (Analytics) entfällt.
 */
class DvrBrowseActivity : FragmentActivity() {
    private var fragment: DvrBrowseFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dvr_main)
        fragment = supportFragmentManager.findFragmentById(R.id.dvr_frame) as? DvrBrowseFragment
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        // AndroidX verlangt super-Aufruf (@CallSuper); im Original fehlte er.
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (TvInputManager.ACTION_VIEW_RECORDING_SCHEDULES == intent.action) {
            fragment?.showScheduledRow()
        }
    }
}
