package com.android.tv.dialog

import android.content.Context
import androidx.fragment.app.DialogFragment
import com.android.tv.MainActivity

/** Dialog, der dismiss() auch vor dem Anhängen erlaubt und MainActivity beim Schließen informiert. */
abstract class SafeDismissDialogFragment : DialogFragment() {
    private var activity: MainActivity? = null
    private var attached = false
    private var dismissPending = false

    override fun onAttach(context: Context) {
        super.onAttach(context)
        attached = true
        if (context is MainActivity) activity = context
        if (dismissPending) {
            dismissPending = false
            dismiss()
        }
    }

    override fun onDestroy() {
        activity?.overlayManager?.onDialogDestroyed()
        super.onDestroy()
    }

    override fun onDetach() {
        super.onDetach()
        attached = false
    }

    override fun dismiss() {
        if (!attached) {
            // Wird beim Anhängen nachgeholt
            dismissPending = true
        } else {
            super.dismiss()
        }
    }
}
