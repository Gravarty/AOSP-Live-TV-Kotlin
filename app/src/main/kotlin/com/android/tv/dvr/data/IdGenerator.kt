package com.android.tv.dvr.data

import java.util.concurrent.atomic.AtomicLong

/** Fortlaufende IDs für Aufnahmen/Serien (startet nach der höchsten gespeicherten ID). */
class IdGenerator {
    private val maxId = AtomicLong(0)

    fun setMaxId(maxId: Long) = this.maxId.set(maxId)
    fun newId(): Long = maxId.incrementAndGet()

    companion object {
        @JvmField val SCHEDULED_RECORDING = IdGenerator()
        @JvmField val SERIES_RECORDING = IdGenerator()
    }
}
