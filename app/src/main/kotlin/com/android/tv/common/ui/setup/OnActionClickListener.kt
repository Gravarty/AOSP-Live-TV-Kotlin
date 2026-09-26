package com.android.tv.common.ui.setup

import android.os.Bundle

/** Empfängt Aktionen aus Setup-Fragmenten (Kategorie + ID). */
interface OnActionClickListener {
    fun onActionClick(category: String, id: Int, params: Bundle?): Boolean
}
