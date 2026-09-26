package com.android.tv.dvr.ui

import com.android.tv.common.SoftPreconditions

/** Speichert große Objekte, die zwischen Activities/Fragments weitergereicht werden. */
object BigArguments {
    private const val TAG = "BigArguments"
    private val bigArgumentMap = HashMap<String, Any>()

    /** Setzt das Argument. */
    @JvmStatic
    fun setArgument(name: String, value: Any?) {
        // Abweichung: value nullable wie im Java-Original (@NonNull nur als Hinweis); null wird nicht gespeichert
        if (!SoftPreconditions.checkState(value != null, TAG, "Set argument, but value is null")) return
        bigArgumentMap[name] = value!!
    }

    /** Liefert das Argument zum Namen. */
    @JvmStatic
    fun getArgument(name: String): Any? = bigArgumentMap[name]

    /** Setzt alle Argumente zurück. */
    @JvmStatic
    fun reset() = bigArgumentMap.clear()
}
