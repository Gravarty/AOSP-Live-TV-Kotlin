package com.android.tv.menu

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewTreeObserver
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout

/** Container des Menüs; Hoch/Runter springt zwischen sichtbaren Zeilen. */
class MenuView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle), IMenuView {

    private val layoutInflater = LayoutInflater.from(context)
    private val menuRows = ArrayList<MenuRow>()
    private val menuRowViews = ArrayList<MenuRowView>()
    private var showReason = Menu.REASON_NONE
    private val layoutManager: MenuLayoutManager

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)
        viewTreeObserver.addOnGlobalFocusChangeListener { _, newFocus ->
            getParentMenuRowView(newFocus)?.let { setSelectedPositionSmooth(menuRowViews.indexOf(it)) }
        }
        layoutManager = MenuLayoutManager(context, this)
    }

    override fun setMenuRows(menuRows: List<MenuRow>) {
        this.menuRows.clear()
        this.menuRows.addAll(menuRows)
        for (row in menuRows) {
            val view = createMenuRowView(row)
            menuRowViews.add(view)
            addView(view)
        }
        layoutManager.setMenuRowsAndViews(this.menuRows, menuRowViews)
    }

    private fun createMenuRowView(row: MenuRow): MenuRowView =
        (layoutInflater.inflate(row.getLayoutResId(), this, false) as MenuRowView).also {
            it.onBind(row)
            row.menuRowView = it
        }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) =
        layoutManager.layout(left, top, right, bottom)

    override fun onShow(reason: Int, rowIdToSelect: String?, runnableAfterShow: Runnable?) {
        showReason = reason
        if (visibility == VISIBLE) {
            // Schon sichtbar: nur die gewünschte Zeile wählen
            if (rowIdToSelect != null) {
                val position = getItemPosition(rowIdToSelect)
                if (position >= 0) {
                    menuRowViews[position].initialize(reason)
                    setSelectedPosition(position)
                }
            }
            return
        }
        initializeChildren()
        update(true)
        var position = getItemPosition(rowIdToSelect)
        if (position == -1 || !menuRows[position].isVisible()) position = getItemPosition(ChannelsRow.ID)
        setSelectedPosition(position)
        visibility = VISIBLE
        requestFocus()
        if (runnableAfterShow != null) {
            viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    viewTreeObserver.removeOnGlobalLayoutListener(this)
                    runnableAfterShow.run()
                }
            })
        }
        layoutManager.onMenuShow()
    }

    override fun onHide() {
        if (visibility == GONE) return
        layoutManager.onMenuHide()
        visibility = GONE
    }

    override fun isVisible() = visibility == VISIBLE

    override fun update(menuActive: Boolean): Boolean {
        if (!menuActive) return false
        menuRows.forEach { it.update() }
        layoutManager.onMenuRowUpdated()
        return true
    }

    override fun update(rowId: String, menuActive: Boolean): Boolean {
        if (!menuActive) return false
        val row = menuRows.firstOrNull { it.getId() == rowId } ?: return false
        row.update()
        layoutManager.onMenuRowUpdated()
        return true
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val selectedPosition = layoutManager.selectedPosition
        if (selectedPosition in menuRowViews.indices) {
            if (context.getSystemService(AccessibilityManager::class.java).isEnabled) {
                menuRowViews[selectedPosition].sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED)
            }
            return menuRowViews[selectedPosition].requestFocus()
        }
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    override fun focusableViewAvailable(v: View) {
        // Fokus nur, wenn das Menü sichtbar ist
        if (visibility == VISIBLE) super.focusableViewAvailable(v)
    }

    private fun setSelectedPosition(position: Int) = layoutManager.setSelectedPosition(position)
    private fun setSelectedPositionSmooth(position: Int) = layoutManager.setSelectedPositionSmooth(position)

    private fun initializeChildren() = menuRowViews.forEach { it.initialize(showReason) }

    private fun getItemPosition(rowIdToSelect: String?): Int =
        if (rowIdToSelect == null) -1 else menuRows.indexOfFirst { it.getId() == rowIdToSelect }

    override fun focusSearch(focused: View?, direction: Int): View? =
        if (direction == FOCUS_UP || direction == FOCUS_DOWN) getUpDownFocus(focused, direction)
        else super.focusSearch(focused, direction)

    /** Nächste sichtbare Zeile in Richtung suchen. */
    private fun getUpDownFocus(focused: View?, direction: Int): View? {
        val newView = super.focusSearch(focused, direction)
        val oldFocusedParent = getParentMenuRowView(focused)
        val newFocusedParent = getParentMenuRowView(newView)
        val selectedPosition = layoutManager.selectedPosition
        val delta = if (direction == FOCUS_UP) -1 else 1
        if (newFocusedParent !== oldFocusedParent) {
            var i = selectedPosition + delta
            while (i in menuRowViews.indices) {
                val view = menuRowViews[i]
                if (view.visibility == VISIBLE) {
                    menuRows[i].isReselected = false
                    return view
                }
                i += delta
            }
        }
        // Bugfix: bei ungültiger Auswahl (-1) nicht abstürzen
        menuRows.getOrNull(selectedPosition)?.isReselected = true
        return newView
    }

    private fun getParentMenuRowView(view: View?): MenuRowView? {
        if (view == null) return null
        val parent = view.parent
        if (parent === this) return view as MenuRowView
        return (parent as? View)?.let { getParentMenuRowView(it) }
    }
}
