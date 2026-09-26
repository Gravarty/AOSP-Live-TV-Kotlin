package com.android.tv.dialog

import android.app.Dialog
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.android.tv.R
import com.android.tv.common.SoftPreconditions

/** Rückfrage, ob eine interaktive TV-App (HbbTV usw.) gestartet werden darf. */
class InteractiveAppDialogFragment : SafeDismissDialogFragment() {

    fun interface OnInteractiveAppCheckedListener {
        fun onInteractiveAppChecked(checked: Boolean)
    }

    private var isChoseOk = false
    private var iAppName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        iAppName = requireArguments().getString(TV_IAPP_NAME)
        setStyle(DialogFragment.STYLE_NO_TITLE, 0)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            window?.attributes?.windowAnimations = R.style.pin_dialog_animation
            isChoseOk = false
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(resources.getDimensionPixelSize(R.dimen.pin_dialog_width), WindowManager.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val v = inflater.inflate(R.layout.tv_app_dialog, container, false)
        v.findViewById<TextView>(R.id.title).text = getString(R.string.tv_app_dialog_title, iAppName)
        v.findViewById<Button>(R.id.ok).setOnClickListener { exit(true) }
        v.findViewById<Button>(R.id.cancel).setOnClickListener { exit(false) }
        return v
    }

    private fun exit(isOkClick: Boolean) {
        isChoseOk = isOkClick
        dismiss()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        val listener = activity as? OnInteractiveAppCheckedListener
        SoftPreconditions.checkState(listener != null, "InteractiveAppDialog", "activity is not a listener")
        listener?.onInteractiveAppChecked(isChoseOk)
    }

    companion object {
        val DIALOG_TAG: String = InteractiveAppDialogFragment::class.java.name
        private const val TV_IAPP_NAME = "tv_iapp_name"

        @JvmStatic
        fun create(iappName: String) = InteractiveAppDialogFragment().apply {
            arguments = Bundle().apply { putString(TV_IAPP_NAME, iappName) }
        }
    }
}
