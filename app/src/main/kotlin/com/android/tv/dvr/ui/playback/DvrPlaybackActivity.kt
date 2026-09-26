package com.android.tv.dvr.ui.playback

import android.content.ContentUris
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.FragmentActivity
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.dialog.PinDialogFragment.OnPinCheckedListener
import com.android.tv.dvr.data.RecordedProgram
import com.android.tv.util.Utils

/**
 * Spielt eine [RecordedProgram] ab.
 * FragmentActivity statt DaggerActivity (kein Injection-Bedarf); das Dagger-Modul entfällt.
 * onVisibleBehindCanceled entfällt (seit Android 8 wirkungslos, wie in MainActivity).
 */
class DvrPlaybackActivity : FragmentActivity(), OnPinCheckedListener {

    private lateinit var overlayFragment: DvrPlaybackOverlayFragment
    private var onPinCheckedListener: OnPinCheckedListener? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        if (DEBUG) Log.d(TAG, "onCreate")
        super.onCreate(savedInstanceState)
        intent = createProgramIntent(intent)
        setContentView(R.layout.activity_dvr_playback)
        overlayFragment = supportFragmentManager.findFragmentById(R.id.dvr_playback_controls_fragment) as DvrPlaybackOverlayFragment
    }

    override fun onNewIntent(intent: Intent) {
        // super-Aufruf ergänzt (AndroidX verlangt ihn, Verhalten unverändert)
        super.onNewIntent(intent)
        setIntent(createProgramIntent(intent))
        overlayFragment.onNewIntent(createProgramIntent(intent))
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val density = resources.displayMetrics.density
        overlayFragment.onWindowSizeChanged(
            (newConfig.screenWidthDp * density).toInt(),
            (newConfig.screenHeightDp * density).toInt(),
        )
    }

    private fun createProgramIntent(intent: Intent): Intent {
        if (Intent.ACTION_VIEW == intent.action) {
            val uri = intent.data
            if (uri != null) {
                val recordedProgramId = ContentUris.parseId(uri)
                intent.putExtra(Utils.EXTRA_KEY_RECORDED_PROGRAM_ID, recordedProgramId)
            }
        }
        return intent
    }

    override fun onPinChecked(checked: Boolean, type: Int, rating: String?) {
        onPinCheckedListener?.onPinChecked(checked, type, rating)
    }

    internal fun setOnPinCheckListener(listener: OnPinCheckedListener?) {
        onPinCheckedListener = listener
    }

    companion object {
        private const val TAG = "DvrPlaybackActivity"
        private const val DEBUG = false
    }
}
