package com.android.tv.menu

import android.content.Context
import com.android.tv.R

/** Menüzeile mit horizontaler Kartenliste. */
open class ItemListRow(
    context: Context,
    menu: Menu,
    title: String,
    itemHeightResId: Int,
    var adapter: ItemListRowView.ItemListAdapter<*>,
) : MenuRow(context, menu, title, itemHeightResId) {

    constructor(context: Context, menu: Menu, titleResId: Int, itemHeightResId: Int, adapter: ItemListRowView.ItemListAdapter<*>) :
        this(context, menu, context.getString(titleResId), itemHeightResId, adapter)

    override fun update() = adapter.update()
    override fun isVisible() = adapter.itemCount > 0
    override fun getLayoutResId() = R.layout.item_list
    override fun getId(): String = this.javaClass.name
}
