package com.android.tv.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import com.android.tv.MainActivity
import com.android.tv.R

/** Input-Banner: eigenes Label groß, Standard-Label klein darunter. */
class InputBannerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : InputBannerViewBase(context, attrs, defStyle) {

    private lateinit var inputLabelTextView: TextView
    private lateinit var secondaryInputLabelTextView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        inputLabelTextView = findViewById(R.id.input_label)
        secondaryInputLabelTextView = findViewById(R.id.secondary_input_label)
    }

    /** Bugfix: Original stürzte ab, wenn der Input nicht (mehr) existiert. */
    override fun updateLabel() {
        val mainActivity = context as MainActivity
        val channel = mainActivity.currentChannel
        if (channel == null || !channel.isPassthrough) return
        val input = mainActivity.tvInputManagerHelper.getTvInputInfo(channel.inputId) ?: return
        val customLabel = input.loadCustomLabel(context)
        val label = input.loadLabel(context)
        if (customLabel.isNullOrEmpty() || customLabel == label) {
            inputLabelTextView.text = label
            secondaryInputLabelTextView.visibility = View.GONE
        } else {
            inputLabelTextView.text = customLabel
            secondaryInputLabelTextView.text = label
            secondaryInputLabelTextView.visibility = View.VISIBLE
        }
    }
}
