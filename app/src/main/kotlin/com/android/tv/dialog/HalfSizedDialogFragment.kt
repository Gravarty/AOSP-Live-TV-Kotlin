package com.android.tv.dialog

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.android.tv.R
import java.util.concurrent.TimeUnit

/** Halbhoher Dialog, der sich nach 30 s ohne Tastendruck selbst schließt. */
open class HalfSizedDialogFragment : SafeDismissDialogFragment() {

    fun interface OnActionClickListener {
        fun onActionClick(actionId: Long)
    }

    private var onActionClickListener: OnActionClickListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoDismisser = Runnable { dismiss() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.halfsized_dialog, container, false)

    override fun onStart() {
        super.onStart()
        handler.postDelayed(autoDismisser, AUTO_DISMISS_TIME_THRESHOLD_MS)
    }

    override fun onPause() {
        super.onPause()
        // Mit Listener: beim Pausieren schließen, sonst geht der Callback verloren
        if (onActionClickListener != null) dismiss()
    }

    override fun onStop() {
        super.onStop()
        handler.removeCallbacks(autoDismisser)
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        super.onCreateDialog(savedInstanceState).apply {
            setOnKeyListener { _, _, _ ->
                // Jede Taste verlängert die Anzeigedauer
                handler.removeCallbacks(autoDismisser)
                handler.postDelayed(autoDismisser, AUTO_DISMISS_TIME_THRESHOLD_MS)
                false
            }
        }

    override fun getTheme(): Int = R.style.Theme_TV_dialog_HalfSizedDialog

    fun setOnActionClickListener(listener: OnActionClickListener?) { onActionClickListener = listener }

    protected fun getOnActionClickListener(): OnActionClickListener? = onActionClickListener

    companion object {
        val DIALOG_TAG: String = HalfSizedDialogFragment::class.java.simpleName
        private val AUTO_DISMISS_TIME_THRESHOLD_MS = TimeUnit.SECONDS.toMillis(30)
    }
}
