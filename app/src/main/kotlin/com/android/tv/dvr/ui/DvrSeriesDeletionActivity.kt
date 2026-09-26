package com.android.tv.dvr.ui

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import androidx.leanback.app.GuidedStepSupportFragment
import com.android.tv.R
import com.android.tv.Starter
import com.android.tv.TvSingletons

/** Activity zum Löschen von Folgen einer Serie. Activity → FragmentActivity. */
class DvrSeriesDeletionActivity : FragmentActivity() {
    private var seriesRecordingId = INVALID_SERIES_RECORDING_ID
    private val idsToDelete = ArrayList<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        Starter.start(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dvr_series_settings)
        // Bugfix: ID auch nach Neuerstellung lesen (Original nur bei savedInstanceState == null)
        seriesRecordingId = intent.getLongExtra(SERIES_RECORDING_ID, INVALID_SERIES_RECORDING_ID)
        // savedInstanceState prüfen, damit die Activity nicht erneut mit Animation erscheint
        if (savedInstanceState == null) {
            val deletionFragment = DvrSeriesDeletionFragment()
            deletionFragment.arguments = intent.extras
            GuidedStepSupportFragment.addAsRoot(this, deletionFragment, R.id.dvr_settings_view_frame)
        }
    }

    @Deprecated("Deprecated in ComponentActivity")
    @Suppress("DEPRECATION")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        // Abweichung: super immer aufrufen (@CallSuper in FragmentActivity), Original nur im default-Zweig
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_DELETE ->
                // Bei Abbruch sind die Ergebnis-Arrays leer
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    deleteSelectedIds(true)
                } else {
                    // Falls je eingebettete und separate DVR-Eingänge unterstützt werden, trotzdem löschen
                    Log.i(TAG, "Write permission denied, Not trying to delete the files for series $seriesRecordingId")
                    deleteSelectedIds(false)
                }
        }
    }

    private fun deleteSelectedIds(deleteFiles: Boolean) {
        val singletons = TvSingletons.getSingletons(this)
        val recordingSize = singletons.getDvrDataManager().getRecordedPrograms(seriesRecordingId).size
        if (idsToDelete.isNotEmpty()) {
            singletons.getDvrManager()?.removeRecordedPrograms(idsToDelete, deleteFiles)
        }
        Toast.makeText(
            this,
            resources.getQuantityString(R.plurals.dvr_msg_episodes_deleted, idsToDelete.size, idsToDelete.size, recordingSize),
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    internal fun setIdsToDelete(ids: List<Long>) {
        idsToDelete.clear()
        idsToDelete.addAll(ids)
    }

    companion object {
        private const val TAG = "DvrSeriesDeletionActivity"

        /** ID der Serienaufnahme im Intent. */
        const val SERIES_RECORDING_ID = "series_recording_id"

        const val REQUEST_DELETE = 1
        const val INVALID_SERIES_RECORDING_ID = -1L
    }
}
