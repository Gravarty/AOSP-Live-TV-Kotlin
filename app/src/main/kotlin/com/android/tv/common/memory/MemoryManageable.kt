package com.android.tv.common.memory

/** Objekte, die bei Speicherdruck Ressourcen freigeben. */
interface MemoryManageable {
    fun performTrimMemory(level: Int)
}
