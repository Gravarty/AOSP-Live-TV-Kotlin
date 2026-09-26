package com.android.tv.dvr.provider

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.database.sqlite.SQLiteQueryBuilder
import android.database.sqlite.SQLiteStatement
import android.provider.BaseColumns
import com.android.tv.dvr.data.ScheduledRecording
import com.android.tv.dvr.data.SeriesRecording
import com.android.tv.dvr.provider.DvrContract.Schedules
import com.android.tv.dvr.provider.DvrContract.SeriesRecordings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLite-Datenbank "dvr.db" (Version 18) für Aufnahmepläne und Serien.
 * DvrFlags.startEarlyEndLateEnabled() ist im AOSP-Build false → die Zeitversatz-Variante (Version 19)
 * bleibt enthalten, wird aber nicht benutzt.
 */
@Singleton
class DvrDatabaseHelper @Inject constructor(@ApplicationContext context: Context) :
    SQLiteOpenHelper(context, DB_NAME, null, if (START_EARLY_END_LATE_ENABLED) DATABASE_VERSION + 1 else DATABASE_VERSION) {

    override fun onConfigure(db: SQLiteDatabase) = db.setForeignKeyConstraintsEnabled(true)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(if (START_EARLY_END_LATE_ENABLED) SQL_CREATE_SCHEDULES_WITH_TIME_OFFSET else SQL_CREATE_SCHEDULES)
        db.execSQL(SQL_CREATE_SERIES_RECORDINGS)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 17) {
            // Alte Schemata: neu anlegen
            db.execSQL(SQL_DROP_SCHEDULES)
            db.execSQL(SQL_DROP_SERIES_RECORDINGS)
            onCreate(db)
            return
        }
        if (oldVersion < 18) {
            db.execSQL("ALTER TABLE ${Schedules.TABLE_NAME} ADD COLUMN ${Schedules.COLUMN_FAILED_REASON} TEXT DEFAULT null;")
        }
        if (START_EARLY_END_LATE_ENABLED && oldVersion < 19) {
            db.execSQL("ALTER TABLE ${Schedules.TABLE_NAME} ADD COLUMN ${Schedules.COLUMN_START_OFFSET_MILLIS} INTEGER NOT NULL DEFAULT '0';")
            db.execSQL("ALTER TABLE ${Schedules.TABLE_NAME} ADD COLUMN ${Schedules.COLUMN_END_OFFSET_MILLIS} INTEGER NOT NULL DEFAULT '0';")
        }
    }

    /** Von Version 19 zurück: Zeitversatz-Spalten per Umkopieren entfernen. */
    override fun onDowngrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion > DATABASE_VERSION) {
            val backup = "schedules_backup"
            db.execSQL(buildCreateSchedulesSql(backup, COLUMNS_SCHEDULES))
            db.execSQL("INSERT INTO $backup${buildSelectSql(COLUMNS_SCHEDULES)} FROM ${Schedules.TABLE_NAME}")
            db.execSQL(SQL_DROP_SCHEDULES)
            db.execSQL(SQL_CREATE_SCHEDULES)
            db.execSQL("INSERT INTO ${Schedules.TABLE_NAME}${buildSelectSql(COLUMNS_SCHEDULES)} FROM $backup")
            db.execSQL(buildDropSql(backup))
        }
    }

    fun query(tableName: String, projections: Array<String>): Cursor =
        SQLiteQueryBuilder().apply { tables = tableName }.query(readableDatabase, projections, null, null, null, null, null)

    fun insertSchedules(vararg recordings: ScheduledRecording) = inTransaction { db ->
        if (START_EARLY_END_LATE_ENABLED) {
            val st = db.compileStatement(SQL_INSERT_SCHEDULES_WITH_TIME_OFFSET)
            for (r in recordings) execute(st, COLUMNS_SCHEDULES_WITH_TIME_OFFSET, ScheduledRecording.toContentValuesWithTimeOffset(r))
        } else {
            val st = db.compileStatement(SQL_INSERT_SCHEDULES)
            for (r in recordings) execute(st, COLUMNS_SCHEDULES, ScheduledRecording.toContentValues(r))
        }
    }

    fun updateSchedules(vararg recordings: ScheduledRecording) = inTransaction { db ->
        if (START_EARLY_END_LATE_ENABLED) {
            val st = db.compileStatement(SQL_UPDATE_SCHEDULES_WITH_TIME_OFFSET)
            for (r in recordings) execute(st, COLUMNS_SCHEDULES_WITH_TIME_OFFSET, ScheduledRecording.toContentValuesWithTimeOffset(r), r.id)
        } else {
            val st = db.compileStatement(SQL_UPDATE_SCHEDULES)
            for (r in recordings) execute(st, COLUMNS_SCHEDULES, ScheduledRecording.toContentValues(r), r.id)
        }
    }

    fun deleteSchedules(vararg recordings: ScheduledRecording) = inTransaction { db ->
        val st = db.compileStatement(SQL_DELETE_SCHEDULES)
        for (r in recordings) deleteById(st, r.id)
    }

    fun insertSeriesRecordings(vararg recordings: SeriesRecording) = inTransaction { db ->
        val st = db.compileStatement(SQL_INSERT_SERIES_RECORDINGS)
        for (r in recordings) execute(st, COLUMNS_SERIES_RECORDINGS, SeriesRecording.toContentValues(r))
    }

    fun updateSeriesRecordings(vararg recordings: SeriesRecording) = inTransaction { db ->
        val st = db.compileStatement(SQL_UPDATE_SERIES_RECORDINGS)
        for (r in recordings) execute(st, COLUMNS_SERIES_RECORDINGS, SeriesRecording.toContentValues(r), r.id)
    }

    fun deleteSeriesRecordings(vararg recordings: SeriesRecording) = inTransaction { db ->
        val st = db.compileStatement(SQL_DELETE_SERIES_RECORDINGS)
        for (r in recordings) deleteById(st, r.id)
    }

    private inline fun inTransaction(block: (SQLiteDatabase) -> Unit) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            block(db)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /** Werte binden; bei Update zusätzlich die ID als letzten Parameter. */
    private fun execute(st: SQLiteStatement, columns: Array<ColumnInfo>, values: ContentValues, updateId: Long? = null) {
        st.clearBindings()
        bindColumns(st, columns, values)
        updateId?.let { st.bindLong(columns.size + 1, it) }
        st.execute()
    }

    private fun deleteById(st: SQLiteStatement, id: Long) {
        st.clearBindings()
        st.bindLong(1, id)
        st.execute()
    }

    private fun bindColumns(st: SQLiteStatement, columns: Array<ColumnInfo>, values: ContentValues) {
        for ((i, column) in columns.withIndex()) {
            val value = values.get(column.name)
            when (column.type) {
                SQL_DATA_TYPE_LONG, SQL_DATA_TYPE_INT ->
                    if (value == null) st.bindNull(i + 1) else st.bindLong(i + 1, (value as Number).toLong())
                SQL_DATA_TYPE_STRING -> {
                    val s = value as String?
                    // Leere Texte als NULL speichern
                    if (s.isNullOrEmpty()) st.bindNull(i + 1) else st.bindString(i + 1, s)
                }
            }
        }
    }

    private class ColumnInfo(val name: String, val type: Int, val constraint: String = "")

    companion object {
        private const val DATABASE_VERSION = 18
        private const val DB_NAME = "dvr.db"
        /** DvrFlags.startEarlyEndLateEnabled() – im AOSP-Build false. */
        const val START_EARLY_END_LATE_ENABLED = false
        private const val NOT_NULL = " NOT NULL"
        private const val PRIMARY_KEY_AUTOINCREMENT = " PRIMARY KEY AUTOINCREMENT"
        private const val SQL_DATA_TYPE_LONG = 0
        private const val SQL_DATA_TYPE_INT = 1
        private const val SQL_DATA_TYPE_STRING = 2

        private fun defaultConstraint(value: Any) = " DEFAULT $value"

        private val COLUMNS_SCHEDULES = arrayOf(
            ColumnInfo(Schedules._ID, SQL_DATA_TYPE_LONG, PRIMARY_KEY_AUTOINCREMENT),
            ColumnInfo(Schedules.COLUMN_PRIORITY, SQL_DATA_TYPE_LONG, defaultConstraint(ScheduledRecording.DEFAULT_PRIORITY)),
            ColumnInfo(Schedules.COLUMN_TYPE, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_INPUT_ID, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_CHANNEL_ID, SQL_DATA_TYPE_LONG, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_PROGRAM_ID, SQL_DATA_TYPE_LONG),
            ColumnInfo(Schedules.COLUMN_PROGRAM_TITLE, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_START_TIME_UTC_MILLIS, SQL_DATA_TYPE_LONG, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_END_TIME_UTC_MILLIS, SQL_DATA_TYPE_LONG, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_SEASON_NUMBER, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_EPISODE_NUMBER, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_EPISODE_TITLE, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_PROGRAM_DESCRIPTION, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_PROGRAM_LONG_DESCRIPTION, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_PROGRAM_POST_ART_URI, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_PROGRAM_THUMBNAIL_URI, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_STATE, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(Schedules.COLUMN_FAILED_REASON, SQL_DATA_TYPE_STRING),
            ColumnInfo(Schedules.COLUMN_SERIES_RECORDING_ID, SQL_DATA_TYPE_LONG),
        )
        private val COLUMNS_SCHEDULES_WITH_TIME_OFFSET = COLUMNS_SCHEDULES + arrayOf(
            ColumnInfo(Schedules.COLUMN_START_OFFSET_MILLIS, SQL_DATA_TYPE_LONG, defaultConstraint(ScheduledRecording.DEFAULT_TIME_OFFSET)),
            ColumnInfo(Schedules.COLUMN_END_OFFSET_MILLIS, SQL_DATA_TYPE_LONG, defaultConstraint(ScheduledRecording.DEFAULT_TIME_OFFSET)),
        )
        private val COLUMNS_SERIES_RECORDINGS = arrayOf(
            ColumnInfo(SeriesRecordings._ID, SQL_DATA_TYPE_LONG, PRIMARY_KEY_AUTOINCREMENT),
            ColumnInfo(SeriesRecordings.COLUMN_PRIORITY, SQL_DATA_TYPE_LONG, defaultConstraint(SeriesRecording.DEFAULT_PRIORITY)),
            ColumnInfo(SeriesRecordings.COLUMN_TITLE, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(SeriesRecordings.COLUMN_SHORT_DESCRIPTION, SQL_DATA_TYPE_STRING),
            ColumnInfo(SeriesRecordings.COLUMN_LONG_DESCRIPTION, SQL_DATA_TYPE_STRING),
            ColumnInfo(SeriesRecordings.COLUMN_INPUT_ID, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(SeriesRecordings.COLUMN_CHANNEL_ID, SQL_DATA_TYPE_LONG, NOT_NULL),
            ColumnInfo(SeriesRecordings.COLUMN_SERIES_ID, SQL_DATA_TYPE_STRING, NOT_NULL),
            ColumnInfo(SeriesRecordings.COLUMN_START_FROM_SEASON, SQL_DATA_TYPE_INT, defaultConstraint(SeriesRecordings.THE_BEGINNING)),
            ColumnInfo(SeriesRecordings.COLUMN_START_FROM_EPISODE, SQL_DATA_TYPE_INT, defaultConstraint(SeriesRecordings.THE_BEGINNING)),
            ColumnInfo(SeriesRecordings.COLUMN_CHANNEL_OPTION, SQL_DATA_TYPE_STRING, defaultConstraint(SeriesRecordings.OPTION_CHANNEL_ONE)),
            ColumnInfo(SeriesRecordings.COLUMN_CANONICAL_GENRE, SQL_DATA_TYPE_STRING),
            ColumnInfo(SeriesRecordings.COLUMN_POSTER_URI, SQL_DATA_TYPE_STRING),
            ColumnInfo(SeriesRecordings.COLUMN_PHOTO_URI, SQL_DATA_TYPE_STRING),
            ColumnInfo(SeriesRecordings.COLUMN_STATE, SQL_DATA_TYPE_STRING),
        )

        private fun foreignKeyConstraint(column: String, referenceTable: String, referenceColumn: String) =
            ",FOREIGN KEY($column) REFERENCES $referenceTable($referenceColumn) ON UPDATE CASCADE ON DELETE SET NULL"

        private fun buildCreateSchedulesSql(tableName: String, columns: Array<ColumnInfo>) = buildCreateSql(tableName, columns,
            foreignKeyConstraint(Schedules.COLUMN_SERIES_RECORDING_ID, SeriesRecordings.TABLE_NAME, SeriesRecordings._ID))

        private fun buildCreateSql(tableName: String, columns: Array<ColumnInfo>, foreignKey: String?): String =
            columns.joinToString(",", "CREATE TABLE $tableName(", "${foreignKey ?: ""});") { c ->
                c.name + (if (c.type == SQL_DATA_TYPE_STRING) " TEXT" else " INTEGER") + c.constraint
            }

        private fun buildSelectSql(columns: Array<ColumnInfo>) = columns.joinToString(",", " SELECT ") { it.name }

        private fun buildInsertSql(tableName: String, columns: Array<ColumnInfo>) =
            columns.joinToString(",", "INSERT INTO $tableName (", ")") { it.name } +
                " VALUES (" + columns.joinToString(",") { "?" } + ")"

        private fun buildUpdateSql(tableName: String, columns: Array<ColumnInfo>) =
            columns.joinToString(",", "UPDATE $tableName SET ") { "${it.name}=?" } + " WHERE ${BaseColumns._ID}=?"

        private fun buildDeleteSql(tableName: String) = "DELETE FROM $tableName WHERE ${BaseColumns._ID}=?"
        private fun buildDropSql(tableName: String) = "DROP TABLE IF EXISTS $tableName"

        internal val SQL_CREATE_SCHEDULES = buildCreateSchedulesSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES)
        private val SQL_INSERT_SCHEDULES = buildInsertSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES)
        private val SQL_UPDATE_SCHEDULES = buildUpdateSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES)
        private val SQL_CREATE_SCHEDULES_WITH_TIME_OFFSET = buildCreateSchedulesSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES_WITH_TIME_OFFSET)
        private val SQL_INSERT_SCHEDULES_WITH_TIME_OFFSET = buildInsertSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES_WITH_TIME_OFFSET)
        private val SQL_UPDATE_SCHEDULES_WITH_TIME_OFFSET = buildUpdateSql(Schedules.TABLE_NAME, COLUMNS_SCHEDULES_WITH_TIME_OFFSET)
        private val SQL_DELETE_SCHEDULES = buildDeleteSql(Schedules.TABLE_NAME)
        internal val SQL_DROP_SCHEDULES = buildDropSql(Schedules.TABLE_NAME)
        internal val SQL_CREATE_SERIES_RECORDINGS = buildCreateSql(SeriesRecordings.TABLE_NAME, COLUMNS_SERIES_RECORDINGS, null)
        private val SQL_INSERT_SERIES_RECORDINGS = buildInsertSql(SeriesRecordings.TABLE_NAME, COLUMNS_SERIES_RECORDINGS)
        private val SQL_UPDATE_SERIES_RECORDINGS = buildUpdateSql(SeriesRecordings.TABLE_NAME, COLUMNS_SERIES_RECORDINGS)
        private val SQL_DELETE_SERIES_RECORDINGS = buildDeleteSql(SeriesRecordings.TABLE_NAME)
        internal val SQL_DROP_SERIES_RECORDINGS = buildDropSql(SeriesRecordings.TABLE_NAME)
    }
}
