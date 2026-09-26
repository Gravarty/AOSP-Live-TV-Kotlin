package com.android.tv.ui

import android.content.Context
import android.media.tv.TvInputInfo
import android.util.AttributeSet
import android.util.Log
import android.widget.TextView
import com.android.tv.MainActivity
import com.android.tv.R

/** Input-Banner V2: "Label (eigenes Label)" in einer Zeile. */
class InputBannerViewV2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : InputBannerViewBase(context, attrs, defStyle) {

    private lateinit var inputLabelTextView: TextView

    override fun onFinishInflate() {
        super.onFinishInflate()
        inputLabelTextView = findViewById(R.id.input_label)
    }

    override fun updateLabel() {
        val mainActivity = context as MainActivity
        val channel = mainActivity.currentChannel
        if (channel == null || !channel.isPassthrough) return
        val input = mainActivity.tvInputManagerHelper.getTvInputInfo(channel.inputId)
        if (input == null) {
            Log.e(TAG, "unable to get TvInputInfo of id ${channel.inputId}")
            return
        }
        updateInputLabel(input)
    }

    private fun updateInputLabel(input: TvInputInfo) {
        val customLabel = input.loadCustomLabel(context)
        val label = input.loadLabel(context)
        inputLabelTextView.text = if (customLabel == null) label
        else resources.getString(R.string.input_banner_v2_input_label_format, label, customLabel)
    }

    companion object {
        private const val TAG = "InputBannerViewV2"
    }
}
