package com.android.tv.dvr.ui

import android.os.Bundle
import androidx.leanback.widget.GuidanceStylist
import androidx.leanback.widget.GuidedAction
import com.android.tv.R

/** Erklärt, warum android.permission.WRITE_EXTERNAL_STORAGE angefordert wird. */
class DvrWriteStoragePermissionRationaleFragment : DvrGuidedStepFragment() {

    override fun onCreateGuidance(savedInstanceState: Bundle?): GuidanceStylist.Guidance {
        val res = requireContext().resources
        return GuidanceStylist.Guidance(res.getString(R.string.write_storage_permission_rationale_title),
            res.getString(R.string.write_storage_permission_rationale_description), null, null)
    }

    override fun onCreateActions(actions: MutableList<GuidedAction>, savedInstanceState: Bundle?) {
        actions.add(GuidedAction.Builder(requireActivity()).id(GuidedAction.ACTION_ID_OK).title(android.R.string.ok).build())
    }

    override fun onTrackedGuidedActionClicked(action: GuidedAction) = dismissDialog()
}
