package com.android.tv.menu

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.Rect
import android.util.Log
import android.util.Property
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.R
import com.android.tv.common.SoftPreconditions
import com.android.tv.util.Utils
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

/**
 * Anordnung und Animation der Menüzeilen: die gewählte Zeile ist vertikal ausgerichtet, darüber
 * und darunter nur Titel. Wechsel zwischen Zeilen und Ein-/Ausblenden von Zeilen wird animiert.
 */
@UiThread
class MenuLayoutManager(context: Context, private val menuView: MenuView) {
    private val menuRows = ArrayList<MenuRow>()
    private val menuRowViews = ArrayList<MenuRowView>()
    private val removingRowViews = ArrayList<Int>()
    var selectedPosition = INVALID_POSITION
        private set
    private var pendingSelectedPosition = INVALID_POSITION

    private val res = context.resources
    private val rowAlignFromBottom = res.getDimensionPixelOffset(R.dimen.menu_row_align_from_bottom)
    private val rowContentsPaddingTop = res.getDimensionPixelOffset(R.dimen.menu_row_contents_padding_top)
    private val rowContentsPaddingBottomMax = res.getDimensionPixelOffset(R.dimen.menu_row_contents_padding_bottom_max)
    private val rowTitleTextDescenderHeight = res.getDimensionPixelOffset(R.dimen.menu_row_title_text_descender_height)
    private val menuMarginBottomMin = res.getDimensionPixelOffset(R.dimen.menu_margin_bottom_min)
    private val rowTitleHeight = res.getDimensionPixelSize(R.dimen.menu_row_title_height)
    private val rowScrollUpAnimationOffset = res.getDimensionPixelOffset(R.dimen.menu_row_scroll_up_anim_offset)
    private val rowAnimationDuration = res.getInteger(R.integer.menu_row_selection_anim_duration).toLong()
    private val oldContentsFadeOutDuration = res.getInteger(R.integer.menu_previous_contents_fade_out_duration).toLong()
    private val currentContentsFadeInDuration = res.getInteger(R.integer.menu_current_contents_fade_in_duration).toLong()
    private val fastOutSlowIn: TimeInterpolator = FastOutSlowInInterpolator()
    private val fastOutLinearIn: TimeInterpolator = FastOutLinearInInterpolator()
    private val linearOutSlowIn: TimeInterpolator = LinearOutSlowInInterpolator()
    private var animatorSet: AnimatorSet? = null
    private var titleFadeOutAnimator: ObjectAnimator? = null
    private val propertyValuesAfterAnimation = ArrayList<ViewPropertyValueHolder>()
    private var tempTitleViewForOld: TextView? = null
    private var tempTitleViewForCurrent: TextView? = null

    fun setMenuRowsAndViews(menuRows: List<MenuRow>, menuRowViews: List<MenuRowView>) {
        this.menuRows.clear()
        this.menuRows.addAll(menuRows)
        this.menuRowViews.clear()
        this.menuRowViews.addAll(menuRowViews)
    }

    /** Layout ohne Animation; während einer Animation übernimmt diese das Layout. */
    fun layout(left: Int, top: Int, right: Int, bottom: Int) {
        if (animatorSet != null) return
        // Bugfix: ohne gültige Auswahl nicht abstürzen
        if (!Utils.isIndexValid(menuRowViews, selectedPosition)) return
        if (menuRowViews[selectedPosition].visibility == View.GONE) {
            // Gewählte Zeile ausgeblendet: erste sichtbare nehmen
            val first = findNextVisiblePosition(INVALID_POSITION)
            if (first == INVALID_POSITION) return
            selectedPosition = first
        }
        val layouts = getViewLayouts(left, top, right, bottom)
        for (i in menuRowViews.indices) {
            layouts[i]?.let { menuRowViews[i].layout(it.left, it.top, it.right, it.bottom) }
        }
        // Nach dem ersten Anzeigen sind die Inhalte noch INVISIBLE
        for (view in menuRowViews) {
            if (view.visibility == View.VISIBLE && view.contentsView.visibility == View.INVISIBLE) view.onDeselected()
        }
        if (pendingSelectedPosition != INVALID_POSITION) setSelectedPositionSmooth(pendingSelectedPosition)
    }

