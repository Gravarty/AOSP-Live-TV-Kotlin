package com.android.tv.dialog

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.DialogFragment

/** Zeigt eine Webseite im Dialog (z. B. Lizenzen). */
class WebDialogFragment : SafeDismissDialogFragment() {
    private var webView: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val title = requireArguments().getString(TITLE)
        setStyle(if (title.isNullOrEmpty()) DialogFragment.STYLE_NO_TITLE else DialogFragment.STYLE_NORMAL, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        dialog?.setTitle(requireArguments().getString(TITLE))
        return WebView(requireActivity()).also {
            it.webViewClient = WebViewClient()
            it.loadUrl(requireArguments().getString(URL).orEmpty())
            webView = it
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        webView?.destroy()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
    }

    companion object {
        private const val URL = "URL"
        private const val TITLE = "TITLE"
        private const val TRACKER_LABEL = "TRACKER_LABEL"

        @JvmStatic
        fun newInstance(url: String, title: String?, trackerLabel: String?) = WebDialogFragment().apply {
            arguments = Bundle().apply {
                putString(URL, url)
                putString(TITLE, title)
                putString(TRACKER_LABEL, trackerLabel)
            }
        }
    }
}
