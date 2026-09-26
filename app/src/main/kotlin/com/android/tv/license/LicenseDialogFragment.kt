package com.android.tv.license

import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import androidx.core.os.BundleCompat
import androidx.fragment.app.DialogFragment
import com.android.tv.R
import com.android.tv.dialog.SafeDismissDialogFragment

/** Vollbild-Dialog mit einem Lizenztext. */
class LicenseDialogFragment : SafeDismissDialogFragment() {
    private lateinit var license: License

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        license = BundleCompat.getParcelable(requireArguments(), LICENSE, License::class.java)!!
        setStyle(if (license.libraryName.isEmpty()) DialogFragment.STYLE_NO_TITLE else DialogFragment.STYLE_NORMAL, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TextView(requireActivity()).apply {
            text = Licenses.getLicenseText(requireContext(), license)
            movementMethod = ScrollingMovementMethod()
            val v = resources.getDimensionPixelSize(R.dimen.vertical_overscan_safe_margin)
            val h = resources.getDimensionPixelSize(R.dimen.horizontal_overscan_safe_margin)
            setPadding(h, v, h, v)
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        dialog?.setTitle(license.libraryName)
    }

    companion object {
        val DIALOG_TAG: String = LicenseDialogFragment::class.java.simpleName
        private const val LICENSE = "LICENSE"

        @JvmStatic
        fun newInstance(license: License) = LicenseDialogFragment().apply {
            arguments = Bundle().apply { putParcelable(LICENSE, license) }
        }
    }
}
