package com.android.tv.menu

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.AttributeSet
import android.view.View
import com.android.tv.R

/** Fortschrittsbalken mit Bereich (Start/Ende) und Position (long statt int). */
class PlaybackProgressBar @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0,
) : View(context, attrs, defStyleAttr, defStyleRes) {

    private val progressDrawable: LayerDrawable
    private val primaryDrawable: Drawable
    private val secondaryDrawable: Drawable
    private var max = 100L
    private var progressStart = 0L
    private var progressEnd = 0L
    private var progress = 0L

    init {
        val a = context.obtainStyledAttributes(attrs, R.styleable.PlaybackProgressBar, defStyleAttr, defStyleRes)
        progressDrawable = a.getDrawable(R.styleable.PlaybackProgressBar_progressDrawable) as LayerDrawable
        primaryDrawable = progressDrawable.findDrawableByLayerId(android.R.id.progress)
        secondaryDrawable = progressDrawable.findDrawableByLayerId(android.R.id.secondaryProgress)
        a.recycle()
        refreshProgress()
    }

    override fun onDraw(canvas: Canvas) {
        val saveCount = canvas.save()
        canvas.translate(paddingLeft.toFloat(), paddingTop.toFloat())
        progressDrawable.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refreshProgress()
    }

    fun setMax(value: Long) {
        val m = maxOf(value, 0L)
        if (m != max) {
            max = m
            if (progressStart > m) progressStart = m
            if (progressEnd > m) progressEnd = m
            if (progress > m) progress = m
            refreshProgress()
        }
    }

    fun setProgressRange(startValue: Long, endValue: Long) {
        val start = startValue.coerceIn(0, max)
        val end = endValue.coerceIn(start, max)
        progress = progress.coerceIn(start, end)
        if (start != progressStart || end != progressEnd) {
            progressStart = start
            progressEnd = end
            setProgressLevels()
        }
    }

    fun setProgress(value: Long) {
        val p = value.coerceIn(progressStart, progressEnd)
        if (p != progress) {
            progress = p
            setProgressLevels()
        }
    }

    private fun refreshProgress() {
        progressDrawable.setBounds(0, 0, width - paddingStart - paddingEnd, height - paddingTop - paddingBottom)
        setProgressLevels()
    }

    private fun setProgressLevels() {
        var updated = setProgressBound(primaryDrawable, progressStart, progress)
        updated = setProgressBound(secondaryDrawable, progress, progressEnd) or updated
        if (updated) postInvalidate()
    }

    private fun setProgressBound(drawable: Drawable, start: Long, end: Long): Boolean {
        val oldBounds = drawable.bounds
        if (max == 0L) {
            if (!isEqualRect(oldBounds, 0, 0, 0, 0)) {
                drawable.setBounds(0, 0, 0, 0)
                return true
            }
            return false
        }
        val width = progressDrawable.bounds.width()
        val height = progressDrawable.bounds.height()
        val left = (width * start / max).toInt()
        val right = (width * end / max).toInt()
        if (!isEqualRect(oldBounds, left, 0, right, height)) {
            drawable.setBounds(left, 0, right, height)
            return true
        }
        return false
    }

    private fun isEqualRect(rect: Rect, left: Int, top: Int, right: Int, bottom: Int) =
        rect.left == left && rect.top == top && rect.right == right && rect.bottom == bottom
}
