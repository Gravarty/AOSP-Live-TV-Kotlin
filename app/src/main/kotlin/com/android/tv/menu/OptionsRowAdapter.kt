package com.android.tv.menu

import android.content.Context
import android.view.View
import com.android.tv.R
import com.android.tv.common.customization.CustomAction

/** Kartenzeile aus MenuActions; Klick führt die Aktion nach dem Ripple aus. */
abstract class OptionsRowAdapter(context: Context) : ItemListRowView.ItemListAdapter<MenuAction>(context) {
    private var actionList: List<MenuAction>? = null

    private val menuActionOnClickListener = View.OnClickListener { view ->
        val action = view.tag as MenuAction
        view.post { executeAction(action.type) }
    }

    override fun update() {
        val list = actionList
        if (list == null) {
            actionList = createActions().also { setItemList(it) }
        } else {
            updateActions()
        }
    }

    override fun getLayoutResId(viewType: Int) = R.layout.menu_card_action

    protected abstract fun createActions(): List<MenuAction>
    protected abstract fun updateActions()
    protected abstract fun executeAction(type: Int)

    protected fun getAction(position: Int): MenuAction = actionList!![position]

    override fun onBindViewHolder(viewHolder: MyViewHolder, position: Int) {
        super.onBindViewHolder(viewHolder, position)
        viewHolder.itemView.tag = itemList[position]
        viewHolder.itemView.setOnClickListener(menuActionOnClickListener)
    }

    override fun getItemViewType(position: Int) = actionList!![position].type
}

/** Optionen-Zeile mit zusätzlichen OEM-Aktionen (negative Typen). */
abstract class CustomizableOptionsRowAdapter(context: Context, protected val customActions: List<CustomAction>?) :
    OptionsRowAdapter(context) {

    protected abstract fun createBaseActions(): List<MenuAction>
    protected abstract fun executeBaseAction(type: Int)

    override fun createActions(): List<MenuAction> {
        val actions = ArrayList(createBaseActions())
        var position = 0
        customActions?.forEachIndexed { i, customAction ->
            val action = MenuAction(customAction.title, -(i + 1), customAction.iconDrawable)
            if (customAction.isFront) actions.add(position++, action) else actions.add(action)
        }
        return actions
    }

    override fun executeAction(type: Int) {
        if (type < 0) mainActivity.startActivitySafe(customActions!![-(type + 1)].intent) else executeBaseAction(type)
    }
}

/** Partner-Zeile: nur OEM-Aktionen. */
class PartnerOptionsRowAdapter(context: Context, customActions: List<CustomAction>) :
    CustomizableOptionsRowAdapter(context, customActions) {
    override fun createBaseActions(): List<MenuAction> = emptyList()
    override fun executeBaseAction(type: Int) {}
    override fun updateActions() {}
}
