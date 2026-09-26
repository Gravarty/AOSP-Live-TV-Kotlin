package com.android.tv.ui.sidepanel

import android.media.tv.TvTrackInfo
import android.view.KeyEvent
import com.android.tv.R
import com.android.tv.util.CaptionSettings
import java.util.Locale

/** Untertitelwahl; Fokus zeigt eine Vorschau, beim Schließen ohne Auswahl wird zurückgesetzt. */
class ClosedCaptionFragment : SideFragment<Item>(KeyEvent.KEYCODE_CAPTIONS, KeyEvent.KEYCODE_S) {

    private var resetClosedCaption = false
    private var closedCaptionOption = 0
    private var closedCaptionLanguage: String? = null
    private var closedCaptionTrackId: String? = null
    private var selectedItem: ClosedCaptionOptionItem? = null

    override fun getTitle(): String = getString(R.string.side_panel_title_closed_caption)

    override fun getItemList(): List<Item> {
        val captionSettings = mainActivity.captionSettings!!
        resetClosedCaption = true
        closedCaptionOption = captionSettings.enableOption
        closedCaptionLanguage = captionSettings.language
        closedCaptionTrackId = captionSettings.trackId

        val items = ArrayList<Item>()
        selectedItem = null
        val tracks = mainActivity.getTracks(TvTrackInfo.TYPE_SUBTITLE)
        if (!tracks.isNullOrEmpty()) {
            val selectedTrackId = if (captionSettings.isEnabled) mainActivity.getSelectedTrack(TvTrackInfo.TYPE_SUBTITLE) else null
            val off = ClosedCaptionOptionItem(null, null)
            items.add(off)
            if (selectedTrackId == null) {
                selectedItem = off
                off.setChecked(true)
                setSelectedPosition(0)
            }
            tracks.forEachIndexed { i, track ->
                val item = ClosedCaptionOptionItem(track, i)
                if (selectedTrackId == track.id) {
                    selectedItem = item
                    item.setChecked(true)
                    setSelectedPosition(i + 1)
                }
                items.add(item)
            }
        }
        if (mainActivity.hasCaptioningSettingsActivity()) {
            items.add(object : ActionItem(
                getString(R.string.closed_caption_system_settings),
                getString(R.string.closed_caption_system_settings_description),
            ) {
                override fun onSelected() = mainActivity.startSystemCaptioningSettingsActivity()

                override fun onFocused() {
                    super.onFocused()
                    // Vorschau auf die gewählte Spur zurücksetzen
                    selectedItem?.let { mainActivity.selectSubtitleTrack(it.option, it.trackId) }
                }
            })
        }
        return items
    }

    override fun onDestroyView() {
        if (resetClosedCaption) {
            mainActivity.selectSubtitleLanguage(closedCaptionOption, closedCaptionLanguage, closedCaptionTrackId)
        }
        super.onDestroyView()
    }

    private fun getLabel(track: TvTrackInfo?, trackIndex: Int?): String = when {
        track == null -> getString(R.string.closed_caption_option_item_off)
        track.language != null -> Locale(track.language).displayName
        else -> getString(R.string.closed_caption_unknown_language, trackIndex!! + 1)
    }

    private inner class ClosedCaptionOptionItem(track: TvTrackInfo?, trackIndex: Int?) : RadioButtonItem(getLabel(track, trackIndex)) {
        val option = if (track == null) CaptionSettings.OPTION_OFF else CaptionSettings.OPTION_ON
        val trackId: String? = track?.id

        override fun onSelected() {
            super.onSelected()
            selectedItem = this
            mainActivity.selectSubtitleTrack(option, trackId)
            resetClosedCaption = false
            closeFragment()
        }

        override fun onFocused() {
            super.onFocused()
            mainActivity.selectSubtitleTrack(option, trackId)
        }
    }
}
