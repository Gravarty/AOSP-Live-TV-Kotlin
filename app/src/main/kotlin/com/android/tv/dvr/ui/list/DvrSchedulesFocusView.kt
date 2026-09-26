package com.android.tv.dvr.ui.list

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.android.tv.R

/** Fokus-Hintergrund (abgerundetes Rechteck) in der Aufnahmeliste; Form hängt vom View-Tag ab. */
class DvrSchedulesFocusView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    private val roundRectF = RectF()
    private val headerFocusViewTag = context.getString(R.string.dvr_schedules_header_focus_view)
    private val itemFocusViewTag = context.getString(R.string.dvr_schedules_item_focus_view)
    private val viewTag = tag as? String
    private val paint = Paint().apply { color = context.getColor(R.color.dvr_schedules_list_item_selector) }
    private val roundRectRadius = when (viewTag) {
        headerFocusViewTag -> resources.getDimensionPixelSize(R.dimen.dvr_schedules_header_selector_radius)
        itemFocusViewTag -> resources.getDimensionPixelSize(R.dimen.dvr_schedules_selector_radius)
        else -> 0
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (viewTag == headerFocusViewTag) {
            roundRectF.set(0f, 0f, width.toFloat(), height.toFloat())
        } else if (viewTag == itemFocusViewTag) {
            val drawHeight = 2 * roundRectRadius
            val drawOffset = (drawHeight - height) / 2
            roundRectF.set(0f, -drawOffset.toFloat(), width.toFloat(), (height + drawOffset).toFloat())
        }
        canvas.drawRoundRect(roundRectF, roundRectRadius.toFloat(), roundRectRadius.toFloat(), paint)
    }
}
