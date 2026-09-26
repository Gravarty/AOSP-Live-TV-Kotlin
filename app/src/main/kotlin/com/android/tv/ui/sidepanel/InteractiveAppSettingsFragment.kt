package com.android.tv.ui.sidepanel

import com.android.tv.R
import com.android.tv.util.TvSettings

/** Schalter: interaktive TV-Apps ohne Rückfrage starten. */
class InteractiveAppSettingsFragment : SideFragment<Item>() {
    override fun getTitle(): String = getString(R.string.interactive_app_settings)

    override fun getItemList(): List<Item> = listOf(
        object : SwitchItem(getString(R.string.tv_iapp_on), getString(R.string.tv_iapp_off)) {
            override fun onUpdate() {
                super.onUpdate()
                setChecked(TvSettings.isTvIAppOn(requireContext()))
            }

            override fun onSelected() {
                super.onSelected()
                TvSettings.setTvIAppOn(requireContext(), isChecked)
            }
        },
    )
}
