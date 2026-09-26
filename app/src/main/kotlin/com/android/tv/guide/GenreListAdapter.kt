package com.android.tv.guide

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.R
import com.android.tv.data.GenreItems

/** Genre-Seitenleiste; Fokus auf ein Genre filtert die Kanalliste. */
internal class GenreListAdapter(
    private val context: Context,
    private val programManager: ProgramManager,
    private val programGuide: ProgramGuide,
) : RecyclerView.Adapter<GenreListAdapter.GenreRowHolder>() {

    private var genreLabels: Array<String> = emptyArray()

    init {
        programManager.addListener(object : ProgramManager.ListenerAdapter() {
            override fun onGenresUpdated() {
                genreLabels = GenreItems.getLabels(context)
                notifyDataSetChanged()
            }
        })
    }

    override fun getItemCount() = programManager.filteredGenreIds.size
    override fun getItemViewType(position: Int) = R.layout.program_guide_side_panel_row

    override fun onBindViewHolder(holder: GenreRowHolder, position: Int) {
        val genreId = programManager.filteredGenreIds[position]
        holder.onBind(genreId, genreLabels.getOrElse(genreId) { "" })
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GenreRowHolder {
        val itemView = LayoutInflater.from(parent.context).inflate(viewType, parent, false)
        itemView.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            // Ohne Animation in den aktuellen Zustand springen (sonst flackert es beim Scrollen)
            override fun onViewAttachedToWindow(view: View) { view.stateListAnimator?.jumpToCurrentState() }
            override fun onViewDetachedFromWindow(view: View) {}
        })
        return GenreRowHolder(itemView, programGuide)
    }

    class GenreRowHolder(itemView: View, private val programGuide: ProgramGuide) :
        RecyclerView.ViewHolder(itemView), View.OnFocusChangeListener {
        private var genreId = 0

        fun onBind(genreId: Int, genreLabel: String) {
            this.genreId = genreId
            (itemView as TextView).text = genreLabel
            itemView.onFocusChangeListener = this
        }

        override fun onFocusChange(view: View, hasFocus: Boolean) {
            if (hasFocus) programGuide.requestGenreChange(genreId)
        }
    }
}
