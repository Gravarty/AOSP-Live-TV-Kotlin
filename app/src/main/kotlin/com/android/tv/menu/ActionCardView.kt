package com.android.tv.menu

import android.content.Context
import android.util.AttributeSet
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import com.android.tv.R

/** Karte einer Optionen-Aktion (Symbol, Name, Zustand); deaktiviert = 30 % Deckkraft. */
class ActionCardView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : RelativeLayout(context, attrs, defStyle), ItemListRowView.CardView<MenuAction> {

    private lateinit var iconView: ImageView
    private lateinit var labelView: TextView
    private lateinit var stateView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        iconView = findViewById(R.id.action_card_icon)
        labelView = findViewById(R.id.action_card_label)
        stateView = findViewById(R.id.action_card_state)
    }

    override fun onBind(item: MenuAction, selected: Boolean) {
        iconView.setImageDrawable(item.getDrawable(context))
        labelView.text = item.getActionName(context)
        stateView.text = item.actionDescription
        val alpha = if (item.isEnabled) OPACITY_ENABLED else OPACITY_DISABLED
        isEnabled = item.isEnabled
        isFocusable = item.isEnabled
        iconView.alpha = alpha
        labelView.alpha = alpha
        stateView.alpha = alpha
    }

    override fun onSelected() {}
    override fun onDeselected() {}
    override fun onRecycled() {}

    override fun requestFocusWithAccessibility(): Boolean =
        requestFocus() && performAccessibilityAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null)

    companion object {
        private const val OPACITY_DISABLED = 0.3f
        private const val OPACITY_ENABLED = 1.0f
    }
}
