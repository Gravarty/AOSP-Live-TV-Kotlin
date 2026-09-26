package com.android.tv.menu

/** Anzeige des Menüs (Zeilen, Ein-/Ausblenden, Aktualisieren). */
interface IMenuView {
    fun setMenuRows(menuRows: List<MenuRow>)
    fun onShow(reason: Int, rowIdToSelect: String?, runnableAfterShow: Runnable?)
    fun onHide()
    fun update(menuActive: Boolean): Boolean
    fun update(rowId: String, menuActive: Boolean): Boolean
    fun isVisible(): Boolean
}
