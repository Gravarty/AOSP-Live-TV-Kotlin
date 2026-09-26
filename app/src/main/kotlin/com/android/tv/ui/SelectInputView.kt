package com.android.tv.ui

import android.content.Context
import android.media.tv.TvInputInfo
import android.media.tv.TvInputManager
import android.media.tv.TvInputManager.TvInputCallback
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.R
import com.android.tv.TvSingletons
import com.android.tv.data.api.Channel
import com.android.tv.util.TvInputManagerHelper

/**
 * Eingangsauswahl (TV + HDMI usw.). Nach kurzer Zeit ohne Taste wird der gewählte Eingang
 * übernommen. HDMI-Switch-Erkennung ist System-API: Parent-Eingänge werden wie beim
 * Original ohne Switch ausgeblendet.
 */
class SelectInputView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0,
) : VerticalGridView(context, attrs, defStyleAttr), TvTransitionManager.TransitionLayout {

    interface OnInputSelectedCallback {
        fun onTunerInputSelected()
        fun onPassthroughInputSelected(input: TvInputInfo)
    }

    private val tvInputManagerHelper: TvInputManagerHelper = TvSingletons.getSingletons(context).getTvInputManagerHelper()
    private val inputList = ArrayList<TvInputInfo>()
    private val comparator = TvInputManagerHelper.HardwareInputComparator(context, tvInputManagerHelper)
    private var currentChannel: Channel? = null
    private var callback: OnInputSelectedCallback? = null
    private val inputItemHeight = resources.getDimensionPixelSize(R.dimen.input_banner_item_height)
    private val showDurationMillis = resources.getInteger(R.integer.select_input_show_duration).toLong()
    private val rippleAnimDurationMillis = resources.getInteger(R.integer.select_input_ripple_anim_duration).toLong()
    private val textColorPrimary = resources.getColor(R.color.select_input_text_color_primary, null)
    private val textColorSecondary = resources.getColor(R.color.select_input_text_color_secondary, null)
    private val textColorDisabled = resources.getColor(R.color.select_input_text_color_disabled, null)
    private val itemViewForMeasure: View = LayoutInflater.from(context).inflate(R.layout.select_input_item, this, false)
    private var resetTransitionAlpha = false
    private var selectedInput: TvInputInfo? = null
    private var maxItemWidth = 0

    private val hideRunnable = Runnable {
        val input = selectedInput ?: return@Runnable
        if (input.isPassthroughInput) callback?.onPassthroughInputSelected(input) else callback?.onTunerInputSelected()
    }

    private val tvInputCallback = object : TvInputCallback() {
        override fun onInputAdded(inputId: String) = refresh()
        override fun onInputRemoved(inputId: String) = refresh()
        override fun onInputUpdated(inputId: String) = refresh()
        override fun onInputStateChanged(inputId: String, state: Int) = refresh()

        private fun refresh() {
            buildInputListAndNotify()
            updateSelectedPositionIfNeeded()
        }

        private fun updateSelectedPositionIfNeeded() {
            val selected = selectedInput
            if (!isFocusable || selected == null) return
            if (!isInputEnabled(selected)) {
                selectedPosition = TUNER_INPUT_POSITION
                return
            }
            val position = getInputPosition(selected.id)
            if (position != selectedPosition) selectedPosition = position
        }
    }

    init {
        adapter = InputListAdapter()
        buildInputListAndNotify()
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        scheduleHide()
        if (keyCode == KeyEvent.KEYCODE_TV_INPUT && inputList.isNotEmpty()) {
            // Zum nächsten verbundenen Eingang (sonst TV)
            val currentPosition = inputList.indexOf(selectedInput)
            var nextPosition = currentPosition
            while (true) {
                nextPosition = (nextPosition + 1) % inputList.size
                if (isInputEnabled(inputList[nextPosition])) break
                if (nextPosition == currentPosition) {
                    nextPosition = 0
                    break
                }
            }
            selectedPosition = nextPosition
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onEnterAction(fromEmptyScene: Boolean) {
        scheduleHide()
        resetTransitionAlpha = fromEmptyScene
        buildInputListAndNotify()
        tvInputManagerHelper.addCallback(tvInputCallback)
        val channel = currentChannel
        val currentInputId = if (channel != null && channel.isPassthrough) channel.inputId else null
        selectedPosition = if (currentInputId != null && !isInputEnabled(tvInputManagerHelper.getTvInputInfo(currentInputId))) {
            // Aktueller Eingang getrennt: TV auswählen
            TUNER_INPUT_POSITION
        } else {
            getInputPosition(currentInputId)
        }
        isFocusable = true
        requestFocus()
    }

    private fun getInputPosition(inputId: String?): Int =
        inputId?.let { id -> inputList.indexOfFirst { it.id == id }.takeIf { it >= 0 } } ?: TUNER_INPUT_POSITION

    override fun onExitAction() {
        tvInputManagerHelper.removeCallback(tvInputCallback)
        removeCallbacks(hideRunnable)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(maxItemWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(inputItemHeight * inputList.size, MeasureSpec.EXACTLY))
    }

    private fun scheduleHide() {
        removeCallbacks(hideRunnable)
        postDelayed(hideRunnable, showDurationMillis)
    }

    /** Ein TV-Eintrag für alle Tuner-Inputs, dazu alle sichtbaren Passthrough-Eingänge. */
    private fun buildInputListAndNotify() {
        inputList.clear()
        val inputMap = HashMap<String, TvInputInfo>()
        var foundTuner = false
        for (input in tvInputManagerHelper.getTvInputInfos(false, false)) {
            if (input.isPassthroughInput) {
                if (!input.isHidden(context)) {
                    inputList.add(input)
                    inputMap[input.id] = input
                }
            } else if (!foundTuner) {
                foundTuner = true
                inputList.add(input)
            }
        }
        // Parent-Eingang (HDMI-Port) ausblenden, wenn ein Gerät daran hängt
        for (input in inputMap.values) {
            if (input.parentId != null) inputList.remove(inputMap[input.parentId])
        }
        inputList.sortWith(comparator)

        maxItemWidth = 0
        for (input in inputList) {
            setItemViewText(itemViewForMeasure, input)
            itemViewForMeasure.measure(0, 0)
            maxItemWidth = maxOf(maxItemWidth, itemViewForMeasure.measuredWidth)
        }
        adapter?.notifyDataSetChanged()
    }

    private fun setItemViewText(v: View, input: TvInputInfo) {
        val inputLabelView = v.findViewById<TextView>(R.id.input_label)
        val secondaryInputLabelView = v.findViewById<TextView>(R.id.secondary_input_label)
        val customLabel = input.loadCustomLabel(context)
        val label = input.loadLabel(context)
        val primary: CharSequence? = if (customLabel.isNullOrEmpty() || customLabel == label) label else customLabel
        if (input.isPassthroughInput) inputLabelView.text = primary else inputLabelView.setText(R.string.input_long_label_for_tuner)
        if (customLabel.isNullOrEmpty() || customLabel == label) {
            secondaryInputLabelView.visibility = View.GONE
        } else {
            secondaryInputLabelView.text = label
            secondaryInputLabelView.visibility = View.VISIBLE
        }
    }

    private fun isInputEnabled(input: TvInputInfo?): Boolean =
        tvInputManagerHelper.getInputState(input) != TvInputManager.INPUT_STATE_DISCONNECTED

    fun setOnInputSelectedCallback(callback: OnInputSelectedCallback?) { this.callback = callback }

    fun setCurrentChannel(channel: Channel?) { currentChannel = channel }

    private inner class InputListAdapter : RecyclerView.Adapter<InputListAdapter.ViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.select_input_item, parent, false))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val input = inputList[position]
            if (input.isPassthroughInput) {
                val enabled = isInputEnabled(input)
                holder.itemView.isFocusable = enabled
                holder.inputLabelView.setTextColor(if (enabled) textColorPrimary else textColorDisabled)
                holder.secondaryInputLabelView.setTextColor(if (enabled) textColorSecondary else textColorDisabled)
                setItemViewText(holder.itemView, input)
            } else {
                holder.itemView.isFocusable = true
                holder.inputLabelView.setTextColor(textColorPrimary)
                holder.inputLabelView.setText(R.string.input_long_label_for_tuner)
                holder.secondaryInputLabelView.visibility = View.GONE
            }
            // Bugfix: aktuelle Position statt der beim Binden gemerkten verwenden
            holder.itemView.setOnClickListener {
                val pos = holder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
                selectedInput = inputList[pos]
                // Ripple zu Ende laufen lassen
                isFocusable = false
                removeCallbacks(hideRunnable)
                postDelayed(hideRunnable, rippleAnimDurationMillis)
            }
            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val pos = holder.bindingAdapterPosition
                if (hasFocus && pos != RecyclerView.NO_POSITION) selectedInput = inputList[pos]
            }
            if (resetTransitionAlpha) ViewUtils.setTransitionAlpha(holder.itemView, 1f)
        }

        override fun getItemCount() = inputList.size

        inner class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val inputLabelView: TextView = v.findViewById(R.id.input_label)
            val secondaryInputLabelView: TextView = v.findViewById(R.id.secondary_input_label)
        }
    }

    companion object {
        private const val TUNER_INPUT_POSITION = 0
    }
}
