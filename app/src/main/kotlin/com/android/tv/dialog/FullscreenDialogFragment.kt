package com.android.tv.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import com.android.tv.MainActivity
import com.android.tv.R

/** Vollbild-Dialog mit einer DialogView als Inhalt (z. B. Intro). */
class FullscreenDialogFragment : SafeDismissDialogFragment() {

    interface DialogView {
        fun initialize(activity: MainActivity, dialog: Dialog)
        fun onBackPressed()
        fun onDestroy()
    }

    private var dialogView: DialogView? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = FullscreenDialog(requireActivity(), R.style.Theme_TV_dialog_Fullscreen)
        val viewLayoutResId = requireArguments().getInt(VIEW_LAYOUT_ID)
        val v = LayoutInflater.from(requireActivity()).inflate(viewLayoutResId, null)
        dialog.setContentView(v)
        (v as DialogView).initialize(requireActivity() as MainActivity, dialog)
        return dialog
    }

    override fun onDestroy() {
        super.onDestroy()
        dialogView?.onDestroy()
    }

    private inner class FullscreenDialog(context: Context, theme: Int) : Dialog(context, theme) {
        override fun setContentView(view: View) {
            super.setContentView(view)
            dialogView = view as DialogView
        }

        override fun dispatchKeyEvent(event: KeyEvent): Boolean =
            super.dispatchKeyEvent(event) || (dialogView as View).dispatchKeyEvent(event)

        @Deprecated("Wie im Original")
        override fun onBackPressed() {
            dialogView?.onBackPressed()
        }
    }

    companion object {
        val DIALOG_TAG: String = FullscreenDialogFragment::class.java.simpleName
        const val VIEW_LAYOUT_ID = "viewLayoutId"
        const val TRACKER_LABEL = "trackerLabel"

        @JvmStatic
        fun newInstance(viewLayoutResId: Int, trackerLabel: String) = FullscreenDialogFragment().apply {
            arguments = Bundle().apply {
                putInt(VIEW_LAYOUT_ID, viewLayoutResId)
                putString(TRACKER_LABEL, trackerLabel)
            }
        }
    }
}
