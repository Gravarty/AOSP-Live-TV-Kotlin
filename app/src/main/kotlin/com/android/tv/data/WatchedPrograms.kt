package com.android.tv.data

import android.net.Uri
import android.provider.BaseColumns

/**
 * Abweichung: TvContract.WatchedPrograms ist @SystemApi und im öffentlichen SDK nicht sichtbar.
 * Die Konstanten sind hier 1:1 mit den Werten aus dem Framework nachgebildet.
 * Lesen/Beobachten klappt ohne Systemrecht ggf. nicht (SecurityException wird abgefangen).
 */
object WatchedPrograms {
    @JvmField val CONTENT_URI: Uri = Uri.parse("content://android.media.tv/watched_program")
    const val _ID = BaseColumns._ID
    const val COLUMN_WATCH_START_TIME_UTC_MILLIS = "watch_start_time_utc_millis"
    const val COLUMN_WATCH_END_TIME_UTC_MILLIS = "watch_end_time_utc_millis"
    const val COLUMN_CHANNEL_ID = "channel_id"
    const val COLUMN_TITLE = "title"
    const val COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis"
    const val COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis"
    const val COLUMN_DESCRIPTION = "description"
}
