package com.android.tv.dvr.provider

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.android.tv.common.concurrent.NamedThreadFactory
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.DvrContract.Schedules
import com.android.tv.dvr.provider.DvrContract.SeriesRecordings
import java.util.concurrent.Executors
import java.util.concurrent.Future

/**
 * DB-Aufgaben auf einem eigenen Thread; Ergebnis auf dem Main-Thread.
 * Guava ListenableFuture/FutureCallback → Future + einfacher Callback.
 * Bugfix: isCancelled() konnte vor der Zuweisung des Futures laufen (NPE).
 */
abstract class DvrDbFuture<P, R> private constructor(internal val dbHelper: DvrDatabaseHelper) {

    /** Ersatz für Guavas FutureCallback. */
    interface Callback<R> {
        fun onSuccess(result: R?)
        fun onFailure(t: Throwable)
    }

    @Volatile private var future: Future<*>? = null

    fun executeOnDbThread(callback: Callback<R>?, vararg params: P): Future<*> {
        val f = DB_EXECUTOR.submit {
            try {
                val result = dbHelperInBackground(*params)
                if (!isCancelled) MAIN.post { callback?.onSuccess(result) }
            } catch (t: Throwable) {
                MAIN.post { callback?.onFailure(t) }
            }
        }
        future = f
        return f
    }

    protected abstract fun dbHelperInBackground(vararg params: P): R?

    val isCancelled: Boolean get() = future?.isCancelled == true

    fun cancel(mayInterrupt: Boolean) { future?.cancel(mayInterrupt) }

    class AddScheduleFuture(db: DvrDatabaseHelper) : DvrDbFuture<ScheduledRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: ScheduledRecording) = dbHelper.insertSchedules(*params)
    }

    class UpdateScheduleFuture(db: DvrDatabaseHelper) : DvrDbFuture<ScheduledRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: ScheduledRecording) = dbHelper.updateSchedules(*params)
    }

    class DeleteScheduleFuture(db: DvrDatabaseHelper) : DvrDbFuture<ScheduledRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: ScheduledRecording) = dbHelper.deleteSchedules(*params)
    }

    class DvrQueryScheduleFuture(db: DvrDatabaseHelper) : DvrDbFuture<Unit, List<ScheduledRecording>>(db) {
        override fun dbHelperInBackground(vararg params: Unit): List<ScheduledRecording>? {
            if (isCancelled) return null
            val list = ArrayList<ScheduledRecording>()
            if (DvrDatabaseHelper.START_EARLY_END_LATE_ENABLED) {
                dbHelper.query(Schedules.TABLE_NAME, ScheduledRecording.PROJECTION_WITH_TIME_OFFSET).use { c ->
                    while (c.moveToNext() && !isCancelled) list.add(ScheduledRecording.fromCursorWithTimeOffset(c))
                }
            } else {
                dbHelper.query(Schedules.TABLE_NAME, ScheduledRecording.PROJECTION).use { c ->
                    while (c.moveToNext() && !isCancelled) list.add(ScheduledRecording.fromCursor(c))
                }
            }
            return list
        }
    }

    class AddSeriesRecordingFuture(db: DvrDatabaseHelper) : DvrDbFuture<SeriesRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: SeriesRecording) = dbHelper.insertSeriesRecordings(*params)
    }

    class UpdateSeriesRecordingFuture(db: DvrDatabaseHelper) : DvrDbFuture<SeriesRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: SeriesRecording) = dbHelper.updateSeriesRecordings(*params)
    }

    class DeleteSeriesRecordingFuture(db: DvrDatabaseHelper) : DvrDbFuture<SeriesRecording, Unit>(db) {
        override fun dbHelperInBackground(vararg params: SeriesRecording) = dbHelper.deleteSeriesRecordings(*params)
    }

    class DvrQuerySeriesRecordingFuture(db: DvrDatabaseHelper) : DvrDbFuture<Unit, List<SeriesRecording>>(db) {
        override fun dbHelperInBackground(vararg params: Unit): List<SeriesRecording>? {
            if (isCancelled) return null
            val list = ArrayList<SeriesRecording>()
            try {
                dbHelper.query(SeriesRecordings.TABLE_NAME, SeriesRecording.PROJECTION).use { c ->
                    while (c.moveToNext() && !isCancelled) list.add(SeriesRecording.fromCursor(c))
                }
            } catch (e: Exception) {
                Log.w("DvrQuerySeriesRecording", "Can't query dvr series recording data", e)
            }
            return list
        }
    }

    companion object {
        private val DB_EXECUTOR = Executors.newSingleThreadExecutor(NamedThreadFactory(DvrDbFuture::class.java.simpleName))
        private val MAIN = Handler(Looper.getMainLooper())
    }
}
