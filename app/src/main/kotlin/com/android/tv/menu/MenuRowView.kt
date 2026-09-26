package com.android.tv.menu

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.widget.LinearLayout
import android.widget.TextView
import com.android.tv.R

/** Basis-Ansicht einer Menüzeile (Titel + Inhalt); merkt sich den zuletzt fokussierten Eintrag. */
abstract class MenuRowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0,
) : LinearLayout(context, attrs, defStyleAttr, defStyleRes) {

    lateinit var titleView: TextView
        private set
    lateinit var contentsView: View
        private set
    val titleViewAlphaDeselected: Float
    val titleViewScaleSelected: Float
    private var lastFocusView: View? = null
    private var row: MenuRow? = null
    private val onFocusChangeListener = OnFocusChangeListener { v, hasFocus -> onChildFocusChange(v, hasFocus) }

    init {
        val outValue = TypedValue()
        resources.getValue(R.dimen.menu_row_title_alpha_deselected, outValue, true)
        titleViewAlphaDeselected = outValue.float
        val textSizeSelected = resources.getDimensionPixelSize(R.dimen.menu_row_title_text_size_selected).toFloat()
        val textSizeDeselected = resources.getDimensionPixelSize(R.dimen.menu_row_title_text_size_deselected).toFloat()
        titleViewScaleSelected = textSizeSelected / textSizeDeselected
        // Screenreader: Fokus in die Zeile holen
        accessibilityDelegate = object : AccessibilityDelegate() {
            override fun sendAccessibilityEvent(host: View, eventType: Int) {
                super.sendAccessibilityEvent(host, eventType)
                if ((eventType == AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED ||
                        eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) &&
                    row?.isReselected == false
                ) {
                    requestChildFocus()
                }
            }
        }
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        titleView = findViewById(R.id.title)
        contentsView = findViewById(getContentsViewId())
        if (contentsView.isFocusable) contentsView.onFocusChangeListener = onFocusChangeListener
        (contentsView as? ViewGroup)?.let { setOnFocusChangeListenerToChildren(it) }
        // Erste Zeile nicht unsichtbar anzeigen, bevor das Menü erscheint
        contentsView.visibility = INVISIBLE
    }

    private fun setOnFocusChangeListenerToChildren(parent: ViewGroup) {
        for (i in 0 until parent.childCount) {
            val child = parent.getChildAt(i)
            if (child.isFocusable) child.onFocusChangeListener = onFocusChangeListener
            if (child is ViewGroup) setOnFocusChangeListenerToChildren(child)
        }
    }

    protected abstract fun getContentsViewId(): Int

    open fun initialize(reason: Int) {
        lastFocusView = null
    }

    protected val menu: Menu? get() = row?.menu

    open fun onBind(row: MenuRow) {
        this.row = row
        titleView.text = row.title
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean =
        (lastFocusView ?: contentsView).requestFocus()

    protected fun setInitialFocusView(v: View) {
        lastFocusView = v
    }

    protected abstract fun requestChildFocus()

    protected open fun onChildFocusChange(v: View, hasFocus: Boolean) {
        if (hasFocus) lastFocusView = v
    }

    val rowId: String? get() = row?.getId()

    open fun onSelected(showTitle: Boolean) {
        if (row?.hideTitleWhenSelected() == true && !showTitle) {
            titleView.visibility = INVISIBLE
        } else {
            titleView.visibility = VISIBLE
            titleView.alpha = 1.0f
            titleView.scaleX = titleViewScaleSelected
            titleView.scaleY = titleViewScaleSelected
        }
        // Sichtbarmachen darf den gemerkten Fokus nicht überschreiben
        val lastFocus = lastFocusView
        contentsView.visibility = VISIBLE
        lastFocusView = lastFocus
    }

    open fun onDeselected() {
        titleView.visibility = VISIBLE
        titleView.alpha = titleViewAlphaDeselected
        titleView.scaleX = 1.0f
        titleView.scaleY = 1.0f
        contentsView.visibility = GONE
    }

    open fun getPreferredContentsHeight(): Int = row!!.height
}