    private fun findNextVisiblePosition(start: Int): Int {
        for (i in start + 1 until menuRowViews.size) if (menuRowViews[i].visibility != View.GONE) return i
        return INVALID_POSITION
    }

    private fun isVisibleInLayout(position: Int, view: MenuRowView, rowsToAdd: List<Int>, rowsToRemove: List<Int>) =
        (view.visibility != View.GONE && position !in rowsToRemove) || position in rowsToAdd

    /** Rechtecke je Zeile (null = nicht sichtbar), relativ zum Menü. */
    private fun getViewLayouts(
        left: Int, top: Int, right: Int, bottom: Int,
        rowsToAdd: List<Int> = emptyList(), rowsToRemove: List<Int> = emptyList(),
    ): MutableList<Rect?> {
        val relativeLeft = 0
        val relativeRight = right - left
        val relativeBottom = bottom - top
        val layouts = ArrayList<Rect?>()
        val count = menuRowViews.size
        val selectedView = menuRowViews[selectedPosition]
        val rowTitleHeight = selectedView.titleView.measuredHeight
        val rowContentsHeight = selectedView.getPreferredContentsHeight()
        // Gewählte Zeile
        var childTop = relativeBottom - rowAlignFromBottom - rowContentsHeight / 2 - rowContentsPaddingTop - rowTitleHeight
        var childBottom = relativeBottom
        var position = selectedPosition + 1
        while (position < count) {
            val nextView = menuRowViews[position]
            if (isVisibleInLayout(position, nextView, rowsToAdd, rowsToRemove)) {
                val nextTitleTopMax = relativeBottom - menuMarginBottomMin - rowTitleHeight + rowTitleTextDescenderHeight
                val childBottomMax = relativeBottom - rowAlignFromBottom + rowContentsHeight / 2 +
                    rowContentsPaddingBottomMax - rowTitleHeight
                childBottom = min(nextTitleTopMax, childBottomMax)
                layouts.add(Rect(relativeLeft, childBottom, relativeRight, relativeBottom))
                break
            } else {
                layouts.add(null)
            }
            ++position
        }
        layouts.add(0, Rect(relativeLeft, childTop, relativeRight, childBottom))
        // Zeilen darüber (nur Titel)
        for (i in selectedPosition - 1 downTo 0) {
            if (isVisibleInLayout(i, menuRowViews[i], rowsToAdd, rowsToRemove)) {
                childTop -= this.rowTitleHeight
                childBottom = childTop + rowTitleHeight
                layouts.add(0, Rect(relativeLeft, childTop, relativeRight, childBottom))
            } else {
                layouts.add(0, null)
            }
        }
        // Weitere Zeilen darunter liegen außerhalb des Bildschirms
        childTop = relativeBottom
        ++position
        while (position < count) {
            if (isVisibleInLayout(position, menuRowViews[position], rowsToAdd, rowsToRemove)) {
                childBottom = childTop + rowTitleHeight
                layouts.add(Rect(relativeLeft, childTop, relativeRight, childBottom))
                childTop += this.rowTitleHeight
            } else {
                layouts.add(null)
            }
            ++position
        }
        return layouts
    }

    /** Auswahl ohne Animation. */
    fun setSelectedPosition(position: Int) {
        if (selectedPosition == position) return
        val indexValid = Utils.isIndexValid(menuRowViews, position)
        SoftPreconditions.checkArgument(indexValid, TAG, "position %s ", position)
        if (!indexValid) return
        if (!menuRows[position].isVisible()) {
            Log.e(TAG, "Selecting invisible row: $position")
            return
        }
        if (Utils.isIndexValid(menuRowViews, selectedPosition)) menuRowViews[selectedPosition].onDeselected()
        selectedPosition = position
        pendingSelectedPosition = INVALID_POSITION
        menuRowViews[selectedPosition].onSelected(false)
        if (menuView.visibility == View.VISIBLE) {
            // Fokus erst hier umsetzen, sonst springt der Fokus
            menuView.requestFocus()
            menuView.requestLayout()
        }
    }

