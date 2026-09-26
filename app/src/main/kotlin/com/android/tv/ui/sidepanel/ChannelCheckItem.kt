package com.android.tv.ui.sidepanel

import android.view.View
import android.widget.TextView
import com.android.tv.R
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.OnCurrentProgramUpdatedListener
import com.android.tv.data.ProgramDataManager
import com.android.tv.data.api.Channel
import com.android.tv.data.api.Program

/** Kanal mit Häkchen, Nummer und aktueller Sendung. */
abstract class ChannelCheckItem(
    channel: Channel,
    private val channelDataManager: ChannelDataManager,
    private val programDataManager: ProgramDataManager,
) : CompoundButtonItem(channel.displayName, "") {

    var channel: Channel = channel
        private set
    private var programTitleView: TextView? = null
    private var channelNumberView: TextView? = null

    private val channelListener = object : ChannelDataManager.ChannelListener {
        override fun onChannelRemoved(channel: Channel) {}
        override fun onChannelUpdated(channel: Channel) { this@ChannelCheckItem.channel = channel }
    }

    private val onCurrentProgramUpdatedListener = OnCurrentProgramUpdatedListener { _, program -> updateProgramTitle(program) }

    override fun getResourceId() = R.layout.option_item_channel_check
    override fun getCompoundButtonId() = R.id.check_box
    override fun getTitleViewId() = R.id.channel_name
    override fun getDescriptionViewId() = R.id.program_title

    override fun onBind(view: View) {
        super.onBind(view)
        channelNumberView = view.findViewById(R.id.channel_number)
        programTitleView = view.findViewById(R.id.program_title)
        channelDataManager.addChannelListener(channel.id, channelListener)
        programDataManager.addOnCurrentProgramUpdatedListener(channel.id, onCurrentProgramUpdatedListener)
    }

    override fun onUpdate() {
        super.onUpdate()
        channelNumberView?.text = channel.displayNumber
        updateProgramTitle(programDataManager.getCurrentProgram(channel.id))
    }

    override fun onUnbind() {
        channelDataManager.removeChannelListener(channel.id, channelListener)
        programDataManager.removeOnCurrentProgramUpdatedListener(channel.id, onCurrentProgramUpdatedListener)
        programTitleView = null
        channelNumberView = null
        super.onUnbind()
    }

    override fun onSelected() = setChecked(!isChecked)

    private fun updateProgramTitle(program: Program?) {
        val view = programTitleView ?: return
        val title = program?.title
        view.text = if (title.isNullOrEmpty()) view.context.getString(R.string.no_program_information) else title
    }
}
