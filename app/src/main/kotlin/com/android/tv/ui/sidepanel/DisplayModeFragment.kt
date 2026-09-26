package com.android.tv.ui.sidepanel

import android.content.Context
import com.android.tv.R
import com.android.tv.data.DisplayMode
import com.android.tv.ui.TvViewUiManager

/** Bildformat (Normal/Voll/Zoom) mit Vorschau beim Fokussieren. */
class DisplayModeFragment : SideFragment<Item>() {
    private lateinit var tvViewUiManager: TvViewUiManager

    override fun getTitle(): String = getString(R.string.side_panel_title_display_mode)

    override fun getItemList(): List<Item> = (0 until DisplayMode.SIZE_OF_RATIO_TYPES).map { DisplayModeRadioItem(it) }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        tvViewUiManager = mainActivity.tvViewUiManager
    }

    override fun onResume() {
        super.onResume()
        setSelectedPosition(tvViewUiManager.getDisplayMode())
    }

    override fun onDetach() {
        super.onDetach()
        tvViewUiManager.restoreDisplayMode(true)
    }

    private inner class DisplayModeRadioItem(private val displayMode: Int) :
        RadioButtonItem(DisplayMode.getLabel(displayMode, requireActivity())) {

        override fun onUpdate() {
            super.onUpdate()
            setEnabled(tvViewUiManager.isDisplayModeAvailable(displayMode))
            setChecked(displayMode == tvViewUiManager.getDisplayMode())
        }

        override fun onSelected() {
            super.onSelected()
            tvViewUiManager.setDisplayMode(displayMode, true, true)
            closeFragment()
        }

        override fun onFocused() {
            super.onFocused()
            tvViewUiManager.setDisplayMode(displayMode, false, true)
        }
    }
}
