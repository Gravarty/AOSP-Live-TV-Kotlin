package com.android.tv.ui.sidepanel

import android.media.tv.TvTrackInfo
import android.view.KeyEvent
import com.android.tv.R
import com.android.tv.util.TvTrackInfoUtils

/** Tonspurwahl; Fokus spielt die Spur zur Probe, ohne Auswahl wird zurückgesetzt. */
class MultiAudioFragment : SideFragment<Item>(KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK, KeyEvent.KEYCODE_A) {

    private var initialSelectedPosition = INVALID_POSITION
    private var selectedTrackId: String? = null
    private var focusedTrackId: String? = null

    override fun getTitle(): String = getString(R.string.side_panel_title_multi_audio)

    override fun getItemList(): List<Item> {
        val tracks = mainActivity.getTracks(TvTrackInfo.TYPE_AUDIO)
        selectedTrackId = mainActivity.getSelectedTrack(TvTrackInfo.TYPE_AUDIO)
        val items = ArrayList<Item>()
        if (tracks != null) {
            val needToShowSampleRate = TvTrackInfoUtils.needToShowSampleRate(requireActivity(), tracks)
            tracks.forEachIndexed { pos, track ->
                val item = MultiAudioOptionItem(
                    TvTrackInfoUtils.getMultiAudioString(requireActivity(), track, needToShowSampleRate), track.id)
                if (track.id == selectedTrackId) {
                    item.setChecked(true)
                    initialSelectedPosition = pos
                    focusedTrackId = track.id
                }
                items.add(item)
            }
        }
        return items
    }

    override fun onResume() {
        super.onResume()
        if (initialSelectedPosition != INVALID_POSITION) setSelectedPosition(initialSelectedPosition)
    }

    private inner class MultiAudioOptionItem(title: String, private val trackId: String) : RadioButtonItem(title) {
        override fun onSelected() {
            super.onSelected()
            selectedTrackId = trackId
            focusedTrackId = trackId
            mainActivity.selectAudioTrack(trackId)
            closeFragment()
        }

        override fun onFocused() {
            super.onFocused()
            focusedTrackId = trackId
            mainActivity.selectAudioTrack(trackId)
        }
    }

    override fun onDetach() {
        if (selectedTrackId != focusedTrackId) mainActivity.selectAudioTrack(selectedTrackId)
        super.onDetach()
    }
}
