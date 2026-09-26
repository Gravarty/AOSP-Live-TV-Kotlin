package com.android.tv.menu

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.leanback.widget.HorizontalGridView
import androidx.leanback.widget.OnChildSelectedListener
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.util.ViewCache

/** Ansicht einer Kartenzeile (horizontale Liste). */
class ItemListRowView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0, defStyleRes: Int = 0,
) : MenuRowView(context, attrs, defStyleAttr, defStyleRes), OnChildSelectedListener {

    interface CardView<T> {
        fun onBind(item: T, selected: Boolean)
        fun onRecycled()
        fun onSelected()
        fun onDeselected()
        fun requestFocusWithAccessibility(): Boolean
    }

    private lateinit var listView: HorizontalGridView
    internal var selectedCard: CardView<*>? = null

    override fun onFinishInflate() {
        super.onFinishInflate()
        listView = contentsView as HorizontalGridView
        // Keine Animation bei Datenänderung
        listView.itemAnimator = null
    }

    override fun getContentsViewId() = R.id.list_view

    override fun onBind(row: MenuRow) {
        super.onBind(row)
        val adapter = (row as ItemListRow).adapter
        adapter.itemListView = this
        listView.setOnChildSelectedListener(this)
        listView.adapter = adapter
    }

    override fun initialize(reason: Int) {
        super.initialize(reason)
        setInitialFocusView(listView)
        listView.selectedPosition = (listView.adapter as ItemListAdapter<*>).getInitialPosition()
    }

    override fun onChildSelected(parent: ViewGroup, child: View?, position: Int, id: Long) {
        if (selectedCard === child) return
        selectedCard?.onDeselected()
        selectedCard = child as CardView<*>?
        selectedCard?.onSelected()
    }

    override fun requestChildFocus() {
        selectedCard?.requestFocusWithAccessibility()
    }

    abstract class ItemListAdapter<T>(context: Context) : RecyclerView.Adapter<ItemListAdapter.MyViewHolder>() {
        protected val mainActivity: MainActivity = context as MainActivity
        private val layoutInflater = LayoutInflater.from(context)
        /** Veränderbar: ChannelsRowAdapter pflegt die Liste direkt (wie im Original). */
        var itemList: MutableList<T> = ArrayList()
            private set
        internal var itemListView: ItemListRowView? = null

        abstract fun update()
        protected abstract fun getLayoutResId(viewType: Int): Int
        open fun release() {}
        open fun getInitialPosition() = 0

        /** Liste setzen und nur geänderte Bereiche melden. */
        protected fun setItemList(itemList: List<T>) {
            val oldSize = this.itemList.size
            val newSize = itemList.size
            this.itemList = itemList as? MutableList<T> ?: itemList.toMutableList()
            when {
                oldSize > newSize -> {
                    notifyItemRangeChanged(0, newSize)
                    notifyItemRangeRemoved(newSize, oldSize - newSize)
                }
                oldSize < newSize -> {
                    notifyItemRangeChanged(0, oldSize)
                    notifyItemRangeInserted(oldSize, newSize - oldSize)
                }
                else -> notifyItemRangeChanged(0, oldSize)
            }
        }

        override fun getItemViewType(position: Int) = 0
        override fun getItemCount() = itemList.size
        protected fun getItemPosition(item: T) = itemList.indexOf(item)
        protected fun containsItem(item: T) = itemList.contains(item)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            MyViewHolder(ViewCache.getInstance().getOrCreateView(layoutInflater, getLayoutResId(viewType), parent))

        @Suppress("UNCHECKED_CAST")
        override fun onBindViewHolder(viewHolder: MyViewHolder, position: Int) {
            val cardView = viewHolder.itemView as CardView<T>
            cardView.onBind(itemList[position], cardView == itemListView?.selectedCard)
        }

        override fun onViewRecycled(viewHolder: MyViewHolder) {
            super.onViewRecycled(viewHolder)
            (viewHolder.itemView as CardView<*>).onRecycled()
        }

        class MyViewHolder(view: View) : RecyclerView.ViewHolder(view)
    }
}
