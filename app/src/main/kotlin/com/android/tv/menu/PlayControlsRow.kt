package com.android.tv.menu

import android.content.Context
import com.android.tv.R
import com.android.tv.TimeShiftManager
import com.android.tv.ui.TunableTvView

/** Wiedergabe-Zeile (Timeshift); nur sichtbar, wenn Timeshift verfügbar ist. */
class PlayControlsRow(
    context: Context,
    val tvView: TunableTvView,
    menu: Menu,
    val timeShiftManager: TimeShiftManager,
) : MenuRow(context, menu, R.string.menu_title_play_controls, R.dimen.play_controls_height) {

    override fun update() = (menuRowView as PlayControlsRowView).update()
    override fun getLayoutResId() = R.layout.play_controls
    override fun getId(): String = ID
    override fun isVisible() = timeShiftManager.isAvailable
    override fun hideTitleWhenSelected() = true

    companion object {
        val ID: String = PlayControlsRow::class.java.name
    }
}
