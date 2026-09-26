package com.android.tv.dvr.provider

import android.provider.BaseColumns

/** Tabellen/Spalten der DVR-Datenbank (Aufnahmepläne und Serien). */
object DvrContract {
    object Schedules {
        const val _ID = BaseColumns._ID
        const val TABLE_NAME = "schedules"
        const val TYPE_PROGRAM = "TYPE_PROGRAM"
        const val TYPE_TIMED = "TYPE_TIMED"
        const val STATE_RECORDING_NOT_STARTED = "STATE_RECORDING_NOT_STARTED"
        const val STATE_RECORDING_IN_PROGRESS = "STATE_RECORDING_IN_PROGRESS"
        const val STATE_RECORDING_FINISHED = "STATE_RECORDING_FINISHED"
        const val STATE_RECORDING_FAILED = "STATE_RECORDING_FAILED"
        const val STATE_RECORDING_CLIPPED = "STATE_RECORDING_CLIPPED"
        const val STATE_RECORDING_DELETED = "STATE_RECORDING_DELETED"
        const val STATE_RECORDING_CANCELED = "STATE_RECORDING_CANCELED"
        const val FAILED_REASON_OTHER = "FAILED_REASON_OTHER"
        const val FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED = "FAILED_REASON_PROGRAM_ENDED_BEFORE_RECORDING_STARTED"
        const val FAILED_REASON_NOT_FINISHED = "FAILED_REASON_NOT_FINISHED"
        const val FAILED_REASON_INVALID_CHANNEL = "FAILED_REASON_INVALID_CHANNEL"
        const val FAILED_REASON_SCHEDULER_STOPPED = "FAILED_REASON_SCHEDULER_STOPPED"
        const val FAILED_REASON_MESSAGE_NOT_SENT = "FAILED_REASON_MESSAGE_NOT_SENT"
        const val FAILED_REASON_CONNECTION_FAILED = "FAILED_REASON_CONNECTION_FAILED"
        const val FAILED_REASON_RESOURCE_BUSY = "FAILED_REASON_RESOURCE_BUSY"
        const val FAILED_REASON_INPUT_UNAVAILABLE = "FAILED_REASON_INPUT_UNAVAILABLE"
        const val FAILED_REASON_INPUT_DVR_UNSUPPORTED = "FAILED_REASON_INPUT_DVR_UNSUPPORTED"
        const val FAILED_REASON_INSUFFICIENT_SPACE = "FAILED_REASON_INSUFFICIENT_SPACE"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_TYPE = "type"
        const val COLUMN_INPUT_ID = "input_id"
        const val COLUMN_CHANNEL_ID = "channel_id"
        const val COLUMN_PROGRAM_ID = "program_id"
        const val COLUMN_PROGRAM_TITLE = "program_title"
        const val COLUMN_START_TIME_UTC_MILLIS = "start_time_utc_millis"
        const val COLUMN_END_TIME_UTC_MILLIS = "end_time_utc_millis"
        const val COLUMN_SEASON_NUMBER = "season_number"
        const val COLUMN_EPISODE_NUMBER = "episode_number"
        const val COLUMN_EPISODE_TITLE = "episode_title"
        const val COLUMN_PROGRAM_DESCRIPTION = "program_description"
        const val COLUMN_PROGRAM_LONG_DESCRIPTION = "program_long_description"
        const val COLUMN_PROGRAM_POST_ART_URI = "program_poster_art_uri"
        const val COLUMN_PROGRAM_THUMBNAIL_URI = "program_thumbnail_uri"
        const val COLUMN_STATE = "state"
        const val COLUMN_FAILED_REASON = "failed_reason"
        const val COLUMN_SERIES_RECORDING_ID = "series_recording_id"
        const val COLUMN_START_OFFSET_MILLIS = "start_offset_millis"
        const val COLUMN_END_OFFSET_MILLIS = "end_offset_millis"
    }

    object SeriesRecordings {
        const val _ID = BaseColumns._ID
        const val TABLE_NAME = "series_recording"
        const val THE_BEGINNING = -1
        const val OPTION_CHANNEL_ONE = "OPTION_CHANNEL_ONE"
        const val OPTION_CHANNEL_ALL = "OPTION_CHANNEL_ALL"
        const val STATE_SERIES_NORMAL = "STATE_SERIES_NORMAL"
        const val STATE_SERIES_STOPPED = "STATE_SERIES_STOPPED"
        const val COLUMN_PRIORITY = "priority"
        const val COLUMN_INPUT_ID = "input_id"
        const val COLUMN_CHANNEL_ID = "channel_id"
        const val COLUMN_SERIES_ID = "series_id"
        const val COLUMN_TITLE = "title"
        const val COLUMN_SHORT_DESCRIPTION = "short_description"
        const val COLUMN_LONG_DESCRIPTION = "long_description"
        const val COLUMN_START_FROM_SEASON = "start_from_season"
        const val COLUMN_START_FROM_EPISODE = "start_from_episode"
        const val COLUMN_CHANNEL_OPTION = "channel_option"
        const val COLUMN_CANONICAL_GENRE = "canonical_genre"
        const val COLUMN_POSTER_URI = "poster_uri"
        const val COLUMN_PHOTO_URI = "photo_uri"
        const val COLUMN_STATE = "state"
    }
}
