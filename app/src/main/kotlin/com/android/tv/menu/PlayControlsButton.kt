package com.android.tv.menu

import android.animation.ValueAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.tv.R

/** Knopf der Wiedergabe-Zeile (Symbol oder Text, Farbwechsel bei Fokus). */
class PlayControlsButton @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr, defStyleRes) {

    private val button: ImageView
    private val icon: ImageView
    private val label: TextView
    private val focusAnimationTimeMs = context.resources.getInteger(android.R.integer.config_shortAnimTime).toLong()
    private val iconColor = context.resources.getColor(R.color.play_controls_icon_color, null)
    private var iconFocusedColor = iconColor
    private var imageResourceId = 0
    private var tintColor = 0

    init {
        inflate(context, R.layout.play_controls_button, this)
        button = findViewById(R.id.button)
        icon = findViewById(R.id.icon)
        label = findViewById(R.id.label)
    }

    fun setImageResId(imageResId: Int) {
        val newTintColor = if (hasFocus()) iconFocusedColor else iconColor
        if (imageResourceId != imageResId) {
            imageResourceId = imageResId
            icon.setImageResource(imageResId)
            updateTint(newTintColor)
        } else if (newTintColor != tintColor) {
            updateTint(newTintColor)
        }
    }

    private fun updateTint(tintColor: Int) {
        this.tintColor = tintColor
        icon.drawable?.setTint(tintColor)
    }

    fun setAction(clickAction: Runnable) = button.setOnClickListener { clickAction.run() }

    /** Symbolfarbe animiert wechseln, wenn der Knopf fokussiert wird. */
    fun setFocusedIconColor(color: Int) {
        val valueAnimator = ValueAnimator.ofArgb(iconColor, color).apply {
            addUpdateListener { icon.drawable?.setTint(it.animatedValue as Int) }
            duration = focusAnimationTimeMs
        }
        button.setOnFocusChangeListener { _, hasFocus -> if (hasFocus) valueAnimator.start() else valueAnimator.reverse() }
        iconFocusedColor = color
    }

    fun setLabel(label: String?) {
        if (label.isNullOrEmpty()) {
            icon.visibility = View.VISIBLE
            this.label.visibility = View.GONE
        } else {
            icon.visibility = View.GONE
            this.label.visibility = View.VISIBLE
            if (this.label.text != label) this.label.text = label
        }
    }

    fun hideRippleAnimation() {
        button.drawable?.jumpToCurrentState()
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        button.isEnabled = enabled
        button.isFocusable = enabled
        icon.isEnabled = enabled
        icon.alpha = if (enabled) ALPHA_ENABLED else ALPHA_DISABLED
        label.isEnabled = enabled
    }

    fun requestFocusWithAccessibility(): Boolean =
        button.requestFocus() && button.performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)

    companion object {
        private const val ALPHA_ENABLED = 1.0f
        private const val ALPHA_DISABLED = 0.3f
    }
}
