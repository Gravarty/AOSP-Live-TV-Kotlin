package com.android.tv.search

/** Suche nach Kanälen/Sendungen für den System-Suchanbieter. */
interface SearchInterface {
    fun search(query: String, limit: Int, action: Int): List<LocalSearchProvider.SearchResult>

    companion object {
        const val ACTION_TYPE_START = 1
        const val ACTION_TYPE_AMBIGUOUS = 1
        const val ACTION_TYPE_SWITCH_CHANNEL = 2
        const val ACTION_TYPE_SWITCH_INPUT = 3
        const val ACTION_TYPE_END = 3
        const val PROGRESS_PERCENTAGE_HIDE = -1
    }
}
