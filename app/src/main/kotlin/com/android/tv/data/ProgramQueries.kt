package com.android.tv.data

import android.content.Context
import android.media.tv.TvContract
import android.util.Log
import androidx.annotation.WorkerThread
import com.android.tv.data.api.Program
import com.android.tv.util.TvProviderUtils

/** Ersatz für AsyncDbTask.AsyncQueryProgramTask: eine Sendung per ID laden (DB-Thread). */
object ProgramQueries {
    private const val TAG = "ProgramQueries"

    @JvmStatic
    @WorkerThread
    fun queryProgram(context: Context, programId: Long): Program? {
        val uri = TvContract.buildProgramUri(programId)
        var projection = ProgramImpl.PROJECTION
        if (TvProviderUtils.checkSeriesIdColumn(context, TvContract.Programs.CONTENT_URI)) {
            projection = TvProviderUtils.addExtraColumnsToProjection(projection, TvProviderUtils.EXTRA_PROGRAM_COLUMN_SERIES_ID)
        }
        return try {
            context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToNext()) ProgramImpl.fromCursor(c) else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error querying $uri", e)
            null
        }
    }
}
