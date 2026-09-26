package com.android.tv.guide

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.util.Range
import android.view.View
import android.view.ViewTreeObserver
import androidx.leanback.widget.VerticalGridView
import com.android.tv.R
import com.android.tv.ui.OnRepeatedKeyInterceptListener
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Senkrechte Liste der Kanalzeilen. Hoch/Runter behält den horizontalen Fokusbereich bei
 * (Sendungen, die sich mit dem Bereich überlappen), am Listenende wird umgebrochen.
 */
class ProgramGrid @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : VerticalGridView(context, attrs, defStyle) {

    fun interface ChildFocusListener {
        fun onRequestChildFocus(oldFocus: View?, newFocus: View?)
    }

    private val globalFocusChangeListener = ViewTreeObserver.OnGlobalFocusChangeListener { _, newFocus ->
        if (newFocus !== nextFocusByUpDown) clearUpDownFocusState(newFocus)
        nextFocusByUpDown = null
        if (GuideUtils.isDescendant(this, newFocus)) lastFocusedView = newFocus
    }

    private val programManagerListener = object : ProgramManager.ListenerAdapter() {
        override fun onTimeRangeUpdated() = clearUpDownFocusState(null)
    }

    private val preDrawListener = object : ViewTreeObserver.OnPreDrawListener {
        override fun onPreDraw(): Boolean {
            viewTreeObserver.removeOnPreDrawListener(this)
            updateInputLogo()
            return true
        }
    }

    private lateinit var programManager: ProgramManager
    private var nextFocusByUpDown: View? = null
    private var focusRangeLeft = 0
    private var focusRangeRight = 0
    private val rowHeight = resources.getDimensionPixelSize(R.dimen.program_guide_table_item_row_height)
    private val detailHeight = resources.getDimensionPixelSize(R.dimen.program_guide_table_detail_height)
    private val selectionRow = resources.getInteger(R.integer.program_guide_selection_row)
    private var lastFocusedView: View? = null
    private val tempRect = Rect()
    var lastUpDownDirection = 0
        private set
    var isKeepCurrentProgramFocused = false
        private set
    private var childFocusListener: ChildFocusListener? = null
    private val onRepeatedKeyInterceptListener = OnRepeatedKeyInterceptListener(this)

    init {
        clearUpDownFocusState(null)
        // Kein View-Cache: sonst stimmen Fokus-Bereiche nicht
        setItemViewCacheSize(0)
        setOnKeyInterceptListener(onRepeatedKeyInterceptListener)
    }

    override fun requestChildFocus(child: View?, focused: View?) {
        childFocusListener?.onRequestChildFocus(focusedChild, child)
        super.requestChildFocus(child, focused)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        viewTreeObserver.addOnGlobalFocusChangeListener(globalFocusChangeListener)
        programManager.addListener(programManagerListener)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewTreeObserver.removeOnGlobalFocusChangeListener(globalFocusChangeListener)
        programManager.removeListener(programManagerListener)
        clearUpDownFocusState(null)
    }

    override fun focusSearch(focused: View?, direction: Int): View? {
        nextFocusByUpDown = null
        if (focused == null || (focused !== this && !GuideUtils.isDescendant(this, focused))) {
            return super.focusSearch(focused, direction)
        }
        if (direction == FOCUS_UP || direction == FOCUS_DOWN) {
            updateUpDownFocusState(focused, direction)
            focusFind(focused, direction)?.let { return it }
        }
        return super.focusSearch(focused, direction)
    }

    override fun onRequestFocusInDescendants(direction: Int, previouslyFocusedRect: Rect?): Boolean {
        val last = lastFocusedView
        if (last != null && last.isShown && last.requestFocus()) return true
        return super.onRequestFocusInDescendants(direction, previouslyFocusedRect)
    }

