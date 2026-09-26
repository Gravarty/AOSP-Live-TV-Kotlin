package com.android.tv.menu

import android.content.Context

/** Eine Zeile im Menü (Wiedergabe, Kanäle, Partner, Optionen). */
abstract class MenuRow(
    protected val context: Context,
    val menu: Menu,
    val title: String,
    heightResId: Int,
) {
    constructor(context: Context, menu: Menu, titleResId: Int, heightResId: Int) :
        this(context, menu, context.getString(titleResId), heightResId)

    val height: Int = context.resources.getDimensionPixelSize(heightResId)
    var menuRowView: MenuRowView? = null
    var isReselected = false

    abstract fun update()
    open fun isVisible() = true
    open fun release() {}
    abstract fun getLayoutResId(): Int
    abstract fun getId(): String
    open fun onRecentChannelsChanged() {}
    open fun hideTitleWhenSelected() = false
}
