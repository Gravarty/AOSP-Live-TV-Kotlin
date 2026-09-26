package com.android.tv.dvr.data

import android.content.ContentValues
import android.database.Cursor
import android.os.Parcel
import android.os.Parcelable
import com.android.tv.data.api.BaseProgram
import com.android.tv.data.api.Program
import com.android.tv.dvr.DvrScheduleManager
import com.android.tv.dvr.provider.DvrContract.SeriesRecordings
import com.android.tv.util.Utils
import java.util.Objects

/** Serienaufnahme: nimmt alle passenden Folgen (ab Staffel/Folge, ein oder alle Kanäle) auf. */
class SeriesRecording private constructor(
    var id: Long,
    val priority: Long,
    val title: String?,
    val description: String?,
    val longDescription: String?,
    val inputId: String?,
    val channelId: Long,
    val seriesId: String?,
    val startFromSeason: Int,
    val startFromEpisode: Int,
    val channelOption: Int,
    val canonicalGenreIds: IntArray?,
    val posterUri: String?,
    val photoUri: String?,
    val state: Int,
) : Parcelable {

    class Builder {
        private var id = ID_NOT_SET
        private var priority = DvrScheduleManager.DEFAULT_SERIES_PRIORITY
        private var title: String? = null
        private var description: String? = null
        private var longDescription: String? = null
        private var inputId: String? = null
        private var channelId = 0L
        private var seriesId: String? = null
        private var startFromSeason = SeriesRecordings.THE_BEGINNING
        private var startFromEpisode = SeriesRecordings.THE_BEGINNING
        private var channelOption = OPTION_CHANNEL_ONE
        private var canonicalGenreIds: IntArray? = null
        private var posterUri: String? = null
        private var photoUri: String? = null
        private var state = STATE_SERIES_NORMAL

        fun setId(v: Long) = apply { id = v }
        fun setPriority(v: Long) = apply { priority = v }
        fun setTitle(v: String?) = apply { title = v }
        fun setDescription(v: String?) = apply { description = v }
        fun setLongDescription(v: String?) = apply { longDescription = v }
        fun setInputId(v: String?) = apply { inputId = v }
        fun setChannelId(v: Long) = apply { channelId = v }
        fun setSeriesId(v: String?) = apply { seriesId = v }
        fun setStartFromSeason(v: Int) = apply { startFromSeason = v }
        fun setChannelOption(v: Int) = apply { channelOption = v }
        fun setStartFromEpisode(v: Int) = apply { startFromEpisode = v }
        fun setCanonicalGenreIds(genres: String?) = apply { canonicalGenreIds = Utils.getCanonicalGenreIds(genres) }
        fun setCanonicalGenreIds(v: IntArray?) = apply { canonicalGenreIds = v }
        fun setPosterUri(v: String?) = apply { posterUri = v }
        fun setPhotoUri(v: String?) = apply { photoUri = v }
        fun setState(v: Int) = apply { state = v }

        fun build() = SeriesRecording(id, priority, title, description, longDescription, inputId, channelId, seriesId,
            startFromSeason, startFromEpisode, channelOption, canonicalGenreIds, posterUri, photoUri, state)
    }

    val isStopped: Boolean get() = state == STATE_SERIES_STOPPED

    fun matchProgram(program: Program): Boolean = matchProgram(program, channelOption)

    /** Gleiche Serie (und ggf. Kanal) und ab der eingestellten Staffel/Folge. */
    fun matchProgram(program: Program, channelOption: Int): Boolean {
        if (seriesId != program.seriesId || (channelOption == OPTION_CHANNEL_ONE && channelId != program.channelId)) return false
        val seasonNumber = program.seasonNumber
        if (startFromSeason != SeriesRecordings.THE_BEGINNING && !seasonNumber.isNullOrEmpty()) {
            // Nicht-numerische Staffel: aufnehmen
            val season = seasonNumber.toIntOrNull() ?: return true
            if (season > startFromSeason) return true
            if (season < startFromSeason) return false
        } else {
            return true
        }
        val episodeNumber = program.episodeNumber
        if (startFromEpisode == SeriesRecordings.THE_BEGINNING || episodeNumber.isNullOrEmpty()) return true
        val episode = episodeNumber.toIntOrNull() ?: return true
        return episode >= startFromEpisode
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SeriesRecording) return false
        return priority == other.priority && channelId == other.channelId && startFromSeason == other.startFromSeason &&
            startFromEpisode == other.startFromEpisode && id == other.id && title == other.title &&
            description == other.description && longDescription == other.longDescription && seriesId == other.seriesId &&
            channelOption == other.channelOption && canonicalGenreIds.contentEquals(other.canonicalGenreIds) &&
            posterUri == other.posterUri && photoUri == other.photoUri && state == other.state
    }

    override fun hashCode() = Objects.hash(priority, channelId, startFromSeason, startFromEpisode, id, title, description,
        longDescription, seriesId, channelOption, canonicalGenreIds.contentHashCode(), posterUri, photoUri, state)

    override fun toString() = "SeriesRecording{inputId=$inputId, channelId=$channelId, id=$id, priority=$priority, " +
        "title='$title', description='$description', longDescription='$longDescription', startFromSeason=$startFromSeason, " +
        "startFromEpisode=$startFromEpisode, channelOption=$channelOption, canonicalGenreIds=${canonicalGenreIds.contentToString()}, " +
        "posterUri=$posterUri, photoUri=$photoUri, state=$state}"

    override fun describeContents() = 0

    override fun writeToParcel(out: Parcel, flags: Int) {
        out.writeLong(id)
        out.writeLong(priority)
        out.writeString(title)
        out.writeString(description)
        out.writeString(longDescription)
        out.writeString(inputId)
        out.writeLong(channelId)
        out.writeString(seriesId)
        out.writeInt(startFromSeason)
        out.writeInt(startFromEpisode)
        out.writeInt(channelOption)
        out.writeIntArray(canonicalGenreIds)
        out.writeString(posterUri)
        out.writeString(photoUri)
        out.writeInt(state)
    }

    companion object {
        const val ID_NOT_SET = 0L
        const val DEFAULT_PRIORITY = Long.MAX_VALUE shr 1
        const val OPTION_CHANNEL_ONE = 0
        const val OPTION_CHANNEL_ALL = 1
        const val STATE_SERIES_NORMAL = 0
        const val STATE_SERIES_STOPPED = 1

        /** Höhere Priorität, dann höhere ID zuerst. */
        @JvmField val PRIORITY_COMPARATOR: Comparator<SeriesRecording> =
            Comparator { lhs, rhs -> rhs.priority.compareTo(lhs.priority).takeIf { it != 0 } ?: rhs.id.compareTo(lhs.id) }
        @JvmField val ID_COMPARATOR: Comparator<SeriesRecording> = compareBy { it.id }

        @JvmStatic
        fun builder(inputId: String?, p: BaseProgram): Builder = Builder()
            .setInputId(inputId).setSeriesId(p.seriesId).setChannelId(p.channelId).setTitle(p.title)
            .setDescription(p.description).setLongDescription(p.longDescription).setCanonicalGenreIds(p.canonicalGenreIds)
            .setPosterUri(p.posterArtUri).setPhotoUri(p.thumbnailUri)

        @JvmStatic
        fun buildFrom(r: SeriesRecording): Builder = Builder()
            .setId(r.id).setInputId(r.inputId).setChannelId(r.channelId).setPriority(r.priority).setTitle(r.title)
            .setDescription(r.description).setLongDescription(r.longDescription).setSeriesId(r.seriesId)
            .setStartFromEpisode(r.startFromEpisode).setStartFromSeason(r.startFromSeason).setChannelOption(r.channelOption)
            .setCanonicalGenreIds(r.canonicalGenreIds).setPosterUri(r.posterUri).setPhotoUri(r.photoUri).setState(r.state)

        @JvmField
        val PROJECTION = arrayOf(
            SeriesRecordings._ID, SeriesRecordings.COLUMN_INPUT_ID, SeriesRecordings.COLUMN_CHANNEL_ID,
            SeriesRecordings.COLUMN_PRIORITY, SeriesRecordings.COLUMN_TITLE, SeriesRecordings.COLUMN_SHORT_DESCRIPTION,
            SeriesRecordings.COLUMN_LONG_DESCRIPTION, SeriesRecordings.COLUMN_SERIES_ID,
            SeriesRecordings.COLUMN_START_FROM_EPISODE, SeriesRecordings.COLUMN_START_FROM_SEASON,
            SeriesRecordings.COLUMN_CHANNEL_OPTION, SeriesRecordings.COLUMN_CANONICAL_GENRE,
            SeriesRecordings.COLUMN_POSTER_URI, SeriesRecordings.COLUMN_PHOTO_URI, SeriesRecordings.COLUMN_STATE,
        )

        @JvmStatic
        fun fromCursor(c: Cursor): SeriesRecording {
            var i = -1
            return Builder()
                .setId(c.getLong(++i)).setInputId(c.getString(++i)).setChannelId(c.getLong(++i)).setPriority(c.getLong(++i))
                .setTitle(c.getString(++i)).setDescription(c.getString(++i)).setLongDescription(c.getString(++i))
                .setSeriesId(c.getString(++i)).setStartFromEpisode(c.getInt(++i)).setStartFromSeason(c.getInt(++i))
                .setChannelOption(channelOption(c.getString(++i))).setCanonicalGenreIds(c.getString(++i))
                .setPosterUri(c.getString(++i)).setPhotoUri(c.getString(++i)).setState(seriesRecordingState(c.getString(++i)))
                .build()
        }

        @JvmStatic
        fun toContentValues(r: SeriesRecording): ContentValues = ContentValues().apply {
            if (r.id != ID_NOT_SET) put(SeriesRecordings._ID, r.id) else putNull(SeriesRecordings._ID)
            put(SeriesRecordings.COLUMN_INPUT_ID, r.inputId)
            put(SeriesRecordings.COLUMN_CHANNEL_ID, r.channelId)
            put(SeriesRecordings.COLUMN_PRIORITY, r.priority)
            put(SeriesRecordings.COLUMN_TITLE, r.title)
            put(SeriesRecordings.COLUMN_SHORT_DESCRIPTION, r.description)
            put(SeriesRecordings.COLUMN_LONG_DESCRIPTION, r.longDescription)
            put(SeriesRecordings.COLUMN_SERIES_ID, r.seriesId)
            put(SeriesRecordings.COLUMN_START_FROM_EPISODE, r.startFromEpisode)
            put(SeriesRecordings.COLUMN_START_FROM_SEASON, r.startFromSeason)
            put(SeriesRecordings.COLUMN_CHANNEL_OPTION, channelOption(r.channelOption))
            put(SeriesRecordings.COLUMN_CANONICAL_GENRE, Utils.getCanonicalGenre(r.canonicalGenreIds))
            put(SeriesRecordings.COLUMN_POSTER_URI, r.posterUri)
            put(SeriesRecordings.COLUMN_PHOTO_URI, r.photoUri)
            put(SeriesRecordings.COLUMN_STATE, seriesRecordingState(r.state))
        }

        @JvmStatic
        fun fromParcel(p: Parcel): SeriesRecording = Builder()
            .setId(p.readLong()).setPriority(p.readLong()).setTitle(p.readString()).setDescription(p.readString())
            .setLongDescription(p.readString()).setInputId(p.readString()).setChannelId(p.readLong())
            .setSeriesId(p.readString()).setStartFromSeason(p.readInt()).setStartFromEpisode(p.readInt())
            .setChannelOption(p.readInt()).setCanonicalGenreIds(p.createIntArray()).setPosterUri(p.readString())
            .setPhotoUri(p.readString()).setState(p.readInt()).build()

        @JvmField
        val CREATOR = object : Parcelable.Creator<SeriesRecording> {
            override fun createFromParcel(p: Parcel) = fromParcel(p)
            override fun newArray(size: Int) = arrayOfNulls<SeriesRecording>(size)
        }

        @JvmStatic
        fun toArray(series: Collection<SeriesRecording>): Array<SeriesRecording> = series.toTypedArray()

        private fun channelOption(option: Int) =
            if (option == OPTION_CHANNEL_ALL) SeriesRecordings.OPTION_CHANNEL_ALL else SeriesRecordings.OPTION_CHANNEL_ONE

        private fun channelOption(option: String?) =
            if (option == SeriesRecordings.OPTION_CHANNEL_ALL) OPTION_CHANNEL_ALL else OPTION_CHANNEL_ONE

        private fun seriesRecordingState(state: Int) =
            if (state == STATE_SERIES_STOPPED) SeriesRecordings.STATE_SERIES_STOPPED else SeriesRecordings.STATE_SERIES_NORMAL

        private fun seriesRecordingState(state: String?) =
            if (state == SeriesRecordings.STATE_SERIES_STOPPED) STATE_SERIES_STOPPED else STATE_SERIES_NORMAL
    }
}