    /** Beim beschleunigten Scrollen die Fokuszeile im sichtbaren Band halten. */
    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        val focusedView = findFocus()
        if (focusedView != null && onRepeatedKeyInterceptListener.isFocusAccelerated) {
            val location = IntArray(2)
            getLocationOnScreen(location)
            val focusedLocation = IntArray(2)
            focusedView.getLocationOnScreen(focusedLocation)
            val y = focusedLocation[1] - location[1]
            val minY = (selectionRow - 1) * rowHeight
            if (y < minY) scrollBy(0, y - minY)
            val maxY = (selectionRow + 1) * rowHeight + detailHeight
            if (y > maxY) scrollBy(0, y - maxY)
        }
        updateInputLogo()
    }

    override fun onViewRemoved(view: View) = updateInputLogo()

    internal fun initialize(programManager: ProgramManager) { this.programManager = programManager }
    internal fun setChildFocusListener(listener: ChildFocusListener?) { childFocusListener = listener }
    internal fun onItemSelectionReset() = viewTreeObserver.addOnPreDrawListener(preDrawListener)

    internal fun resetFocusState() {
        lastFocusedView = null
        clearUpDownFocusState(null)
    }

    internal val focusRange: Range<Int> get() = Range(focusRangeLeft, focusRangeRight)

    private fun focusFind(focused: View, direction: Int): View? {
        val focusedChildIndex = getFocusedChildIndex()
        if (focusedChildIndex == INVALID_INDEX) {
            Log.w(TAG, "No child view has focus")
            return null
        }
        val nextChildIndex = if (direction == FOCUS_UP) focusedChildIndex - 1 else focusedChildIndex + 1
        if (nextChildIndex < 0 || nextChildIndex >= childCount) {
            // Umbruch am Listenanfang/-ende
            val itemCountTotal = adapter?.itemCount ?: 0
            if (selectedPosition == 0) {
                scrollToPosition(itemCountTotal - 1)
                return null
            } else if (selectedPosition == itemCountTotal - 1) {
                val itemCount = layoutManager!!.itemCount
                if (itemCount > 2 * (childCount + 1) || itemCount <= childCount) {
                    scrollToPosition(0)
                    return null
                }
                smoothScrollToPosition(0)
                return getChildAt(0)
            }
            return focused
        }
        val next = GuideUtils.findNextFocusedProgram(getChildAt(nextChildIndex), focusRangeLeft, focusRangeRight, isKeepCurrentProgramFocused)
        if (next != null) {
            next.getGlobalVisibleRect(tempRect)
            nextFocusByUpDown = next
        } else {
            Log.w(TAG, "focusFind doesn't find proper focusable")
        }
        return next
    }

    private fun getFocusedChildIndex(): Int = (0 until childCount).firstOrNull { getChildAt(it).hasFocus() } ?: INVALID_INDEX

    /** Fokusbereich auf die Schnittmenge mit der aktuellen Sendung verengen. */
    private fun updateUpDownFocusState(focused: View, direction: Int) {
        lastUpDownDirection = direction
        val rightMost = getRightMostFocusablePosition()
        val focusedRect = tempRect
        focused.getGlobalVisibleRect(focusedRect)
        focusRangeLeft = min(focusRangeLeft, rightMost)
        focusRangeRight = min(focusRangeRight, rightMost)
        focusedRect.left = min(focusedRect.left, rightMost)
        focusedRect.right = min(focusedRect.right, rightMost)
        if (focusedRect.left > focusRangeRight || focusedRect.right < focusRangeLeft) {
            Log.w(TAG, "The current focus is out of [focusRangeLeft, focusRangeRight]")
            focusRangeLeft = focusedRect.left
            focusRangeRight = focusedRect.right
            return
        }
        focusRangeLeft = max(focusRangeLeft, focusedRect.left)
        focusRangeRight = min(focusRangeRight, focusedRect.right)
    }

    private fun clearUpDownFocusState(focus: View?) {
        lastUpDownDirection = 0
        focusRangeLeft = 0
        focusRangeRight = getRightMostFocusablePosition()
        nextFocusByUpDown = null
        isKeepCurrentProgramFocused = focus !is ProgramItemView || GuideUtils.isCurrentProgram(focus)
    }

    /** Rechter Rand minus 15 min: Sendungen, die erst dort beginnen, nicht fokussieren. */
    private fun getRightMostFocusablePosition(): Int {
        if (!getGlobalVisibleRect(tempRect)) return Int.MAX_VALUE
        return tempRect.right - GuideUtils.convertMillisToPixel(FOCUS_AREA_RIGHT_MARGIN_MILLIS)
    }

    private fun getFirstVisibleChildIndex(): Int {
        val lm = layoutManager ?: return -1
        val top = lm.paddingTop
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if ((lm.getDecoratedTop(child) + lm.getDecoratedBottom(child)) / 2 > top) return i
        }
        return -1
    }

    /** Input-Logo nur in der ersten Zeile eines Inputs zeigen. */
    private fun updateInputLogo() {
        if (childCount == 0) return
        val first = getFirstVisibleChildIndex()
        if (first == -1) return
        var childView = getChildAt(first)
        var childAdapterPosition = getChildAdapterPosition(childView)
        (getChildViewHolder(childView) as ProgramTableAdapter.ProgramRowViewHolder).updateInputLogo(childAdapterPosition, true)
        for (i in first + 1 until childCount) {
            childView = getChildAt(i)
            (getChildViewHolder(childView) as ProgramTableAdapter.ProgramRowViewHolder).updateInputLogo(childAdapterPosition, false)
            childAdapterPosition = getChildAdapterPosition(childView)
        }
    }

    companion object {
        private const val TAG = "ProgramGrid"
        private const val INVALID_INDEX = -1
        private val FOCUS_AREA_RIGHT_MARGIN_MILLIS = TimeUnit.MINUTES.toMillis(15)
    }
}
