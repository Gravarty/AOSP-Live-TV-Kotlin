package com.android.tv.common.singletons

import android.content.Context

/** Context (z. B. MainActivity), der Singletons für Views bereitstellt. */
interface HasSingletons<C> {
    fun singletons(): C

    companion object {
        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <C> get(context: Context): C = (context as HasSingletons<C>).singletons()
    }
}
