package com.android.tv.search

import android.app.SearchManager
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import com.android.tv.Starter
import com.android.tv.common.CommonConstants

/**
 * Suchanbieter für die System-Suche (Kanäle und laufende Sendungen).
 * Ohne ACCESS_ALL_EPG_DATA sucht er in den geladenen Daten (DataManagerSearch). Die TvProvider-
 * Suche des Originals (nur mit Systemrecht) entfällt.
 */
class LocalSearchProvider : ContentProvider() {

    override fun onCreate(): Boolean = true

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        require(isSearchUri(uri)) { "Unknown URI: $uri" }
        val context = context!!
        Starter.start(context)
        val search: SearchInterface = DataManagerSearch(context)
        val query = uri.lastPathSegment
        var limit = getQueryParameter(uri, SearchManager.SUGGEST_PARAMETER_LIMIT, DEFAULT_SEARCH_LIMIT)
        if (limit <= 0) limit = DEFAULT_SEARCH_LIMIT
        var action = getQueryParameter(uri, SUGGEST_PARAMETER_ACTION, DEFAULT_SEARCH_ACTION)
        if (action < SearchInterface.ACTION_TYPE_START || action > SearchInterface.ACTION_TYPE_END) action = DEFAULT_SEARCH_ACTION
        val results = if (query.isNullOrEmpty()) emptyList() else search.search(query, limit, action)
        return createSuggestionsCursor(results)
    }

    private fun getQueryParameter(uri: Uri, key: String, defaultValue: Int): Int =
        try {
            uri.getQueryParameter(key)?.toInt() ?: defaultValue
        } catch (e: NumberFormatException) {
            defaultValue
        } catch (e: UnsupportedOperationException) {
            defaultValue
        }

    private fun createSuggestionsCursor(results: List<SearchResult>): Cursor {
        val cursor = MatrixCursor(SEARCHABLE_COLUMNS, results.size)
        for (r in results) {
            cursor.addRow(arrayOf(
                r.title, r.description, r.imageUri, r.intentAction, r.intentData, r.intentExtraData, r.contentType,
                if (r.isLive) LIVE_CONTENTS else NO_LIVE_CONTENTS,
                r.videoWidth.takeIf { it != 0 }?.toString(),
                r.videoHeight.takeIf { it != 0 }?.toString(),
                r.duration.takeIf { it != 0L }?.toString(),
                r.progressPercentage.toString(),
            ))
        }
        return cursor
    }

    override fun getType(uri: Uri): String? =
        if (uri.path?.startsWith(EXPECTED_PATH_PREFIX) == true) SearchManager.SUGGEST_MIME_TYPE else null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException()

    /** Suchergebnis (ersetzt die AutoValue-Klasse). */
    data class SearchResult(
        val channelId: Long = 0,
        val channelNumber: String? = null,
        val title: String? = null,
        val description: String? = null,
        val imageUri: String? = null,
        val intentAction: String? = null,
        val intentData: String? = null,
        val intentExtraData: String? = null,
        val contentType: String? = null,
        val isLive: Boolean = false,
        val videoWidth: Int = 0,
        val videoHeight: Int = 0,
        val duration: Long = 0,
        val progressPercentage: Int = 0,
    )

    companion object {
        const val AUTHORITY = CommonConstants.BASE_PACKAGE + ".search"
        private const val SUGGEST_COLUMN_PROGRESS_BAR_PERCENTAGE = "progress_bar_percentage"
        private val SEARCHABLE_COLUMNS = arrayOf(
            SearchManager.SUGGEST_COLUMN_TEXT_1, SearchManager.SUGGEST_COLUMN_TEXT_2,
            SearchManager.SUGGEST_COLUMN_RESULT_CARD_IMAGE, SearchManager.SUGGEST_COLUMN_INTENT_ACTION,
            SearchManager.SUGGEST_COLUMN_INTENT_DATA, SearchManager.SUGGEST_COLUMN_INTENT_EXTRA_DATA,
            SearchManager.SUGGEST_COLUMN_CONTENT_TYPE, SearchManager.SUGGEST_COLUMN_IS_LIVE,
            SearchManager.SUGGEST_COLUMN_VIDEO_WIDTH, SearchManager.SUGGEST_COLUMN_VIDEO_HEIGHT,
            SearchManager.SUGGEST_COLUMN_DURATION, SUGGEST_COLUMN_PROGRESS_BAR_PERCENTAGE,
        )
        private const val EXPECTED_PATH_PREFIX = "/" + SearchManager.SUGGEST_URI_PATH_QUERY
        internal const val SUGGEST_PARAMETER_ACTION = "action"
        internal const val DEFAULT_SEARCH_ACTION = SearchInterface.ACTION_TYPE_AMBIGUOUS
        internal const val DEFAULT_SEARCH_LIMIT = 10
        private const val NO_LIVE_CONTENTS = "0"
        private const val LIVE_CONTENTS = "1"

        /** Ersatz für TvUriMatcher.MATCH_ON_DEVICE_SEARCH: …/search_suggest_query/<query>. */
        private fun isSearchUri(uri: Uri): Boolean {
            val segments = uri.pathSegments
            return uri.authority == AUTHORITY && segments.size == 2 && segments[0] == SearchManager.SUGGEST_URI_PATH_QUERY
        }
    }
}
