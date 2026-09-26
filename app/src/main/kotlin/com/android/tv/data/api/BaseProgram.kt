package com.android.tv.data.api

import android.content.Context
import android.media.tv.TvContentRating

/** Gemeinsame Basis von Programmen und Aufnahmen. */
interface BaseProgram {
    val id: Long
    val title: String?
    val episodeTitle: String?
    fun getEpisodeDisplayTitle(context: Context): String?
    fun getEpisodeContentDescription(context: Context): String?
    val description: String?
    val longDescription: String?
    val startTimeUtcMillis: Long
    val endTimeUtcMillis: Long
    val durationMillis: Long
    val seriesId: String?
    val seasonNumber: String?
    val episodeNumber: String?
    val posterArtUri: String?
    val thumbnailUri: String?
    val canonicalGenreIds: IntArray?
    val contentRatings: List<TvContentRating>?
    val channelId: Long
    val isValid: Boolean
    val isEpisodic: Boolean

    /** Sortiert nach Staffel (optional absteigend), dann Folge. */
    class EpisodeComparator internal constructor(private val reversedSeason: Boolean) : Comparator<BaseProgram> {
        override fun compare(lhs: BaseProgram, rhs: BaseProgram): Int {
            if (lhs === rhs) return 0
            val season = numberCompare(lhs.seasonNumber, rhs.seasonNumber)
            if (season != 0) return if (reversedSeason) -season else season
            return numberCompare(lhs.episodeNumber, rhs.episodeNumber)
        }
    }

    companion object {
        val EPISODE_COMPARATOR: Comparator<BaseProgram> = EpisodeComparator(false)
        val SEASON_REVERSED_EPISODE_COMPARATOR: Comparator<BaseProgram> = EpisodeComparator(true)
        const val COLUMN_SERIES_ID = "series_id"
        const val COLUMN_STATE = "state"

        /** Vergleicht numerisch, sonst als Text; null zuerst. */
        @JvmStatic
        fun numberCompare(s1: String?, s2: String?): Int {
            if (s1 == s2) return 0
            if (s1 == null) return -1
            if (s2 == null) return 1
            return try {
                Integer.compare(s1.toInt(), s2.toInt())
            } catch (e: NumberFormatException) {
                s1.compareTo(s2)
            }
        }

        @JvmStatic
        fun generateSeriesId(packageName: String?, title: String?): String = "$packageName/$title"
    }
}
