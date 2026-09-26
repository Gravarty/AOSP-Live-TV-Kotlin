package com.android.tv.common

import android.media.tv.TvContentRating
import android.util.Log
import com.android.tv.common.memory.MemoryManageable
import java.util.SortedSet
import java.util.TreeSet

/** Cache für geparste Altersfreigaben (CSV -> Liste). */
object TvContentRatingCache : MemoryManageable {
    private const val TAG = "TvContentRatings"

    private val ratingsMultiMap = HashMap<String, List<TvContentRating>>()

    @JvmStatic
    fun getInstance(): TvContentRatingCache = this

    @Synchronized
    fun getRatings(commaSeparatedRatings: String?): List<TvContentRating> {
        if (commaSeparatedRatings.isNullOrEmpty()) return emptyList()
        ratingsMultiMap[commaSeparatedRatings]?.let { return it }
        val normalized = getSortedSetFromCsv(commaSeparatedRatings).joinToString(",")
        val ratings = ratingsMultiMap[normalized]
            ?: stringToContentRatings(commaSeparatedRatings).also { ratingsMultiMap[normalized] = it }
        if (normalized != commaSeparatedRatings) {
            // Nicht normalisierter Schlüssel zeigt auf dasselbe Ergebnis
            ratingsMultiMap[commaSeparatedRatings] = ratings
        }
        return ratings
    }

    @JvmStatic
    fun stringToContentRatings(commaSeparatedRatings: String?): List<TvContentRating> {
        if (commaSeparatedRatings.isNullOrEmpty()) return emptyList()
        return getSortedSetFromCsv(commaSeparatedRatings).mapNotNull {
            try {
                TvContentRating.unflattenFromString(it)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Can't parse the content rating: '$it'", e)
                null
            }
        }
    }

    private fun getSortedSetFromCsv(csv: String): Set<String> {
        val parts = csv.split(Regex("\\s*,\\s*")).dropLastWhile { it.isEmpty() } // wie Java split()
        return if (parts.size == 1) setOf(parts[0]) else TreeSet(parts)
    }

    @JvmStatic
    fun contentRatingsToString(contentRatings: List<TvContentRating>?): String? {
        if (contentRatings == null) return null
        val set: SortedSet<String> = TreeSet()
        contentRatings.forEach { set.add(it.flattenToString()) }
        return set.joinToString(",")
    }

    @Synchronized
    override fun performTrimMemory(level: Int) = ratingsMultiMap.clear()
}