    /** Auswahl mit Animation (Titel/Inhalt wandern, alte Zeile blendet aus). */
    fun setSelectedPositionSmooth(position: Int) {
        if (menuView.visibility != View.VISIBLE) {
            setSelectedPosition(position)
            return
        }
        if (selectedPosition == position) return
        val oldIndexValid = Utils.isIndexValid(menuRowViews, selectedPosition)
        SoftPreconditions.checkState(oldIndexValid, TAG, "No previous selection: $selectedPosition")
        if (!oldIndexValid) return
        val newIndexValid = Utils.isIndexValid(menuRowViews, position)
        SoftPreconditions.checkArgument(newIndexValid, TAG, "position %s", position)
        if (!newIndexValid) return
        if (!menuRows[position].isVisible()) {
            Log.e(TAG, "Moving to the invisible row: $position")
            return
        }
        animatorSet?.end()
        titleFadeOutAnimator?.cancel()
        val currentView = menuRowViews[position]
        val currentTitleView = currentView.titleView
        val currentContentsView = currentView.contentsView
        currentTitleView.visibility = View.VISIBLE
        currentContentsView.visibility = View.VISIBLE
        if (currentView is PlayControlsRowView) currentView.onPreselected()
        // Liste noch nicht fertig gebaut: nach dem Layout erneut versuchen
        if (currentContentsView is RecyclerView && currentContentsView.hasPendingAdapterUpdates()) {
            currentContentsView.requestLayout()
            pendingSelectedPosition = position
            return
        }
        val oldPosition = selectedPosition
        selectedPosition = position
        pendingSelectedPosition = INVALID_POSITION
        // Fokus vor der Animation setzen
        menuView.requestFocus()
        if (tempTitleViewForOld == null) {
            // Hilfs-Titel für die Animation (liegen im Menü-Layout)
            tempTitleViewForOld = menuView.findViewById(R.id.temp_title_for_old)
            tempTitleViewForCurrent = menuView.findViewById(R.id.temp_title_for_current)
        }
        val tempOld = tempTitleViewForOld!!
        val tempCurrent = tempTitleViewForCurrent!!
        propertyValuesAfterAnimation.clear()
        val animators = ArrayList<Animator>()
        val scrollDown = position > oldPosition
        val layouts = getViewLayouts(menuView.left, menuView.top, menuView.right, menuView.bottom)

        // Alte Zeile
        val oldRow = menuRows[oldPosition]
        val oldView = menuRowViews[oldPosition]
        val oldContentsView = oldView.contentsView
        animators.add(createAlphaAnimator(oldContentsView, 1.0f, 0.0f, 1.0f, linearOutSlowIn).setDuration(oldContentsFadeOutDuration))
        val oldTitleView = oldView.titleView
        setTempTitleView(tempOld, oldTitleView)
        val oldLayoutRect = layouts[oldPosition]!!
        if (scrollDown) {
            if (oldRow.hideTitleWhenSelected() && oldTitleView.visibility != View.VISIBLE) {
                // Ausgeblendeter Titel: von unten einblenden
                tempOld.scaleX = 1.0f
                tempOld.scaleY = 1.0f
                animators.add(createAlphaAnimator(tempOld, 0.0f, oldView.titleViewAlphaDeselected, fastOutLinearIn))
                val offset = oldLayoutRect.top - tempOld.top
                animators.add(createTranslationYAnimator(tempOld, (offset + rowScrollUpAnimationOffset).toFloat(), offset.toFloat()))
            } else {
                animators.add(createScaleXAnimator(tempOld, oldView.titleViewScaleSelected, 1.0f))
                animators.add(createScaleYAnimator(tempOld, oldView.titleViewScaleSelected, 1.0f))
                animators.add(createAlphaAnimator(tempOld, oldTitleView.alpha, oldView.titleViewAlphaDeselected, linearOutSlowIn))
                animators.add(createTranslationYAnimator(tempOld, 0f, (oldLayoutRect.top - tempOld.top).toFloat()))
            }
            oldTitleView.alpha = oldView.titleViewAlphaDeselected
            oldTitleView.visibility = View.INVISIBLE
        } else {
            val currentLayoutRect = Rect(layouts[position])
            val distanceCurrentTitle = currentLayoutRect.top - currentView.top
            val distance = max(rowScrollUpAnimationOffset, distanceCurrentTitle)
            val distanceToTopOfSecondTitle = oldLayoutRect.top - rowScrollUpAnimationOffset - oldView.top
            animators.add(createTranslationYAnimator(oldTitleView, 0.0f, min(distance, distanceToTopOfSecondTitle).toFloat()))
            animators.add(createAlphaAnimator(oldTitleView, 1.0f, 0.0f, 1.0f, linearOutSlowIn).setDuration(oldContentsFadeOutDuration))
            animators.add(createScaleXAnimator(oldTitleView, oldView.titleViewScaleSelected, 1.0f))
            animators.add(createScaleYAnimator(oldTitleView, oldView.titleViewScaleSelected, 1.0f))
            tempOld.scaleX = 1.0f
            tempOld.scaleY = 1.0f
            animators.add(createAlphaAnimator(tempOld, 0.0f, oldView.titleViewAlphaDeselected, fastOutLinearIn))
            val offset = oldLayoutRect.top - tempOld.top
            animators.add(createTranslationYAnimator(tempOld, (offset - rowScrollUpAnimationOffset).toFloat(), offset.toFloat()))
        }

        // Neue Zeile
        val currentLayoutRect = Rect(layouts[position])
        currentContentsView.alpha = 0.0f
        if (scrollDown) {
            setTempTitleView(tempCurrent, currentTitleView)
            val distanceOldTitle = oldView.top - oldLayoutRect.top
            val distance = max(rowScrollUpAnimationOffset, distanceOldTitle)
            val distanceTopOfSecondTitle = currentView.top - rowScrollUpAnimationOffset - currentLayoutRect.top
            animators.add(createTranslationYAnimator(currentTitleView, min(distance, distanceTopOfSecondTitle).toFloat(), 0.0f))
            currentView.top = currentLayoutRect.top
            var animator = createAlphaAnimator(currentTitleView, 0.0f, 1.0f, fastOutLinearIn).setDuration(currentContentsFadeInDuration)
            animator.startDelay = oldContentsFadeOutDuration
            currentTitleView.alpha = 0.0f
            animators.add(animator)
            animators.add(createScaleXAnimator(currentTitleView, 1.0f, currentView.titleViewScaleSelected))
            animators.add(createScaleYAnimator(currentTitleView, 1.0f, currentView.titleViewScaleSelected))
            animators.add(createTranslationYAnimator(tempCurrent, 0.0f, -rowScrollUpAnimationOffset.toFloat()))
            animators.add(createAlphaAnimator(tempCurrent, currentView.titleViewAlphaDeselected, 0f, linearOutSlowIn))
            animators.add(createTranslationYAnimator(currentContentsView, rowScrollUpAnimationOffset.toFloat(), 0.0f))
            animator = createAlphaAnimator(currentContentsView, 0.0f, 1.0f, fastOutLinearIn).setDuration(currentContentsFadeInDuration)
            animator.startDelay = oldContentsFadeOutDuration
            animators.add(animator)
        } else {
            currentView.bottom = currentLayoutRect.bottom
            val currentViewOffset = currentLayoutRect.top - currentView.top
            animators.add(createTranslationYAnimator(currentTitleView, 0f, currentViewOffset.toFloat()))
            animators.add(createAlphaAnimator(currentTitleView, currentView.titleViewAlphaDeselected, 1.0f, fastOutSlowIn))
            animators.add(createScaleXAnimator(currentTitleView, 1.0f, currentView.titleViewScaleSelected))
            animators.add(createScaleYAnimator(currentTitleView, 1.0f, currentView.titleViewScaleSelected))
            animators.add(createTranslationYAnimator(currentContentsView,
                (currentViewOffset - rowScrollUpAnimationOffset).toFloat(), currentViewOffset.toFloat()))
            val animator = createAlphaAnimator(currentContentsView, 0.0f, 1.0f, fastOutLinearIn).setDuration(currentContentsFadeInDuration)
            animator.startDelay = oldContentsFadeOutDuration
            animators.add(animator)
        }

        // Zeile unter der neuen/alten Auswahl
        val nextPosition: Int
        if (scrollDown) {
            nextPosition = findNextVisiblePosition(position)
            if (nextPosition != INVALID_POSITION) {
                val nextView = menuRowViews[nextPosition]
                val nextLayoutRect = layouts[nextPosition]!!
                animators.add(createTranslationYAnimator(nextView,
                    (nextLayoutRect.top + rowScrollUpAnimationOffset - nextView.top).toFloat(),
                    (nextLayoutRect.top - nextView.top).toFloat()))
                animators.add(createAlphaAnimator(nextView, 0.0f, 1.0f, fastOutLinearIn))
            }
        } else {
            nextPosition = findNextVisiblePosition(oldPosition)
            if (nextPosition != INVALID_POSITION) {
                val nextView = menuRowViews[nextPosition]
                animators.add(createTranslationYAnimator(nextView, 0f, rowScrollUpAnimationOffset.toFloat()))
                animators.add(createAlphaAnimator(nextView, nextView.titleViewAlphaDeselected, 0.0f, 1.0f, linearOutSlowIn))
            }
        }

        // Übrige sichtbare Zeilen verschieben
        for (i in menuRowViews.indices) {
            val view = menuRowViews[i]
            if (view.visibility == View.VISIBLE && i != oldPosition && i != position && i != nextPosition) {
                val rect = layouts[i] ?: continue
                animators.add(createTranslationYAnimator(view, 0f, (rect.top - view.top).toFloat()))
            }
        }

        val valuesAfter = ArrayList(propertyValuesAfterAnimation)
        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animator: Animator) {
                    animatorSet = null
                    // Eigenschaften zurücksetzen, sonst wirken sie beim nächsten Layout nach
                    valuesAfter.forEach { it.property.set(it.view, it.value) }
                    oldView.onDeselected()
                    currentView.onSelected(true)
                    tempOld.visibility = View.GONE
                    tempCurrent.visibility = View.GONE
                    layout(menuView.left, menuView.top, menuView.right, menuView.bottom)
                    if (menuRows[position].hideTitleWhenSelected()) {
                        val titleView = menuRowViews[position].titleView
                        titleFadeOutAnimator = createAlphaAnimator(titleView, titleView.alpha, 0.0f, linearOutSlowIn).apply {
                            startDelay = TITLE_SHOW_DURATION_BEFORE_HIDDEN_MS
                            addListener(object : AnimatorListenerAdapter() {
                                private var canceled = false
                                override fun onAnimationCancel(animator: Animator) { canceled = true }
                                override fun onAnimationEnd(animator: Animator) {
                                    titleFadeOutAnimator = null
                                    if (!canceled) menuRowViews[position].onSelected(false)
                                }
                            })
                            start()
                        }
                    }
                }
            })
            start()
        }
    }

    /** Kopiert Text/Position/Aussehen eines Titels in einen Hilfs-Titel für die Animation. */
    private fun setTempTitleView(dest: TextView, src: TextView) {
        dest.visibility = View.VISIBLE
        dest.text = src.text
        dest.translationY = 0.0f
        if (src.visibility == View.VISIBLE) {
            dest.alpha = src.alpha
            dest.scaleX = src.scaleX
            dest.scaleY = src.scaleY
        } else {
            dest.alpha = 0.0f
            dest.scaleX = 1.0f
            dest.scaleY = 1.0f
        }
        val parent = src.parent as View
        dest.left = src.left + parent.left
        dest.right = src.right + parent.left
        dest.top = src.top + parent.top
        dest.bottom = src.bottom + parent.top
    }

    /** Zeilen ein-/ausblenden, deren Sichtbarkeit sich geändert hat (animiert, wenn das Menü offen ist). */
    fun onMenuRowUpdated() {
        if (menuView.visibility != View.VISIBLE) {
            for (i in menuRowViews.indices) menuRowViews[i].visibility = if (menuRows[i].isVisible()) View.VISIBLE else View.GONE
            return
        }
        val addedRowViews = ArrayList<Int>()
        val removedRowViews = ArrayList<Int>()
        val offsetsToMove = HashMap<Int, Int>()
        var added = 0
        for (i in selectedPosition - 1 downTo 0) {
            val row = menuRows[i]
            val view = menuRowViews[i]
            if (row.isVisible() && (view.visibility == View.GONE || i in removingRowViews)) {
                addedRowViews.add(i); ++added
            } else if (!row.isVisible() && view.visibility == View.VISIBLE) {
                removedRowViews.add(i); --added
            } else if (added != 0) {
                offsetsToMove[i] = -added
            }
        }
        added = 0
        for (i in selectedPosition + 1 until menuRowViews.size) {
            val row = menuRows[i]
            val view = menuRowViews[i]
            if (row.isVisible() && (view.visibility == View.GONE || i in removingRowViews)) {
                addedRowViews.add(i); ++added
            } else if (!row.isVisible() && view.visibility == View.VISIBLE) {
                removedRowViews.add(i); --added
            } else if (added != 0) {
                offsetsToMove[i] = added
            }
        }
        if (addedRowViews.isEmpty() && removedRowViews.isEmpty()) return
        animatorSet?.end()
        titleFadeOutAnimator?.end()
        propertyValuesAfterAnimation.clear()
        val animators = ArrayList<Animator>()
        val layouts = getViewLayouts(menuView.left, menuView.top, menuView.right, menuView.bottom, addedRowViews, removedRowViews)
        for (position in addedRowViews) {
            val view = menuRowViews[position]
            view.visibility = View.VISIBLE
            val rect = layouts[position]!!
            // Sofort layouten, damit die Animation die richtige Position hat
            view.layout(rect.left, rect.top, rect.right, rect.bottom)
            val titleView = view.titleView
            val params = titleView.layoutParams as MarginLayoutParams
            titleView.layout(
                view.paddingLeft + params.leftMargin,
                view.paddingTop + params.topMargin,
                rect.right - rect.left - view.paddingRight - params.rightMargin,
                rect.bottom - rect.top - view.paddingBottom - params.bottomMargin)
            animators.add(createAlphaAnimator(view, 0.0f, 1.0f, fastOutLinearIn))
        }
        for (position in removedRowViews) {
            animators.add(createAlphaAnimator(menuRowViews[position], 1.0f, 0.0f, 1.0f, linearOutSlowIn))
        }
        for ((key, value) in offsetsToMove) {
            animators.add(createTranslationYAnimator(menuRowViews[key], 0f, (value * rowTitleHeight).toFloat()))
        }
        val valuesAfter = ArrayList(propertyValuesAfterAnimation)
        removingRowViews.clear()
        removingRowViews.addAll(removedRowViews)
        animatorSet = AnimatorSet().apply {
            playTogether(animators)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    animatorSet = null
                    valuesAfter.forEach { it.property.set(it.view, it.value) }
                    removingRowViews.forEach { menuRowViews[it].visibility = View.GONE }
                    layout(menuView.left, menuView.top, menuView.right, menuView.bottom)
                }
            })
            start()
        }
    }

    private fun createTranslationYAnimator(view: View, from: Float, to: Float): ObjectAnimator {
        propertyValuesAfterAnimation.add(ViewPropertyValueHolder(View.TRANSLATION_Y, view, 0f))
        return ObjectAnimator.ofFloat(view, View.TRANSLATION_Y, from, to).apply {
            duration = rowAnimationDuration
            interpolator = fastOutSlowIn
        }
    }

    private fun createAlphaAnimator(view: View, from: Float, to: Float, interpolator: TimeInterpolator) =
        ObjectAnimator.ofFloat(view, View.ALPHA, from, to).apply {
            duration = rowAnimationDuration
            this.interpolator = interpolator
        }

    private fun createAlphaAnimator(view: View, from: Float, to: Float, end: Float, interpolator: TimeInterpolator): ObjectAnimator {
        propertyValuesAfterAnimation.add(ViewPropertyValueHolder(View.ALPHA, view, end))
        return createAlphaAnimator(view, from, to, interpolator)
    }

    private fun createScaleXAnimator(view: View, from: Float, to: Float) =
        ObjectAnimator.ofFloat(view, View.SCALE_X, from, to).apply {
            duration = rowAnimationDuration
            interpolator = fastOutSlowIn
        }

    private fun createScaleYAnimator(view: View, from: Float, to: Float) =
        ObjectAnimator.ofFloat(view, View.SCALE_Y, from, to).apply {
            duration = rowAnimationDuration
            interpolator = fastOutSlowIn
        }

    private class ViewPropertyValueHolder(val property: Property<View, Float>, val view: View, val value: Float)

    fun onMenuShow() {}

    fun onMenuHide() {
        animatorSet?.end()
        animatorSet = null
        titleFadeOutAnimator?.end()
        titleFadeOutAnimator = null
    }

    companion object {
        private const val TAG = "MenuLayoutManager"
        private val TITLE_SHOW_DURATION_BEFORE_HIDDEN_MS = TimeUnit.SECONDS.toMillis(2)
        private const val INVALID_POSITION = -1
    }
}
