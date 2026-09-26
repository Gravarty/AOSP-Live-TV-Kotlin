package com.android.tv.dvr.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.leanback.widget.GuidanceStylist.Guidance
import androidx.leanback.widget.GuidedAction
import com.android.tv.R
import com.android.tv.ui.DetailsActivity

/** Fehlerdialog: Aufnahmespeicher fehlt. */
class DvrMissingStorageErrorFragment : DvrGuidedStepFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): Guidance {
        val title = resources.getString(R.string.dvr_error_missing_storage_title)
        val description = resources.getString(R.string.dvr_error_missing_storage_description)
        return Guidance(title, description, null, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        val activity = requireActivity()
        actions.add(GuidedAction.Builder(activity).id(ACTION_OK.toLong()).title(android.R.string.ok).build())
        actions.add(GuidedAction.Builder(activity).id(ACTION_OPEN_STORAGE_SETTINGS.toLong())
            .title(resources.getString(R.string.dvr_action_error_storage_settings)).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) {
        val activity = activity
        if (activity is DetailsActivity) activity.finish() else dismissDialog()
        if (action.id != ACTION_OPEN_STORAGE_SETTINGS.toLong()) return
        try {
            context?.startActivity(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "Can't start internal storage settings activity", e)
        }
    }

    private companion object {
        const val TAG = "DvrMissingStorageError"
        const val ACTION_OK = 1
        const val ACTION_OPEN_STORAGE_SETTINGS = 2
    }
}
