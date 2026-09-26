package com.android.tv.ui

import android.content.Context
import android.media.tv.TvInputInfo
import android.util.AttributeSet
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.android.tv.R

/** "Kein Signal an <Input>"-Hinweis mit HDMI- bzw. Tuner-Symbol. */
class EmptyInputStatusBlockView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyle: Int = 0,
) : FrameLayout(context, attrs, defStyle) {

    private lateinit var icon: ImageView
    private lateinit var title: TextView

    init {
        inflate(context, R.layout.empty_input_status_block, this)
    }

    override fun onFinishInflate() {
        super.onFinishInflate()
        icon = findViewById(R.id.empty_input_status_icon)
        title = findViewById(R.id.empty_input_status_title_text)
    }

    fun setIconAndLabelByInputInfo(inputInfo: TvInputInfo?) {
        if (inputInfo == null) return
        title.text = resources.getString(R.string.empty_input_status_title_format, inputInfo.loadLabel(context))
        icon.setImageResource(
            if (inputInfo.isPassthroughInput) R.drawable.ic_empty_input_hdmi else R.drawable.ic_empty_input_tuner)
    }
}
