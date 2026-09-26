package com.android.tv.ui

import android.app.AlertDialog
import android.content.Context
import com.android.tv.common.SoftPreconditions

object DialogUtils {
    /** Listen-Dialog; ein Klick führt den zugehörigen Runnable aus und schließt. */
    @JvmStatic
    fun showListDialog(context: Context, itemResIds: IntArray, runnables: Array<Runnable?>) {
        SoftPreconditions.checkState(itemResIds.size == runnables.size, "DialogUtils", "size mismatch")
        val items = Array<CharSequence>(itemResIds.size) { context.resources.getString(itemResIds[it]) }
        AlertDialog.Builder(context).setItems(items) { dialog, which ->
            runnables[which]?.run()
            dialog.dismiss()
        }.create().show()
    }
}
