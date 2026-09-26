package com.android.tv.data

import android.content.Context
import com.android.tv.R
import com.android.tv.data.api.BaseProgram

/** Gemeinsame Anzeige-Logik für Folgentitel. */
abstract class BaseProgramImpl : BaseProgram {

    override fun getEpisodeDisplayTitle(context: Context): String? =
        formatEpisode(context, R.string.display_episode_title_format_no_season_number, R.string.display_episode_title_format)

    override fun getEpisodeContentDescription(context: Context): String? =
        formatEpisode(context, R.string.content_description_episode_format_no_season_number,
            R.string.content_description_episode_format)

    /** "S1: E2 Titel" bzw. ohne Staffel, wenn diese leer oder "0" ist. */
    private fun formatEpisode(context: Context, noSeasonRes: Int, withSeasonRes: Int): String? {
        val episodeNumber = episodeNumber
        if (episodeNumber.isNullOrEmpty()) return episodeTitle
        val title = episodeTitle ?: ""
        val season = seasonNumber
        return if (season.isNullOrEmpty() || season == "0") {
            context.getString(noSeasonRes, episodeNumber, title)
        } else {
            context.getString(withSeasonRes, season, episodeNumber, title)
        }
    }

    override val isEpisodic: Boolean get() = !seriesId.isNullOrEmpty()
}
