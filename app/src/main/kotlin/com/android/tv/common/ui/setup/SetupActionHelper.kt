package com.android.tv.common.ui.setup

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.fragment.app.Fragment

/** Leitet Aktionen eines Fragments an die Activity (OnActionClickListener) weiter. */
object SetupActionHelper {
    private const val TAG = "SetupActionHelper"

    @JvmStatic
    @JvmOverloads
    fun onActionClick(fragment: Fragment, category: String, actionId: Int, params: Bundle? = null): Boolean {
        val activity = fragment.activity
        if (activity is OnActionClickListener) return activity.onActionClick(category, actionId, params)
        Log.e(TAG, "Activity can't handle the action: {category=$category, actionId=$actionId, params=$params}")
        return false
    }

    @JvmStatic
    fun createOnClickListenerForAction(fragment: Fragment, category: String, actionId: Int, params: Bundle?): View.OnClickListener =
        View.OnClickListener { onActionClick(fragment, category, actionId, params) }
}
