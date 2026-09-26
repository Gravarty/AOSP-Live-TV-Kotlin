package com.android.tv.dvr.ui.browse

import android.app.Activity
import android.content.Context
import android.view.View
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.leanback.widget.Presenter
import com.android.tv.common.SoftPreconditions
import com.android.tv.dvr.ui.DvrUiHelper

/**
 * Abstrakter Presenter für DVR-Einträge ([com.android.tv.dvr.data.ScheduledRecording],
 * [com.android.tv.dvr.data.RecordedProgram], [com.android.tv.dvr.data.SeriesRecording]) in einer
 * [RecordingCardView], hauptsächlich in [DvrBrowseFragment].
 */
abstract class DvrItemPresenter<T>(protected val context: Context) : Presenter() {
    private val boundViewHolders = HashSet<DvrItemViewHolder>()
    // Im Original ein Feldinitialisierer (Aufruf im Konstruktor); lazy vermeidet den Zugriff
    // auf noch nicht initialisierte Felder der Unterklassen.
    private val onClickListener: View.OnClickListener by lazy { onCreateOnClickListener() }

    // Im Original protected; öffentlich, damit Unterklassen (auch in dvr/ui/playback) ihn in
    // öffentlichen Overrides zurückgeben dürfen.
    open inner class DvrItemViewHolder(view: RecordingCardView) : Presenter.ViewHolder(view) {
        fun getView(): RecordingCardView = view as RecordingCardView

        open fun onBound(item: T) {}

        open fun onUnbound() {}
    }

    final override fun onCreateViewHolder(parent: ViewGroup): Presenter.ViewHolder = onCreateDvrItemViewHolder()

    final override fun onBindViewHolder(viewHolder: Presenter.ViewHolder, item: Any?) {
        @Suppress("UNCHECKED_CAST")
        val dvrItem = item as? T
        val holder = viewHolder as? DvrItemPresenter<T>.DvrItemViewHolder
        if (holder == null || dvrItem == null) {
            SoftPreconditions.checkState(false, TAG, "Unexpected view holder or item: $item")
            return
        }
        holder.view.tag = item
        holder.view.setOnClickListener(onClickListener)
        onBindDvrItemViewHolder(holder, dvrItem)
        holder.onBound(dvrItem)
        boundViewHolders.add(holder)
    }

    @CallSuper
    override fun onUnbindViewHolder(viewHolder: Presenter.ViewHolder) {
        @Suppress("UNCHECKED_CAST")
        val holder = viewHolder as DvrItemPresenter<T>.DvrItemViewHolder
        boundViewHolders.remove(holder)
        holder.onUnbound()
        holder.view.tag = null
        holder.view.setOnClickListener(null)
    }

    /** Löst alle gebundenen ViewHolder. */
    fun unbindAllViewHolders() {
        // Beim Zerstören der Browse-Fragments ruft RecyclerView onUnbindViewHolder() nicht auf;
        // daher selbst erledigen, um Lecks zu vermeiden.
        for (viewHolder in HashSet(boundViewHolders)) {
            onUnbindViewHolder(viewHolder)
        }
    }

    /** Erzeugt einen [DvrItemViewHolder]. */
    abstract fun onCreateDvrItemViewHolder(): DvrItemViewHolder

    /** Bindet einen [DvrItemViewHolder] an einen DVR-Eintrag. */
    abstract fun onBindDvrItemViewHolder(viewHolder: DvrItemViewHolder, item: T)

    /** Erzeugt den Klick-Listener der Karten (öffnet die Detailansicht). */
    protected open fun onCreateOnClickListener(): View.OnClickListener = View.OnClickListener { view ->
        if (view is RecordingCardView) {
            DvrUiHelper.startDetailsActivity(view.context as Activity, view.tag, view.imageView, false)
        }
    }

    companion object {
        private const val TAG = "DvrItemPresenter"
    }
}
