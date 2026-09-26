package com.android.tv.dvr.data

import java.util.Objects

/** Staffel/Folge einer Serie; gleich nur bei beiden gesetzten Nummern. */
class SeasonEpisodeNumber(
    @JvmField val seriesRecordingId: Long,
    @JvmField val seasonNumber: String?,
    @JvmField val episodeNumber: String?,
) {
    constructor(r: ScheduledRecording) : this(r.seriesRecordingId, r.seasonNumber, r.episodeNumber)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SeasonEpisodeNumber || seasonNumber.isNullOrEmpty() || episodeNumber.isNullOrEmpty()) return false
        return seriesRecordingId == other.seriesRecordingId && seasonNumber == other.seasonNumber && episodeNumber == other.episodeNumber
    }

    override fun hashCode() = Objects.hash(seriesRecordingId, seasonNumber, episodeNumber)

    override fun toString() =
        "SeasonEpisodeNumber{seriesRecordingId=$seriesRecordingId, seasonNumber=$seasonNumber, episodeNumber=$episodeNumber}"
}
