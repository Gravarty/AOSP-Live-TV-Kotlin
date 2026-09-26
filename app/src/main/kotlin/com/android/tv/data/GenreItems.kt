package com.android.tv.data

import android.content.Context
import android.media.tv.TvContract.Programs.Genres
import com.android.tv.R

/** Genre-IDs der UI (Index in CANONICAL_GENRES), 0 = alle Kanäle. */
object GenreItems {
    const val ID_ALL_CHANNELS = 0

    private val CANONICAL_GENRES = arrayOf(
        null, // Alle Kanäle
        Genres.FAMILY_KIDS, Genres.SPORTS, Genres.SHOPPING, Genres.MOVIES, Genres.COMEDY,
        Genres.TRAVEL, Genres.DRAMA, Genres.EDUCATION, Genres.ANIMAL_WILDLIFE, Genres.NEWS,
        Genres.GAMING, Genres.ARTS, Genres.ENTERTAINMENT, Genres.LIFE_STYLE, Genres.MUSIC,
        Genres.PREMIER, Genres.TECH_SCIENCE,
    )

    @JvmStatic
    fun getLabels(context: Context): Array<String> {
        val items = context.resources.getStringArray(R.array.genre_labels)
        require(items.size == CANONICAL_GENRES.size) { "Genre data mismatch" }
        return items
    }

    @JvmStatic
    fun getGenreCount(): Int = CANONICAL_GENRES.size

    @JvmStatic
    fun getCanonicalGenre(id: Int): String? = CANONICAL_GENRES.getOrNull(id)

    @JvmStatic
    fun getId(canonicalGenre: String?): Int {
        if (canonicalGenre == null) return ID_ALL_CHANNELS
        for (i in 1 until CANONICAL_GENRES.size) if (CANONICAL_GENRES[i] == canonicalGenre) return i
        return ID_ALL_CHANNELS
    }
}
