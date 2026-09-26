package com.android.tv.ui.sidepanel

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.CompoundButton
import android.widget.LinearLayout
import android.widget.TextView
import com.android.tv.R

/** Eintrag mit Schalter/Häkchen/Radio und je nach Zustand eigenem Titel. */
abstract class CompoundButtonItem(
    private val checkedTitle: String?,
    private val uncheckedTitle: String?,
    private val description: String?,
    private val maxLine: Int = 0,
) : Item() {
    constructor(title: String?, description: String?) : this(title, title, description)

    private var textView: TextView? = null
    private var compoundButton: CompoundButton? = null
    var isChecked = false
        private set

    protected abstract fun getCompoundButtonId(): Int
    protected open fun getTitleViewId() = R.id.title
    protected open fun getDescriptionViewId() = R.id.description

    override fun onBind(view: View) {
        super.onBind(view)
        compoundButton = view.findViewById(getCompoundButtonId())
        textView = view.findViewById(getTitleViewId())
        val descriptionView = view.findViewById<TextView>(getDescriptionViewId())
        if (description != null) {
            if (maxLine != 0) {
                descriptionView.maxLines = maxLine
            } else {
                if (defaultMaxLine == 0) {
                    defaultMaxLine = view.resources.getInteger(R.integer.option_item_description_max_lines)
                }
                descriptionView.maxLines = defaultMaxLine
            }
            descriptionView.visibility = View.VISIBLE
            descriptionView.text = description
        } else {
            descriptionView.visibility = View.GONE
        }
    }

    override fun onUnbind() {
        super.onUnbind()
        textView = null
        compoundButton = null
    }

    override fun onUpdate() {
        super.onUpdate()
        updateInternal()
    }

    open fun setChecked(checked: Boolean) {
        if (isChecked != checked) {
            isChecked = checked
            updateInternal()
        }
    }

    private fun updateInternal() {
        if (isBound) {
            textView?.text = if (isChecked) checkedTitle else uncheckedTitle
            compoundButton?.isChecked = isChecked
        }
    }

    companion object {
        private var defaultMaxLine = 0
    }
}

open class RadioButtonItem @JvmOverloads constructor(title: String?, description: String? = null) :
    CompoundButtonItem(title, description) {
    override fun getResourceId() = R.layout.option_item_radio_button
    override fun getCompoundButtonId() = R.id.radio_button
    override fun onSelected() = setChecked(true)
}

open class SwitchItem @JvmOverloads constructor(
    checkedTitle: String?, uncheckedTitle: String? = checkedTitle, description: String? = null, maxLines: Int = 0,
) : CompoundButtonItem(checkedTitle, uncheckedTitle, description, maxLines) {
    override fun getResourceId() = R.layout.option_item_switch
    override fun getCompoundButtonId() = R.id.switch_button
    override fun onSelected() = setChecked(!isChecked)
}

open class CheckBoxItem @JvmOverloads constructor(
    title: String?, description: String? = null, private val layoutForLargeDescription: Boolean = false,
) : CompoundButtonItem(title, description) {

    override fun onBind(view: View) {
        super.onBind(view)
        if (layoutForLargeDescription) {
            // Häkchen oben ausrichten, Beschreibung unbegrenzt
            val checkBox = view.findViewById<CompoundButton>(getCompoundButtonId())
            val lp = checkBox.layoutParams as LinearLayout.LayoutParams
            lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            lp.topMargin = view.resources.getDimensionPixelOffset(R.dimen.option_item_check_box_margin_top)
            checkBox.layoutParams = lp
            val outValue = TypedValue()
            view.resources.getValue(R.dimen.option_item_check_box_line_spacing_multiplier, outValue, true)
            view.findViewById<TextView>(getDescriptionViewId()).apply {
                maxLines = Int.MAX_VALUE
                setLineSpacing(0f, outValue.float)
            }
        }
    }

    override fun getResourceId() = R.layout.option_item_check_box
    override fun getCompoundButtonId() = R.id.check_box
    override fun onSelected() = setChecked(!isChecked)
}
