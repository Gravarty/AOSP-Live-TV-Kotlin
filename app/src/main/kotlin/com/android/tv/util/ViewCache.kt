package com.android.tv.util

import android.content.Context
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

/** Vorab aufgeblasene Views je Layout-ID (beschleunigt das erste Öffnen von Menüs/Seitenleisten). */
class ViewCache private constructor() {
    private val views = SparseArray<ArrayList<View>>()

    val isEmpty: Boolean get() = views.size() == 0

    fun putView(resId: Int, view: View) {
        (views.get(resId) ?: ArrayList<View>().also { views.put(resId, it) }).add(view)
    }

    fun putView(context: Context, resId: Int, fakeParent: ViewGroup, num: Int) {
        val inflater = LayoutInflater.from(context)
        val list = views.get(resId) ?: ArrayList<View>().also { views.put(resId, it) }
        repeat(num) { list.add(inflater.inflate(resId, fakeParent, false)) }
    }

    fun getView(resId: Int): View? {
        val list = views.get(resId)
        if (list.isNullOrEmpty()) return null
        val view = list.removeAt(list.size - 1)
        if (list.isEmpty()) views.remove(resId)
        return view
    }

    fun getOrCreateView(inflater: LayoutInflater, resId: Int, container: ViewGroup?): View =
        getView(resId) ?: inflater.inflate(resId, container, false)

    fun clear() = views.clear()

    companion object {
        private val instance = ViewCache()

        @JvmStatic
        fun getInstance(): ViewCache = instance
    }
}
