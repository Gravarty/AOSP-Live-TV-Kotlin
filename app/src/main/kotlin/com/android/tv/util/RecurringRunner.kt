package com.android.tv.util

import android.content.Context
import android.os.Handler
import android.util.Log
import com.android.tv.common.SoftPreconditions
import com.android.tv.common.util.SharedPreferencesUtils
import java.util.Date
import java.util.concurrent.Executors

/** Führt eine Aufgabe periodisch aus; der nächste Termin überlebt Neustarts (SharedPreferences). */
class RecurringRunner(
    context: Context,
    private val intervalMs: Long,
    private val runnable: Runnable,
    private val onStopRunnable: Runnable?,
) {
    private val context = context.applicationContext
    private val handler = Handler(this.context.mainLooper)
    private val name: String = runnable.javaClass.canonicalName ?: runnable.javaClass.name
    private var running = false

    /** AsyncTask → Hintergrund-Thread + Main-Handler (Prefs lesen kann auf Platte gehen). */
    @JvmOverloads
    fun start(resetNextRunTime: Boolean = false) {
        SoftPreconditions.checkState(!running, TAG, "$name start is called twice.")
        if (running) return
        running = true
        if (resetNextRunTime) resetNextRunTime()
        EXECUTOR.execute {
            val next = getNextRunTime()
            handler.post { postAt(next) }
        }
    }

    fun stop() {
        running = false
        handler.removeCallbacksAndMessages(null)
        onStopRunnable?.run()
    }

    private fun postAt(next: Long) {
        if (!running) return
        // Auch Termine in der Vergangenheit sofort ausführen
        val delay = maxOf(next - System.currentTimeMillis(), 0)
        val posted = handler.postDelayed({
            try {
                runnable.run()
            } catch (e: Exception) {
                Log.w(TAG, "Error running $name", e)
            }
            postAt(resetNextRunTime())
        }, delay)
        if (!posted) Log.w(TAG, "Scheduling a future run of $name at ${Date(next)}failed")
    }

    private fun prefs() = context.getSharedPreferences(SharedPreferencesUtils.SHARED_PREF_RECURRING_RUNNER, Context.MODE_PRIVATE)

    private fun getNextRunTime(): Long {
        var next = prefs().getLong(name, System.currentTimeMillis())
        if (next > System.currentTimeMillis() + intervalMs) next = resetNextRunTime()
        return next
    }

    private fun resetNextRunTime(): Long {
        val next = System.currentTimeMillis() + intervalMs
        prefs().edit().putLong(name, next).apply()
        return next
    }

    companion object {
        private const val TAG = "RecurringRunner"
        private val EXECUTOR = Executors.newCachedThreadPool()
    }
}
