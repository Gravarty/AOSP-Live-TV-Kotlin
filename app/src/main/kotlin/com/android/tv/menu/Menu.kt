package com.android.tv.menu

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
import android.content.Context
import android.view.View
import android.view.accessibility.AccessibilityManager.AccessibilityStateChangeListener
import androidx.leanback.widget.HorizontalGridView
import com.android.tv.ChannelTuner
import com.android.tv.R
import com.android.tv.TvOptionsManager
import com.android.tv.ui.TunableTvView
import com.android.tv.ui.hideable.AutoHideScheduler
import com.android.tv.util.ViewCache

/** Das TV-Menü (Zeilen: Wiedergabe, Kanäle, Partner, Optionen) mit Auto-Ausblenden. */
class Menu(
    private val context: Context,
    tvView: TunableTvView?,
    optionsManager: TvOptionsManager?,
    private val menuView: IMenuView,
    menuRowFactory: MenuRowFactory,
    private val onMenuVisibilityChangeListener: OnMenuVisibilityChangeListener?,
) : AccessibilityStateChangeListener {

    fun interface OnMenuVisibilityChangeListener {
        fun onMenuVisibilityChange(visible: Boolean)
    }

    private val showDurationMillis = context.resources.getInteger(R.integer.menu_show_duration).toLong()
    private val menuUpdater = MenuUpdater(this, tvView, optionsManager)
    private val menuRows = ArrayList<MenuRow>()
    private val showAnimator: Animator = AnimatorInflater.loadAnimator(context, R.animator.menu_enter).apply { setTarget(menuView) }
    private val hideAnimator: Animator = AnimatorInflater.loadAnimator(context, R.animator.menu_exit).apply {
        addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) = hideInternal()
        })
        setTarget(menuView)
    }
    private var keepVisible = false
    private val autoHideScheduler: AutoHideScheduler

    init {
        addMenuRow(menuRowFactory.createMenuRow(this, PlayControlsRow::class.java))
        addMenuRow(menuRowFactory.createMenuRow(this, ChannelsRow::class.java))
        addMenuRow(menuRowFactory.createMenuRow(this, MenuRowFactory.PartnerRow::class.java))
        addMenuRow(menuRowFactory.createMenuRow(this, MenuRowFactory.TvOptionsRow::class.java))
        menuView.setMenuRows(menuRows)
        autoHideScheduler = AutoHideScheduler(context) { hide(true) }
    }

    fun setChannelTuner(channelTuner: ChannelTuner?) = menuUpdater.setChannelTuner(channelTuner)

    private fun addMenuRow(row: MenuRow?) {
        if (row != null) menuRows.add(row)
    }

    fun release() {
        menuUpdater.release()
        menuRows.forEach { it.release() }
        autoHideScheduler.cancel()
    }

    /** Menü-Karten vorab erzeugen (Lazy-Init). */
    fun preloadItemViews() {
        val fakeParent = HorizontalGridView(context)
        for ((id, count) in PRELOAD_VIEW_IDS) ViewCache.getInstance().putView(context, id, fakeParent, count)
    }

    fun show(reason: Int) {
        if (hideAnimator.isStarted) hideAnimator.end()
        onMenuVisibilityChangeListener?.onMenuVisibilityChange(true)
        menuView.onShow(reason, ROW_ID_FOR_REASON[reason]) { if (isActive) showAnimator.start() }
        scheduleHide()
    }

    fun hide(withAnimation: Boolean) {
        if (showAnimator.isStarted) showAnimator.cancel()
        if (!isActive) return
        autoHideScheduler.cancel()
        when {
            withAnimation -> if (!hideAnimator.isStarted) hideAnimator.start()
            hideAnimator.isStarted -> hideAnimator.end()
            else -> hideInternal()
        }
    }

    private fun hideInternal() {
        menuView.onHide()
        onMenuVisibilityChangeListener?.onMenuVisibilityChange(false)
    }

    fun scheduleHide() = autoHideScheduler.schedule(showDurationMillis)

    /** Menü bleibt sichtbar (z. B. während Timeshift-Pause). */
    fun setKeepVisible(keepVisible: Boolean) {
        this.keepVisible = keepVisible
        if (keepVisible) autoHideScheduler.cancel() else if (isActive) scheduleHide()
    }

    internal val isHideScheduled: Boolean get() = autoHideScheduler.isScheduled

    val isActive: Boolean get() = menuView.isVisible() && !hideAnimator.isStarted

    fun update(): Boolean = menuView.update(isActive)
    fun update(rowId: String): Boolean = menuView.update(rowId, isActive)

    fun onRecentChannelsChanged() = menuRows.forEach { it.onRecentChannelsChanged() }
    fun onStreamInfoChanged() = menuUpdater.onStreamInfoChanged()

    override fun onAccessibilityStateChanged(enabled: Boolean) = autoHideScheduler.onAccessibilityStateChanged(enabled)

    companion object {
        const val REASON_NONE = 0
        const val REASON_GUIDE = 1
        const val REASON_PLAY_CONTROLS_PLAY = 2
        const val REASON_PLAY_CONTROLS_PAUSE = 3
        const val REASON_PLAY_CONTROLS_PLAY_PAUSE = 4
        const val REASON_PLAY_CONTROLS_REWIND = 5
        const val REASON_PLAY_CONTROLS_FAST_FORWARD = 6
        const val REASON_PLAY_CONTROLS_JUMP_TO_PREVIOUS = 7
        const val REASON_PLAY_CONTROLS_JUMP_TO_NEXT = 8

        private val ROW_ID_FOR_REASON: List<String?> = listOf(
            null, ChannelsRow.ID,
            PlayControlsRow.ID, PlayControlsRow.ID, PlayControlsRow.ID, PlayControlsRow.ID,
            PlayControlsRow.ID, PlayControlsRow.ID, PlayControlsRow.ID,
        )

        private val PRELOAD_VIEW_IDS = mapOf(
            R.layout.menu_card_guide to 1,
            R.layout.menu_card_setup to 1,
            R.layout.menu_card_dvr to 1,
            R.layout.menu_card_app_link to 1,
            R.layout.menu_card_channel to ChannelsRow.MAX_COUNT_FOR_RECENT_CHANNELS,
            R.layout.menu_card_action to 7,
        )
    }
}
