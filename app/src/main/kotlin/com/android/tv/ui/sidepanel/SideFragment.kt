package com.android.tv.ui.sidepanel

import android.content.Context
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.leanback.widget.VerticalGridView
import androidx.recyclerview.widget.RecyclerView
import com.android.tv.MainActivity
import com.android.tv.R
import com.android.tv.data.ChannelDataManager
import com.android.tv.data.ProgramDataManager
import com.android.tv.util.ViewCache

/**
 * Basis einer Seitenleiste (Liste von Items). Framework-Fragment → AndroidX.
 * Entfällt: Debug-Tasten zum Schließen (nur mit Entwickleroptionen im ENG-Build).
 */
abstract class SideFragment<T : Item> @JvmOverloads constructor(
    private val hideKey: Int = KeyEvent.KEYCODE_UNKNOWN,
    @Suppress("unused") private val debugHideKey: Int = KeyEvent.KEYCODE_UNKNOWN,
) : Fragment() {

    fun interface SideFragmentListener {
        fun onSideFragmentViewDestroyed()
    }

    private var listView: VerticalGridView? = null
    private lateinit var adapter: ItemAdapter<T>
    private var listener: SideFragmentListener? = null
    protected lateinit var channelDataManager: ChannelDataManager
        private set
    protected lateinit var programDataManager: ProgramDataManager
        private set

    override fun onAttach(context: Context) {
        super.onAttach(context)
        channelDataManager = mainActivity.channelDataManager
        programDataManager = mainActivity.programDataManager
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = ViewCache.getInstance().getOrCreateView(inflater, getFragmentLayoutResourceId(), container)
        view.findViewById<TextView>(R.id.side_panel_title).text = getTitle()
        val list = view.findViewById<VerticalGridView>(R.id.side_panel_list)
        listView = list // vor getItemList(): Unterklassen setzen dort die Auswahl
        list.setRecycledViewPool(recycledViewPool)
        adapter = ItemAdapter(inflater, getItemList())
        list.adapter = adapter
        list.requestFocus()
        return view
    }

    fun isHideKeyForThisPanel(keyCode: Int): Boolean = hideKey != KeyEvent.KEYCODE_UNKNOWN && hideKey == keyCode

    override fun onDestroyView() {
        super.onDestroyView()
        listView?.swapAdapter(null, true)
        listener?.onSideFragmentViewDestroyed()
    }

    fun setListener(listener: SideFragmentListener?) { this.listener = listener }

    protected fun setSelectedPosition(position: Int) { listView?.selectedPosition = position }
    protected fun getSelectedPosition(): Int = listView?.selectedPosition ?: INVALID_POSITION

    fun setItems(items: List<T>) = adapter.reset(items)

    protected fun closeFragment() = mainActivity.overlayManager.sideFragmentManager.popSideFragment()

    protected val mainActivity: MainActivity get() = requireActivity() as MainActivity

    protected fun notifyDataSetChanged() = adapter.notifyDataSetChanged()

    protected open fun getFragmentLayoutResourceId() = R.layout.option_fragment
    protected abstract fun getTitle(): String
    protected abstract fun getItemList(): List<T>

    private class ItemAdapter<T : Item>(private val layoutInflater: LayoutInflater, private var items: List<T>?) :
        RecyclerView.Adapter<ViewHolder>() {

        fun reset(items: List<T>) {
            this.items = items
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            ViewHolder(ViewCache.getInstance().getOrCreateView(layoutInflater, viewType, parent))

        override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.onBind(this, getItem(position))
        override fun onViewRecycled(holder: ViewHolder) = holder.onUnbind()
        override fun getItemViewType(position: Int) = getItem(position).getResourceId()
        override fun getItemCount() = items?.size ?: 0

        private fun getItem(position: Int): T = items!![position]

        /** Andere Radio-Buttons derselben zusammenhängenden Gruppe abwählen. */
        fun clearRadioGroup(item: Item) {
            val list = items ?: return
            val position = list.indexOf(item)
            for (i in position - 1 downTo 0) (list[i] as? RadioButtonItem)?.setChecked(false) ?: break
            for (i in position + 1 until list.size) (list[i] as? RadioButtonItem)?.setChecked(false) ?: break
        }
    }

    private class ViewHolder(view: View) : RecyclerView.ViewHolder(view), View.OnClickListener, View.OnFocusChangeListener {
        private var adapter: ItemAdapter<*>? = null
        var item: Item? = null

        init {
            itemView.setOnClickListener(this)
            itemView.onFocusChangeListener = this
        }

        fun onBind(adapter: ItemAdapter<*>, item: Item) {
            this.adapter = adapter
            this.item = item
            item.onBind(itemView)
            item.onUpdate()
        }

        fun onUnbind() {
            item?.onUnbind()
            item = null
            adapter = null
        }

        override fun onClick(view: View) {
            val current = item ?: return
            if (current is RadioButtonItem) adapter?.clearRadioGroup(current)
            if (view.background is RippleDrawable) {
                // Ripple zu Ende laufen lassen
                view.postDelayed({ item?.onSelected() }, view.resources.getInteger(R.integer.side_panel_ripple_anim_duration).toLong())
            } else {
                current.onSelected()
            }
        }

        override fun onFocusChange(view: View, focusGained: Boolean) {
            if (focusGained) item?.onFocused()
        }
    }

    companion object {
        const val INVALID_POSITION = -1
        private const val PRELOAD_VIEW_SIZE = 7
        private val PRELOAD_VIEW_IDS = intArrayOf(
            R.layout.option_item_radio_button, R.layout.option_item_channel_lock, R.layout.option_item_check_box,
            R.layout.option_item_channel_check, R.layout.option_item_action,
        )
        private val recycledViewPool = RecyclerView.RecycledViewPool()

        @JvmStatic
        fun preloadItemViews(context: Context) {
            ViewCache.getInstance().putView(context, R.layout.option_fragment, FrameLayout(context), 1)
            val fakeParent = VerticalGridView(context)
            for (id in PRELOAD_VIEW_IDS) {
                recycledViewPool.setMaxRecycledViews(id, PRELOAD_VIEW_SIZE)
                ViewCache.getInstance().putView(context, id, fakeParent, PRELOAD_VIEW_SIZE)
            }
        }

        @JvmStatic
        fun releaseRecycledViewPool() = recycledViewPool.clear()
    }
}
