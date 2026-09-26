package com.android.tv.menu

import com.android.tv.ChannelTuner
import com.android.tv.TvOptionsManager
import com.android.tv.data.api.Channel
import com.android.tv.ui.TunableTvView

/** Aktualisiert Menüzeilen bei Kanal-, Options- und Sperränderungen. */
class MenuUpdater(
    private val menu: Menu,
    private val tvView: TunableTvView?,
    private val optionsManager: TvOptionsManager?,
) {
    private var channelTuner: ChannelTuner? = null

    private val channelTunerListener = object : ChannelTuner.Listener {
        override fun onLoadFinished() {}
        override fun onBrowsableChannelListChanged() { menu.update(ChannelsRow.ID) }
        override fun onCurrentChannelUnavailable(channel: Channel?) {}
        override fun onChannelChanged(previousChannel: Channel?, currentChannel: Channel?) { menu.update(ChannelsRow.ID) }
    }

    private val optionChangeListener = TvOptionsManager.OptionChangedListener { _, _ -> menu.update(MenuRowFactory.TvOptionsRow.ID) }

    init {
        tvView?.setOnScreenBlockedListener(object : TunableTvView.OnScreenBlockingChangedListener() {
            override fun onScreenBlockingChanged(blocked: Boolean) { menu.update(PlayControlsRow.ID) }
        })
        optionsManager?.let {
            it.setOptionChangedListener(TvOptionsManager.OPTION_CLOSED_CAPTIONS, optionChangeListener)
            it.setOptionChangedListener(TvOptionsManager.OPTION_DISPLAY_MODE, optionChangeListener)
            it.setOptionChangedListener(TvOptionsManager.OPTION_MULTI_AUDIO, optionChangeListener)
        }
    }

    fun setChannelTuner(channelTuner: ChannelTuner?) {
        this.channelTuner?.removeListener(channelTunerListener)
        this.channelTuner = channelTuner
        channelTuner?.addListener(channelTunerListener)
    }

    fun onStreamInfoChanged() { menu.update(MenuRowFactory.TvOptionsRow.ID) }

    fun release() {
        channelTuner?.removeListener(channelTunerListener)
        tvView?.setOnScreenBlockedListener(null)
        optionsManager?.let {
            it.setOptionChangedListener(TvOptionsManager.OPTION_CLOSED_CAPTIONS, null)
            it.setOptionChangedListener(TvOptionsManager.OPTION_DISPLAY_MODE, null)
            it.setOptionChangedListener(TvOptionsManager.OPTION_MULTI_AUDIO, null)
        }
    }
}
